package smartcampus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.FacultySubjectAssignment;
import smartcampus.entity.Role;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;

/**
 * Adversarial checks on {@link AcademicAccessGuard} against real (Testcontainers) MySQL
 * — the exact scenarios called out in PROJECT_PLAN.md's Phase 3 checkpoint: a faculty
 * member must not gain access to a subject they are not assigned to, and an assignment
 * to one section must not extend to another.
 *
 * <p>Every test builds its own department/course/subjects/faculty with
 * {@code System.nanoTime()}-suffixed unique codes, matching the pattern
 * {@code PasswordResetFlowIntegrationTest} uses, rather than relying on transactional
 * rollback between tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AcademicAccessGuardTest {

    @Autowired private AcademicAccessGuard guard;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private FacultySubjectAssignmentRepository assignmentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Faculty assignedFaculty;
    private User assignedFacultyUser;
    private Faculty unassignedFaculty;
    private Faculty inactiveFaculty;
    private Subject subjectOne;
    private Subject subjectTwo;

    /**
     * A per-JVM monotonic counter, not just {@code System.nanoTime()}: on this platform
     * {@code nanoTime()} has ~1µs resolution, so taking a short suffix of it collided
     * between two {@code @BeforeEach} invocations that landed in the same tick (a real
     * failure observed as a duplicate-key error on {@code departments.uk_departments_code}
     * — one test's setUp inserted a department code another test's setUp had already
     * used). The counter guarantees every test method gets a distinct tag regardless of
     * clock resolution.
     */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @BeforeEach
    void setUp() {
        String unique = System.nanoTime() + "-" + SEQUENCE.incrementAndGet();
        String tag = String.valueOf(SEQUENCE.get());

        Department department =
                departmentRepository.save(
                        Department.builder().code("AAG" + tag).name("Access Guard Dept " + unique).build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("AAGC" + tag)
                                .name("Access Guard Course " + unique)
                                .department(department)
                                .durationSemesters(8)
                                .build());
        subjectOne =
                subjectRepository.save(
                        Subject.builder()
                                .code("AAGS1" + tag)
                                .name("Access Guard Subject One")
                                .credits(4)
                                .semester(1)
                                .course(course)
                                .build());
        subjectTwo =
                subjectRepository.save(
                        Subject.builder()
                                .code("AAGS2" + tag)
                                .name("Access Guard Subject Two")
                                .credits(3)
                                .semester(1)
                                .course(course)
                                .build());

        assignedFacultyUser = createUser("assigned-" + unique);
        assignedFaculty = createFaculty(department, assignedFacultyUser, "EA" + tag, FacultyStatus.ACTIVE);
        unassignedFaculty =
                createFaculty(department, createUser("unassigned-" + unique), "EU" + tag, FacultyStatus.ACTIVE);
        inactiveFaculty =
                createFaculty(department, createUser("inactive-" + unique), "EI" + tag, FacultyStatus.INACTIVE);

        // The one grant this whole test class exercises: assignedFaculty may act on
        // subjectOne, 2025-2026, semester 1, section A - and nothing else.
        assignmentRepository.save(
                FacultySubjectAssignment.builder()
                        .faculty(assignedFaculty)
                        .subject(subjectOne)
                        .academicYear("2025-2026")
                        .semester(1)
                        .section("A")
                        .build());

        // A stale assignment row for a since-deactivated faculty member - proves the
        // guard checks live faculty.status, not just row existence (RESTRICT means this
        // row cannot simply be deleted when a faculty account is deactivated).
        assignmentRepository.save(
                FacultySubjectAssignment.builder()
                        .faculty(inactiveFaculty)
                        .subject(subjectOne)
                        .academicYear("2025-2026")
                        .semester(1)
                        .section("A")
                        .build());
    }

    @Test
    void exactMatchingAssignment_isAllowed() {
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "A"))
                .isTrue();

        // Must not throw.
        guard.requireAssignment(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "A");
    }

    @Test
    void assignmentToOneSection_doesNotAuthorizeAnotherSection() {
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "B"))
                .isFalse();
        assertThatThrownBy(
                        () -> guard.requireAssignment(
                                assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "B"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignmentToOneSubject_doesNotAuthorizeAnotherSubject() {
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectTwo.getId(), "2025-2026", 1, "A"))
                .isFalse();
    }

    @Test
    void assignmentInOneAcademicYear_doesNotAuthorizeAnotherYear() {
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2026-2027", 1, "A"))
                .isFalse();
    }

    @Test
    void assignmentInOneSemester_doesNotAuthorizeAnotherSemester() {
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 2, "A"))
                .isFalse();
    }

    @Test
    void facultyWithNoAssignmentAtAll_isDenied() {
        assertThat(guard.isAssigned(unassignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "A"))
                .isFalse();
        assertThatThrownBy(
                        () -> guard.requireAssignment(
                                unassignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "A"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivatedFaculty_isDenied_evenWithAMatchingAssignmentRow() {
        assertThat(guard.isAssigned(inactiveFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "A"))
                .isFalse();
    }

    @Test
    void nullOrUnknownInputs_denyByDefault() {
        assertThat(guard.isAssigned((Long) null, subjectOne.getId(), "2025-2026", 1, "A")).isFalse();
        assertThat(guard.isAssigned(assignedFaculty.getId(), null, "2025-2026", 1, "A")).isFalse();
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), null, 1, "A")).isFalse();
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", null, "A"))
                .isFalse();
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, null))
                .isFalse();
        assertThat(guard.isAssigned(999_999_999L, subjectOne.getId(), "2025-2026", 1, "A")).isFalse();
    }

    @Test
    void principalOverload_resolvesFacultyFromUser_andDeniesANonFacultyPrincipal() {
        User studentUser = createUser("not-a-faculty-" + System.nanoTime());

        assertThat(guard.isAssigned(studentUser, subjectOne.getId(), "2025-2026", 1, "A")).isFalse();
        assertThat(guard.isAssigned(assignedFacultyUser, subjectOne.getId(), "2025-2026", 1, "A"))
                .isTrue();
        assertThatThrownBy(
                        () -> guard.requireAssignment(
                                studentUser, subjectOne.getId(), "2025-2026", 1, "A"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isAssignedToSubjectAnywhere_isTrueAcrossSections_butStillNotAWriteAuthorization() {
        // assignedFaculty teaches subjectOne (section A) somewhere, so the "do they
        // teach this subject at all" lookup is true...
        assertThat(guard.isAssignedToSubjectAnywhere(assignedFaculty.getId(), subjectOne.getId()))
                .isTrue();
        // ...but that must never be used to authorize a specific section: the exact
        // check for section B still denies.
        assertThat(guard.isAssigned(assignedFaculty.getId(), subjectOne.getId(), "2025-2026", 1, "B"))
                .isFalse();
        assertThat(guard.isAssignedToSubjectAnywhere(unassignedFaculty.getId(), subjectOne.getId()))
                .isFalse();
    }

    private User createUser(String emailPrefix) {
        return userRepository.save(
                User.builder()
                        .email(emailPrefix + "@example.com")
                        .password(passwordEncoder.encode("Password123!"))
                        .fullName("Test User " + emailPrefix)
                        .role(Role.FACULTY)
                        .build());
    }

    private Faculty createFaculty(
            Department department, User user, String employeeCode, FacultyStatus status) {
        return facultyRepository.save(
                Faculty.builder()
                        .user(user)
                        .employeeCode(employeeCode)
                        .department(department)
                        .status(status)
                        .build());
    }
}
