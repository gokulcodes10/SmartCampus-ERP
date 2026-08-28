package smartcampus.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.PageResponse;
import smartcampus.dto.StudentActivateRequest;
import smartcampus.dto.StudentAdminUpdateRequest;
import smartcampus.dto.StudentResponse;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Enrollment;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.DuplicateResourceException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.StudentRepository;

/**
 * Business logic for the {@code students} resource — admin CRUD with server-side
 * search/filter/pagination, the G1 pending-approval activation flow, and every access
 * rule that decides who may see a given student's record.
 *
 * <p><b>This class is the single, centralized authorization gate for student data</b>
 * (PROJECT_PLAN.md Phase 3 checkpoint — "a student cannot read another student's
 * record by editing the ID in the URL"). Every read path — {@code GET /api/students},
 * {@code GET /api/students/{id}}, {@code GET /api/students/me}, and the faculty
 * "students I teach" view — routes through either {@link #assertViewable} or the
 * identical filtering logic {@link #list} applies, so the rule is expressed exactly
 * once instead of once per endpoint:
 *
 * <ul>
 *   <li>{@code ADMIN} — sees everything.
 *   <li>{@code STUDENT} — sees only the row whose {@code user_id} is their own; every
 *       other id resolves to {@link ResourceNotFoundException} (404), identical to a
 *       genuinely nonexistent id, so id enumeration cannot distinguish "not yours"
 *       from "doesn't exist".
 *   <li>{@code FACULTY} — sees only students they actually teach, computed from
 *       {@link FacultySubjectAssignment} joined against {@link Enrollment}
 *       (PROJECT_PLAN.md clarification G2) — never the full roster.
 * </ul>
 */
