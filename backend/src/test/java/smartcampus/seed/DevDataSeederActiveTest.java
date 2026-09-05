package smartcampus.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import smartcampus.TestcontainersConfiguration;
import smartcampus.entity.Role;
import smartcampus.entity.StudentStatus;
import smartcampus.repository.AnnouncementRepository;
import smartcampus.repository.AttendanceRepository;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.MarksRepository;
import smartcampus.repository.ProblemTestCaseRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;

/**
 * Verifies the {@code seed} profile + {@code smartcampus.seed.enabled=true} combination
 * against real Testcontainers MySQL: {@link DevDataSeeder} exists as a bean, the
 * {@link DevDataSeeder#run} ApplicationRunner seeds the full scope-65 dataset at context
 * startup, the row counts are exactly what the dataset design calls for, and calling
 * {@link DevDataSeeder#seed()} a SECOND time — simulating a second application boot
 * against a database that already has this data — changes nothing and throws nothing.
 *
 * <p>This context gets its own fresh Testcontainers MySQL instance (a new container per
 * distinct Spring test context), so the counts asserted here are exact, not "at least".
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("seed")
@TestPropertySource(properties = "smartcampus.seed.enabled=true")
class DevDataSeederActiveTest {

    @Autowired private DevDataSeeder devDataSeeder;

    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private FacultyRepository facultyRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private FacultySubjectAssignmentRepository facultySubjectAssignmentRepository;
    @Autowired private ExamRepository examRepository;
    @Autowired private MarksRepository marksRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private CodingProblemRepository codingProblemRepository;
    @Autowired private ProblemTestCaseRepository problemTestCaseRepository;
    @Autowired private CodingContestRepository codingContestRepository;
    @Autowired private ContestProblemRepository contestProblemRepository;
    @Autowired private AnnouncementRepository announcementRepository;

    @Test
    void seederBeanExists() {
        assertThat(devDataSeeder).isNotNull();
    }

    @Test
    void startupSeedingProducesExactExpectedRowCountsAndASecondRunIsIdempotent() {
        assertRowCounts("after the implicit startup run");

        // Simulate a second application boot against a database that already has this
        // data (exactly what re-running `./mvnw spring-boot:run` with the seed profile
        // a second time does). Must not throw, and must not change anything.
        devDataSeeder.seed();

        assertRowCounts("after a second, idempotent run");

        // A third pass for good measure — idempotency must hold indefinitely, not just once.
        devDataSeeder.seed();
        assertRowCounts("after a third, idempotent run");
    }

    private void assertRowCounts(String phase) {
        assertThat(departmentRepository.count()).as("departments " + phase).isEqualTo(4);
        assertThat(courseRepository.count()).as("courses " + phase).isEqualTo(4);
        assertThat(subjectRepository.count()).as("subjects " + phase).isEqualTo(16);

        // 1 seed admin + 4 faculty + 12 students
        assertThat(userRepository.count()).as("users " + phase).isEqualTo(17);
        assertThat(userRepository.findByEmail("admin@smartcampus.local"))
                .as("seed admin exists " + phase)
                .isPresent()
                .get()
                .extracting(u -> u.getRole())
                .isEqualTo(Role.ADMIN);

        assertThat(facultyRepository.count()).as("faculty " + phase).isEqualTo(4);
        assertThat(studentRepository.count()).as("students " + phase).isEqualTo(12);
        assertThat(studentRepository.findAll())
                .as("every seeded student is ACTIVE " + phase)
                .allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(StudentStatus.ACTIVE));

        assertThat(enrollmentRepository.count()).as("enrollments " + phase).isEqualTo(24);
        assertThat(facultySubjectAssignmentRepository.count())
                .as("faculty-subject assignments " + phase)
                .isEqualTo(8);

        assertThat(examRepository.count()).as("exams " + phase).isEqualTo(24);
        assertThat(marksRepository.count()).as("marks " + phase).isEqualTo(72);
        assertThat(attendanceRepository.count()).as("attendance " + phase).isEqualTo(480);

        assertThat(companyRepository.count()).as("companies " + phase).isEqualTo(4);
        assertThat(jobRepository.count()).as("jobs " + phase).isEqualTo(6);

        assertThat(codingProblemRepository.count()).as("coding problems " + phase).isEqualTo(8);
        assertThat(problemTestCaseRepository.count()).as("problem test cases " + phase).isEqualTo(40);

        assertThat(codingContestRepository.count()).as("contests " + phase).isEqualTo(2);
        assertThat(contestProblemRepository.count()).as("contest problems " + phase).isEqualTo(4);

        assertThat(announcementRepository.count()).as("announcements " + phase).isEqualTo(6);
    }
}
