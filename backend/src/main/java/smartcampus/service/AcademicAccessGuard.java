package smartcampus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.User;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;

/**
 * The single, reusable answer to one question, asked everywhere a faculty member tries
 * to touch subject-scoped data: <b>may this faculty act on this subject, in this
 * academic year, semester and section?</b>
 *
 * <p>Every faculty authorization check in Phase 3 and every later phase (Phase 4
 * attendance and marks entry, and anything after that touches a roster) MUST route
 * through this class rather than re-querying
 * {@link smartcampus.repository.FacultySubjectAssignmentRepository} directly. Centralizing
 * it here means the deny-by-default rule below is enforced in exactly one place instead
 * of being re-implemented (and potentially gotten wrong) at every call site.
 *
 * <h2>Contract</h2>
 *
 * <ul>
 *   <li><b>Exact-match only.</b> {@link #isAssigned} and {@link #requireAssignment} check
 *       the <em>full</em> {@code (facultyId, subjectId, academicYear, semester, section)}
 *       tuple against {@code faculty_subject_assignments}. An assignment to subject X
 *       section "A" does <em>not</em> authorize section "B" of the same subject, and an
 *       assignment for academic year 2024-2025 does not authorize 2025-2026. There is no
 *       "wider" mode that relaxes this — {@link #isAssignedToSubjectAnywhere} exists only
 *       for non-authorizing use (e.g. populating a "subjects I teach" dropdown) and is
 *       called out below as unsafe for gating a write.
 *   <li><b>Deny by default.</b> A {@code null} faculty/subject id, a {@code null} or blank
 *       year/section, a {@code null} semester, an unknown faculty, an unknown subject, a
 *       faculty whose account is not {@code ACTIVE}, or simply no matching assignment row
 *       — every one of these denies. Nothing here defaults to permissive.
 *   <li><b>Deactivated faculty lose access immediately, even with a stale assignment
 *       row.</b> Deactivating a faculty member (flipping {@code faculty.status} to
 *       {@code INACTIVE}) does not delete their {@code faculty_subject_assignments} rows
 *       — the FK is {@code RESTRICT}, and the schema's intended pattern is
 *       soft-delete-via-status, not a cascading cleanup. Without the status check below, a
 *       fired/departed faculty member's old assignment rows would keep authorizing them
 *       forever. Every check here re-reads {@code faculty.status} live, so deactivation
 *       revokes access on the very next call.
 * </ul>
 *
 * <h2>The rollback trap</h2>
 *
 * On denial this class only logs via SLF4J before throwing — it never writes a denial
 * record to the database. That is deliberate: PROJECT_PLAN.md's Phase 2 postmortem
 * documents a real bug where a method saved state and then threw an unchecked exception
 * from the same {@code @Transactional} method, and Spring's default rollback-on-unchecked
 * rule silently discarded the save along with the throw. If a future phase adds a
 * persisted authorization-audit trail here, the method that writes it and then calls
 * {@code throw} MUST declare {@code @Transactional(noRollbackFor = AccessDeniedException.class)}
 * (or wrap the write in its own {@code REQUIRES_NEW} transaction) or the audit row will
 * vanish exactly like that bug did.
 */
