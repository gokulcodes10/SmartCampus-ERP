package smartcampus.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.TeachingClassResponse;
import smartcampus.entity.EnrollmentStatus;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;

/**
 * Answers "which (subject, academic year, semester, section) tuples is this faculty
 * member authorized to act on?" — the discovery endpoint behind {@code
 * GET /api/teaching/my-classes}. This is the only route through which a faculty user
 * can see their own assignment rows; {@code /api/faculty-subject-assignments} stays
 * ADMIN-only end to end (Phase 3).
 */
@Service
public class TeachingService {

    private final FacultyRepository facultyRepository;
    private final FacultySubjectAssignmentRepository assignmentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public TeachingService(
            FacultyRepository facultyRepository,
            FacultySubjectAssignmentRepository assignmentRepository,
            EnrollmentRepository enrollmentRepository) {
        this.facultyRepository = facultyRepository;
        this.assignmentRepository = assignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Returns the calling faculty's own assignments, enriched with subject/course
     * display fields and the current active-enrollment count. A caller with no {@link
     * Faculty} profile, or whose faculty row is not {@link FacultyStatus#ACTIVE},
     * yields an empty list rather than a thrown exception — the caller's role is
     * validated by the controller, and a faculty account mid-provisioning or
     * deactivated simply has no classes to show, not a broken request.
     *
     * <p>{@code @Transactional(readOnly = true)}: {@link TeachingClassResponse#from}
     * reads the lazy {@code subject} and {@code subject.course} associations, so it
     * must run inside the same persistence context that loaded each assignment.
     *
     * <p>Restricted to FACULTY callers — STUDENT and ADMIN are rejected with {@link
     * AccessDeniedException}. An admin manages assignments through the existing
     * ADMIN-only {@code /api/faculty-subject-assignments} instead.
     */
    @Transactional(readOnly = true)
    public List<TeachingClassResponse> myClasses(User caller) {
        if (caller == null || caller.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Only faculty accounts have a teaching schedule.");
        }

        Faculty faculty = facultyRepository.findByUserId(caller.getId()).orElse(null);
        if (faculty == null || faculty.getStatus() != FacultyStatus.ACTIVE) {
            return List.of();
        }

        List<FacultySubjectAssignment> assignments = assignmentRepository.findByFacultyId(faculty.getId());

        return assignments.stream()
                .map(assignment -> TeachingClassResponse.from(assignment, enrolledCount(assignment)))
                .sorted(
                        Comparator.comparing(TeachingClassResponse::academicYear)
                                .thenComparing(TeachingClassResponse::semester)
                                .thenComparing(TeachingClassResponse::subjectCode)
                                .thenComparing(TeachingClassResponse::section))
                .toList();
    }

    private long enrolledCount(FacultySubjectAssignment assignment) {
        return enrollmentRepository
                .findBySubjectIdAndAcademicYearAndSemesterAndSection(
                        assignment.getSubject().getId(),
                        assignment.getAcademicYear(),
                        assignment.getSemester(),
                        assignment.getSection())
                .stream()
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .count();
    }
}