@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final FacultyRepository facultyRepository;
    private final FacultySubjectAssignmentRepository facultySubjectAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public StudentService(
            StudentRepository studentRepository,
            DepartmentRepository departmentRepository,
            CourseRepository courseRepository,
            FacultyRepository facultyRepository,
            FacultySubjectAssignmentRepository facultySubjectAssignmentRepository,
            EnrollmentRepository enrollmentRepository) {
        this.studentRepository = studentRepository;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.facultyRepository = facultyRepository;
        this.facultySubjectAssignmentRepository = facultySubjectAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * Server-side search/filter/pagination (§44) over students, scoped by caller role.
     * {@code STUDENT} callers are rejected outright — the list endpoint is not how a
     * student reads their own profile, {@code GET /api/students/me} is. {@code
     * FACULTY} callers are silently narrowed to the students they teach: every filter
     * is combined with an {@code id IN (taught student ids)} predicate rather than
     * ever handing back the unfiltered roster.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(
            User caller,
            StudentStatus status,
            Long departmentId,
            Long courseId,
            Integer currentSemester,
            String section,
            String q,
            int page,
            int size) {
        if (caller.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Students cannot list student records.");
        }
        if (status == StudentStatus.PENDING && caller.getRole() != Role.ADMIN) {
            // The pending-activation queue is an admin-only view (G1); a faculty
            // member scoped to "students I teach" would never legitimately see a
            // PENDING student anyway (they aren't enrolled in anything yet), but this
            // makes the restriction explicit rather than incidental.
            throw new AccessDeniedException("Only administrators can view pending students.");
        }

        Specification<Student> spec =
                buildSpecification(status, departmentId, courseId, currentSemester, section, q);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        if (caller.getRole() == Role.FACULTY) {
            Faculty facultyProfile = requireFacultyProfile(caller);
            Set<Long> taughtIds = taughtStudentIds(facultyProfile.getId());
            if (taughtIds.isEmpty()) {
                return PageResponse.from(Page.empty(pageRequest));
            }
            spec = spec.and((root, cq, cb) -> root.get("id").in(taughtIds));
        }

        Page<Student> result = studentRepository.findAll(spec, pageRequest);
        return PageResponse.from(result.map(StudentResponse::from));
    }

    /** The caller's own profile — {@code GET /api/students/me}. */
    @Transactional(readOnly = true)
    public StudentResponse getOwnProfile(User caller) {
        Student student =
                studentRepository
                        .findByUserId(caller.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Student profile not found."));
        return StudentResponse.from(student);
    }

    /**
     * {@code GET /api/students/{id}} — the direct-by-id route every adversarial ID
     * enumeration attempt targets. Routes through the same {@link #assertViewable}
     * gate as everything else.
     */
    @Transactional(readOnly = true)
    public StudentResponse getById(Long id, User caller) {
        Student student = loadOrThrow(id);
        assertViewable(student, caller);
        return StudentResponse.from(student);
    }

    /**
     * G1 activation: assigns register number, department, course and current
     * semester, and flips the profile from {@code PENDING} to {@code ACTIVE} — all
     * four in the same write, satisfying {@code
     * chk_students_active_requires_assignment} (V3__academic.sql) atomically.
     *
     * <p>Idempotent in the sense that matters: calling this twice never silently
     * re-applies or partially re-applies the activation. An already-{@code ACTIVE}
     * profile is rejected cleanly with 409 rather than either quietly succeeding
     * again or falling through to a raw MySQL constraint error.
     */
    @Transactional
    public StudentResponse activate(Long id, StudentActivateRequest request, User caller) {
        requireAdmin(caller);
        Student student = loadOrThrow(id);

        if (student.getStatus() == StudentStatus.ACTIVE) {
            throw new DuplicateResourceException("Student is already active.");
        }

        String registerNumber = request.registerNumber().trim();
        if (studentRepository.existsByRegisterNumber(registerNumber)) {
            throw new DuplicateResourceException("Register number is already in use.");
        }

        Department department =
                departmentRepository
                        .findById(request.departmentId())
                        .orElseThrow(() -> new BadRequestException("Department not found."));
        Course course =
                courseRepository
                        .findById(request.courseId())
                        .orElseThrow(() -> new BadRequestException("Course not found."));

        student.setRegisterNumber(registerNumber);
        student.setDepartment(department);
        student.setCourse(course);
        student.setCurrentSemester(request.currentSemester());
        if (request.section() != null) {
            student.setSection(request.section());
        }
        if (request.admissionYear() != null) {
            student.setAdmissionYear(request.admissionYear());
        }
        student.setStatus(StudentStatus.ACTIVE);

        try {
            student = studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException ex) {
            // Belt and braces: a register-number race past the existsBy check above,
            // or (should validation ever be bypassed) the CHECK constraint itself.
            throw new DuplicateResourceException(
                    "Unable to activate student — register number may already be in use.");
        }

        log.info("Admin {} activated student {}", caller.getEmail(), student.getId());
        return StudentResponse.from(student);
    }

    /**
     * Admin edit of an already-assigned student's department/course/semester/section/
     * admission year. Deliberately cannot touch {@code registerNumber} or {@code
     * status} — see {@link StudentAdminUpdateRequest}'s javadoc for why that split
     * keeps the CHECK constraint always satisfiable.
     */
    @Transactional
    public StudentResponse update(Long id, StudentAdminUpdateRequest request, User caller) {
        requireAdmin(caller);
        Student student = loadOrThrow(id);

        if (request.departmentId() != null) {
            Department department =
                    departmentRepository
                            .findById(request.departmentId())
                            .orElseThrow(() -> new BadRequestException("Department not found."));
            student.setDepartment(department);
        }
        if (request.courseId() != null) {
            Course course =
                    courseRepository
                            .findById(request.courseId())
                            .orElseThrow(() -> new BadRequestException("Course not found."));
            student.setCourse(course);
        }
        if (request.currentSemester() != null) {
            student.setCurrentSemester(request.currentSemester());
        }
        if (request.section() != null) {
            student.setSection(request.section());
        }
        if (request.admissionYear() != null) {
            student.setAdmissionYear(request.admissionYear());
        }

        return StudentResponse.from(studentRepository.save(student));
    }

    /**
     * Soft-delete: {@code ACTIVE}/{@code PENDING} → {@code INACTIVE}. Hard delete is
     * never offered — {@code students} is referenced by {@code enrollments} with the
     * default {@code RESTRICT} FK (V3__academic.sql), and deactivation is the intended
     * pattern for a student who has left.
     */
    @Transactional
    public StudentResponse deactivate(Long id, User caller) {
        requireAdmin(caller);
        Student student = loadOrThrow(id);
        if (student.getStatus() == StudentStatus.INACTIVE) {
            throw new DuplicateResourceException("Student is already inactive.");
        }
        student.setStatus(StudentStatus.INACTIVE);
        return StudentResponse.from(studentRepository.save(student));
    }

    /**
     * {@code INACTIVE} → {@code ACTIVE} for a student that was previously activated
     * (all four required fields are already set, so the CHECK constraint is
     * satisfied without asking for them again). A student that was never activated —
     * rejected while still {@code PENDING}, for instance — must go through {@link
     * #activate} instead, since this method never sets those fields itself.
     */
    @Transactional
    public StudentResponse reactivate(Long id, User caller) {
        requireAdmin(caller);
        Student student = loadOrThrow(id);
        if (student.getStatus() == StudentStatus.ACTIVE) {
            throw new DuplicateResourceException("Student is already active.");
        }
        if (student.getRegisterNumber() == null
                || student.getDepartment() == null
                || student.getCourse() == null
                || student.getCurrentSemester() == null) {
            throw new BadRequestException(
                    "Student was never activated; use the activation endpoint instead.");
        }
        student.setStatus(StudentStatus.ACTIVE);
        try {
            student = studentRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Unable to reactivate student.");
        }
        return StudentResponse.from(student);
    }

    // -------------------------------------------------------------------------------
    // Centralized authorization
    // -------------------------------------------------------------------------------

    /**
     * The single gate every by-id read routes through. A mismatch of any kind — wrong
     * student, faculty not teaching this student — throws {@link
     * ResourceNotFoundException} rather than a 403, so a caller probing IDs cannot
     * distinguish "exists but isn't yours" from "doesn't exist".
     */
    private void assertViewable(Student student, User caller) {
        switch (caller.getRole()) {
            case ADMIN -> {
                // full access
            }
            case STUDENT -> {
                if (!student.getUser().getId().equals(caller.getId())) {
                    throw new ResourceNotFoundException("Student not found.");
                }
            }
            case FACULTY -> {
                Faculty facultyProfile =
                        facultyRepository.findByUserId(caller.getId()).orElse(null);
                if (facultyProfile == null
                        || !taughtStudentIds(facultyProfile.getId()).contains(student.getId())) {
                    throw new ResourceNotFoundException("Student not found.");
                }
            }
        }
    }

    /**
     * Every student id a faculty member is authorized to see, derived from {@link
     * FacultySubjectAssignment} (which subject/section/year/semester they teach) joined
     * against {@link Enrollment} (which students are enrolled in that exact
     * subject/section/year/semester) — PROJECT_PLAN.md clarification G2. Never the
     * full student roster.
     */
    private Set<Long> taughtStudentIds(Long facultyId) {
        List<FacultySubjectAssignment> assignments =
                facultySubjectAssignmentRepository.findByFacultyId(facultyId);
        Set<Long> ids = new HashSet<>();
        for (FacultySubjectAssignment assignment : assignments) {
            List<Enrollment> enrollments =
                    enrollmentRepository.findBySubjectIdAndAcademicYearAndSemesterAndSection(
                            assignment.getSubject().getId(),
                            assignment.getAcademicYear(),
                            assignment.getSemester(),
                            assignment.getSection());
            for (Enrollment enrollment : enrollments) {
                ids.add(enrollment.getStudent().getId());
            }
        }
        return ids;
    }

    private Faculty requireFacultyProfile(User caller) {
        return facultyRepository
                .findByUserId(caller.getId())
                .orElseThrow(() -> new AccessDeniedException("No faculty profile for this account."));
    }

    private void requireAdmin(User caller) {
        if (caller.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators can perform this action.");
        }
    }

    private Student loadOrThrow(Long id) {
        return studentRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
    }

    private Specification<Student> buildSpecification(
            StudentStatus status,
            Long departmentId,
            Long courseId,
            Integer currentSemester,
            String section,
            String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (courseId != null) {
                predicates.add(cb.equal(root.get("course").get("id"), courseId));
            }
            if (currentSemester != null) {
                predicates.add(cb.equal(root.get("currentSemester"), currentSemester));
            }
            if (section != null && !section.isBlank()) {
                predicates.add(cb.equal(root.get("section"), section));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                Join<Student, User> userJoin = root.join("user", JoinType.LEFT);
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(userJoin.get("fullName")), like),
                                cb.like(cb.lower(userJoin.get("email")), like),
                                cb.like(cb.lower(root.get("registerNumber")), like)));
            }
            if (query != null) {
                query.distinct(true);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
