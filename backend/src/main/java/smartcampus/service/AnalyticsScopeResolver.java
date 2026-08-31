package smartcampus.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.AnalyticsCohortRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;

/**
 * THE SECURITY BOUNDARY of Phase 5. Resolves WHICH {@code (subjectId, academicYear,
 * semester, section)} tuples a caller may aggregate analytics over, before {@code
 * AnalyticsService} ever runs a cohort query.
 *
 * <p>Deliberately mirrors {@link ScopedWriteAuthorizer}/{@link AcademicAccessGuard}'s
 * shape rather than re-implementing any part of the authorization decision: a FACULTY
 * caller pinned to a single fully-specified tuple is checked through {@link
 * AcademicAccessGuard#requireAssignment(User, Long, String, Integer, String)} — the
 * same G2 exact-tuple rule every other faculty write/read in this application uses.
 * There is no second authorization path anywhere in this class.
 *
 * <h2>The single most dangerous mistake in this phase</h2>
 *
 * A {@link ScopeResolution} is only half of the authorization decision. {@code
 * subjectIds} alone is NOT sufficient to gate a cohort query: a faculty member assigned
 * to teach subject X in section A only still has that same subject id in {@code
 * subjectIds} — filtering a cohort query by {@code subjectIds} alone would let them
 * read section B's students too. The caller (AnalyticsService) MUST additionally drop
 * every returned row whose own {@code (subjectId, academicYear, semester, section)}
 * tuple is not in {@link ScopeResolution#allowedTupleKeys()}, whenever that set is
 * non-null. {@code allowedTupleKeys() == null} is the ONLY representation of
 * "unrestricted" (ADMIN); every other case is a concrete, possibly-empty set of exact
 * tuples.
 */
@Service
public class AnalyticsScopeResolver {

    private final FacultyRepository facultyRepository;
    private final FacultySubjectAssignmentRepository assignmentRepository;
    private final AcademicAccessGuard academicAccessGuard;
    private final AnalyticsCohortRepository cohortRepository;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    public AnalyticsScopeResolver(
            FacultyRepository facultyRepository,
            FacultySubjectAssignmentRepository assignmentRepository,
            AcademicAccessGuard academicAccessGuard,
            AnalyticsCohortRepository cohortRepository,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.facultyRepository = facultyRepository;
        this.assignmentRepository = assignmentRepository;
        this.academicAccessGuard = academicAccessGuard;
        this.cohortRepository = cohortRepository;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    /** {@code allowedTupleKeys() == null} means UNRESTRICTED (ADMIN only). */
    public record ScopeResolution(List<Long> subjectIds, Set<String> allowedTupleKeys, boolean empty) {

        /**
         * A scope that matches nothing — never falls back to unfiltered data.
         *
         * <p>Named {@code none()} rather than {@code empty()} because the record's own
         * {@code empty} component already generates an {@code empty()} accessor, and a
         * static method cannot share that signature.
         */
        public static ScopeResolution none() {
            return new ScopeResolution(List.of(), Set.of(), true);
        }
    }

    public static String tupleKey(Long subjectId, String academicYear, Integer semester, String section) {
        return subjectId + "|" + academicYear + "|" + semester + "|" + section;
    }

    /**
     * Resolves the scope for {@code GET /api/analytics/class}. {@code caller == null}
     * or a STUDENT caller is always denied outright — a class view is never a student's
     * own view (that is {@code /api/analytics/me}).
     */
    @Transactional(readOnly = true)
    public ScopeResolution forClass(
            User caller, Long courseId, Long subjectId, String academicYear, Integer semester, String section) {
        if (caller == null || caller.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Class analytics are restricted to faculty and admin accounts.");
        }

        if (caller.getRole() == Role.ADMIN) {
            List<Long> subjectIds = cohortRepository.findSubjectIds(courseId, null);
            if (subjectId != null) {
                subjectIds = List.of(subjectId);
            }
            return new ScopeResolution(subjectIds, null, subjectIds.isEmpty());
        }

        // FACULTY. A deactivated or mid-provisioning faculty simply has no classes —
        // the same choice TeachingService already made — not an exception.
        Faculty faculty = facultyRepository.findByUserId(caller.getId()).orElse(null);
        if (faculty == null || faculty.getStatus() != FacultyStatus.ACTIVE) {
            return ScopeResolution.none();
        }

        if (subjectId != null && academicYear != null && semester != null && section != null) {
            // Exact tuple requested: route through the one real authorization check
            // rather than re-deriving it from the assignment list below.
            academicAccessGuard.requireAssignment(caller, subjectId, academicYear, semester, section);
            return new ScopeResolution(
                    List.of(subjectId), Set.of(tupleKey(subjectId, academicYear, semester, section)), false);
        }

        List<FacultySubjectAssignment> assignments = assignmentRepository.findByFacultyId(faculty.getId());
        List<Long> subjectIds = new ArrayList<>();
        Set<String> tupleKeys = new HashSet<>();
        for (FacultySubjectAssignment assignment : assignments) {
            if (courseId != null && !courseId.equals(assignment.getSubject().getCourse().getId())) {
                continue;
            }
            if (subjectId != null && !subjectId.equals(assignment.getSubject().getId())) {
                continue;
            }
            if (academicYear != null && !academicYear.equals(assignment.getAcademicYear())) {
                continue;
            }
            if (semester != null && !semester.equals(assignment.getSemester())) {
                continue;
            }
            if (section != null && !section.equals(assignment.getSection())) {
                continue;
            }
            Long sid = assignment.getSubject().getId();
            if (!subjectIds.contains(sid)) {
                subjectIds.add(sid);
            }
            tupleKeys.add(
                    tupleKey(sid, assignment.getAcademicYear(), assignment.getSemester(), assignment.getSection()));
        }
        return new ScopeResolution(subjectIds, tupleKeys, subjectIds.isEmpty());
    }

    /** Resolves the scope for {@code GET /api/analytics/overview}. ADMIN only, unrestricted within it. */
    @Transactional(readOnly = true)
    public ScopeResolution forOverview(User caller, Long departmentId, Long courseId) {
        scopedWriteAuthorizer.requireAdmin(caller);
        List<Long> subjectIds = cohortRepository.findSubjectIds(courseId, departmentId);
        return new ScopeResolution(subjectIds, null, subjectIds.isEmpty());
    }
}