@Service
public class AcademicAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(AcademicAccessGuard.class);

    private final FacultySubjectAssignmentRepository assignmentRepository;
    private final FacultyRepository facultyRepository;

    public AcademicAccessGuard(
            FacultySubjectAssignmentRepository assignmentRepository,
            FacultyRepository facultyRepository) {
        this.assignmentRepository = assignmentRepository;
        this.facultyRepository = facultyRepository;
    }

    /**
     * Returns {@code true} only if {@code facultyId} is an {@code ACTIVE} faculty member
     * assigned to {@code subjectId} in exactly this academic year, semester and section.
     * Never throws — every failure mode (bad input, unknown id, inactive faculty, no
     * matching row) simply returns {@code false}. Use this where a boolean is more
     * natural than a thrown exception (e.g. deciding whether to show a UI action);
     * use {@link #requireAssignment(Long, Long, String, Integer, String)} to gate an
     * actual write.
     */
    @Transactional(readOnly = true)
    public boolean isAssigned(
            Long facultyId, Long subjectId, String academicYear, Integer semester, String section) {
        if (facultyId == null
                || subjectId == null
                || academicYear == null
                || academicYear.isBlank()
                || semester == null
                || section == null
                || section.isBlank()) {
            return false;
        }

        boolean facultyActive =
                facultyRepository
                        .findById(facultyId)
                        .map(f -> f.getStatus() == FacultyStatus.ACTIVE)
                        .orElse(false);
        if (!facultyActive) {
            return false;
        }

        return assignmentRepository
                .findByFacultyIdAndSubjectIdAndAcademicYearAndSemesterAndSection(
                        facultyId, subjectId, academicYear, semester, section)
                .isPresent();
    }

    /**
     * Same check as {@link #isAssigned(Long, Long, String, Integer, String)}, resolving
     * the faculty id from an authenticated principal instead of a raw id. {@code
     * principal} is the {@code User} placed in the {@code SecurityContext} by {@code
     * JwtAuthenticationFilter} — pass {@code (User) authentication.getPrincipal()} from a
     * controller. A user with no matching {@code Faculty} profile (e.g. a STUDENT or
     * ADMIN token, or a race with faculty provisioning) denies rather than throwing
     * {@code NoSuchElementException}.
     */
    @Transactional(readOnly = true)
    public boolean isAssigned(
            User principal, Long subjectId, String academicYear, Integer semester, String section) {
        Long facultyId = resolveFacultyId(principal);
        return isAssigned(facultyId, subjectId, academicYear, semester, section);
    }

    /**
     * Enforces {@link #isAssigned(Long, Long, String, Integer, String)}, throwing
     * {@link AccessDeniedException} on denial. {@code GlobalExceptionHandler} already
     * maps this to the standard §47 403 envelope, so callers do not need their own
     * try/catch — just call this at the top of any faculty-facing operation scoped to a
     * subject/section and let the exception propagate.
     */
    public void requireAssignment(
            Long facultyId, Long subjectId, String academicYear, Integer semester, String section) {
        if (!isAssigned(facultyId, subjectId, academicYear, semester, section)) {
            log.warn(
                    "Denied faculty {} access to subject {} (year={}, semester={}, section={}): "
                            + "no matching ACTIVE faculty_subject_assignments row.",
                    facultyId,
                    subjectId,
                    academicYear,
                    semester,
                    section);
            throw new AccessDeniedException(
                    "You are not assigned to teach this subject for this academic year, "
                            + "semester and section.");
        }
    }

    /** Principal-based counterpart of {@link #requireAssignment(Long, Long, String, Integer, String)}. */
    public void requireAssignment(
            User principal, Long subjectId, String academicYear, Integer semester, String section) {
        requireAssignment(resolveFacultyId(principal), subjectId, academicYear, semester, section);
    }

    /**
     * Whether {@code facultyId} is assigned to teach {@code subjectId} in <em>any</em>
     * academic year, semester or section. This is a leftmost-prefix match against the
     * same unique key {@code isAssigned} checks in full, and it exists only to drive
     * non-authorizing UI (e.g. "which subjects does this faculty teach, ever" for a
     * picker). <b>Never use this to gate a write on a specific roster</b> — it does not
     * confirm the faculty may act in the section/year/semester actually being written
     * to, which is the entire point of the exact-match check above.
     */
    @Transactional(readOnly = true)
    public boolean isAssignedToSubjectAnywhere(Long facultyId, Long subjectId) {
        if (facultyId == null || subjectId == null) {
            return false;
        }
        return !assignmentRepository.findByFacultyIdAndSubjectId(facultyId, subjectId).isEmpty();
    }

    private Long resolveFacultyId(User principal) {
        if (principal == null || principal.getId() == null) {
            return null;
        }
        return facultyRepository.findByUserId(principal.getId()).map(Faculty::getId).orElse(null);
    }
}
