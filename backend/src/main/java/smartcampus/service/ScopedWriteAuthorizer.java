package smartcampus.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import smartcampus.entity.Faculty;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.StudentRepository;

/**
 * The single Phase 4 write gate. Every write against {@code attendance}, {@code exams},
 * {@code marks} and {@code grade_bands} MUST call {@link #requireScopedWrite} (or one of
 * the narrower {@link #requireAdmin}/{@link #isAdmin} helpers where the operation is
 * ADMIN-only outright) rather than re-implementing any part of this check inline.
 *
 * <p>This class does not decide authorization itself for the FACULTY case — it
 * delegates to the existing {@link AcademicAccessGuard} (PROJECT_PLAN.md clarification
 * G2), which is unit-tested against ten adversarial cases. Being assigned to teach a
 * subject in one section, academic year or semester grants <b>nothing</b> in any other
 * section, year or semester: the guard checks the exact
 * {@code (facultyId, subjectId, academicYear, semester, section)} tuple, and the tuple
 * passed to {@link #requireScopedWrite} here MUST be the tuple actually being written
 * to the row or exam in question — never a tuple read from anywhere else. No Phase 4
 * code may authorize a write any other way.
 */
@Service
public class ScopedWriteAuthorizer {

    private final AcademicAccessGuard academicAccessGuard;
    private final FacultyRepository facultyRepository;
    private final StudentRepository studentRepository;

    public ScopedWriteAuthorizer(
            AcademicAccessGuard academicAccessGuard,
            FacultyRepository facultyRepository,
            StudentRepository studentRepository) {
        this.academicAccessGuard = academicAccessGuard;
        this.facultyRepository = facultyRepository;
        this.studentRepository = studentRepository;
    }

    /** {@code true} iff {@code caller} is non-null and holds the ADMIN role. */
    public boolean isAdmin(User caller) {
        return caller != null && caller.getRole() == Role.ADMIN;
    }

    /** Throws {@link AccessDeniedException} unless {@code caller} is an ADMIN. */
    public void requireAdmin(User caller) {
        if (!isAdmin(caller)) {
            throw new AccessDeniedException("This operation requires an ADMIN account.");
        }
    }

    /**
     * The single Phase 4 write gate.
     *
     * <ul>
     *   <li>{@code caller == null} — throws {@link AccessDeniedException}.
     *   <li>ADMIN — allowed unconditionally; an admin has no {@code faculty} row, so
     *       routing an admin through {@link AcademicAccessGuard} would always deny.
     *   <li>FACULTY — delegates to {@link
     *       AcademicAccessGuard#requireAssignment(User, Long, String, Integer, String)}
     *       against the exact tuple supplied.
     *   <li>Anything else (STUDENT) — throws {@link AccessDeniedException}.
     * </ul>
     */
    public void requireScopedWrite(
            User caller, Long subjectId, String academicYear, Integer semester, String section) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        switch (caller.getRole()) {
            case ADMIN -> {
                // An admin has no faculty row; the guard would always deny them, so an
                // admin is authorized here directly instead of being routed through it.
            }
            case FACULTY ->
                    academicAccessGuard.requireAssignment(caller, subjectId, academicYear, semester, section);
            default ->
                    throw new AccessDeniedException(
                            "This operation is restricted to faculty assigned to this class, or an admin.");
        }
    }

    /** The caller's {@link Faculty} row, or {@code null} for ADMIN/STUDENT. Used to stamp marked_by/entered_by. */
    public Faculty facultyOrNull(User caller) {
        if (caller == null || caller.getRole() != Role.FACULTY) {
            return null;
        }
        return facultyRepository.findByUserId(caller.getId()).orElse(null);
    }

    /** The caller's own {@link Student} row. Throws {@link ResourceNotFoundException} if the caller has no student profile. */
    public Student requireOwnStudent(User caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required for this operation.");
        }
        return studentRepository
                .findByUserId(caller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No student profile exists for this account."));
    }
}
