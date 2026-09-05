package smartcampus.seed;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import smartcampus.dto.AnnouncementCreateRequest;
import smartcampus.dto.AttendanceBulkRequest;
import smartcampus.dto.AttendanceMarkEntry;
import smartcampus.dto.CompanyCreateRequest;
import smartcampus.dto.ContestCreateRequest;
import smartcampus.dto.ContestProblemRequest;
import smartcampus.dto.CourseCreateRequest;
import smartcampus.dto.DepartmentCreateRequest;
import smartcampus.dto.EnrollmentRequest;
import smartcampus.dto.ExamCreateRequest;
import smartcampus.dto.FacultyCreateRequest;
import smartcampus.dto.FacultySubjectAssignmentRequest;
import smartcampus.dto.JobCreateRequest;
import smartcampus.dto.MarksBulkRequest;
import smartcampus.dto.MarksEntry;
import smartcampus.dto.ProblemCreateRequest;
import smartcampus.dto.ProvisionUserRequest;
import smartcampus.dto.RegisterRequest;
import smartcampus.dto.StudentActivateRequest;
import smartcampus.dto.SubjectCreateRequest;
import smartcampus.dto.TestCaseRequest;
import smartcampus.entity.AnnouncementAudience;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Announcement;
import smartcampus.entity.Company;
import smartcampus.entity.CodingContest;
import smartcampus.entity.CodingProblem;
import smartcampus.entity.Course;
import smartcampus.entity.Department;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamType;
import smartcampus.entity.Faculty;
import smartcampus.entity.Job;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;
import smartcampus.entity.NotificationPriority;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.Subject;
import smartcampus.entity.User;
import smartcampus.repository.CodingContestRepository;
import smartcampus.repository.CodingProblemRepository;
import smartcampus.repository.CompanyRepository;
import smartcampus.repository.AnnouncementRepository;
import smartcampus.repository.ContestProblemRepository;
import smartcampus.repository.CourseRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.EnrollmentRepository;
import smartcampus.repository.ExamRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.FacultySubjectAssignmentRepository;
import smartcampus.repository.JobRepository;
import smartcampus.repository.ProblemTestCaseRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.SubjectRepository;
import smartcampus.repository.UserRepository;
import smartcampus.service.AnnouncementService;
import smartcampus.service.AttendanceService;
import smartcampus.service.AuthService;
import smartcampus.service.CodingContestService;
import smartcampus.service.CodingProblemService;
import smartcampus.service.CompanyService;
import smartcampus.service.CourseService;
import smartcampus.service.DepartmentService;
import smartcampus.service.EnrollmentService;
import smartcampus.service.ExamService;
import smartcampus.service.FacultySubjectAssignmentService;
import smartcampus.service.FacultyService;
import smartcampus.service.JobService;
import smartcampus.service.MarksService;
import smartcampus.service.StudentService;
import smartcampus.service.SubjectService;
import smartcampus.service.UserProvisioningService;

/**
 * The scope-65 development dataset: departments, courses, subjects, an admin, faculty,
 * students, enrollments, faculty-subject assignments, attendance, exams and marks,
 * companies and jobs, coding problems with test cases, contests, and announcements.
 *
 * <h2>Why this is a profile-gated bean, not a {@code V12} Flyway migration</h2>
 *
 * PROJECT_PLAN.md's Phase 12 note leaves {@code V12} deliberately unclaimed. Flyway runs
 * unconditionally at startup in <b>every</b> environment ({@code spring.flyway.enabled=true}
 * with no profile gate in {@code application.properties}), so a {@code V12__seed.sql}
 * migration would install fake students, fake marks, and a known-password admin into
 * production the very first time the application booted there — exactly the "production
 * system depends on fake data" failure scope §65 forbids. There is no safe way to gate a
 * migration instead: {@code spring.flyway.locations} is a single committed value, and a
 * dev-only extra location would make {@code flyway_schema_history} diverge between
 * environments, at which point {@code validate-on-migrate=true} (also committed) refuses
 * to start production the moment its history disagrees with what CI or a fresh clone
 * would apply. A migration would also mean committing BCrypt hashes of known development
 * passwords as SQL literals, permanently, in git history. A bean gated on both a Spring
 * profile AND a property is the only mechanism that is inert by default and cannot fire
 * from a half-applied configuration.
 *
 * <h2>Three independent switches — all must agree, or nothing happens</h2>
 *
 * <ol>
 *   <li>{@link Profile @Profile("seed")} — the {@code seed} Spring profile must be active.
 *   <li>{@link ConditionalOnProperty @ConditionalOnProperty(name =
 *       "smartcampus.seed.enabled", havingValue = "true")} — AND the property must be the
 *       literal string {@code "true"}. Neither alone creates this bean.
 *   <li>A runtime check in {@link #run} that throws if the active profiles contain
 *       {@code "prod"} (case-insensitive), so even an operator who mistakenly activates
 *       both {@code seed} and {@code prod} together gets a loud startup failure instead of
 *       a poisoned production database.
 * </ol>
 *
 * <h2>Idempotency</h2>
 *
 * Every entity this seeder writes is looked up by its natural key first (department code,
 * course code, subject code, user email, employee code, register number, company name,
 * job company+title, problem slug, contest slug, announcement title) and created only if
 * absent. Attendance and marks are the two exceptions that need no such guard: {@link
 * AttendanceService#bulkMark} and {@link MarksService#bulkUpsert} are themselves UPSERTs
 * against their respective unique keys ({@code (student_id, subject_id, attendance_date,
 * period)} and {@code (exam_id, student_id)}), so calling them again with the same
 * arguments corrects the same rows rather than duplicating them. Nothing here ever
 * deletes or truncates anything, under any condition. {@link #seed()} is public
 * specifically so a test can invoke it twice in one Spring context and assert the second
 * run leaves row counts unchanged — see {@code smartcampus.seed.DevDataSeederTest}.
 *
 * <h2>What this deliberately does NOT seed</h2>
 *
 * Per §69 ("no fake functionality") and the Phase 12 task brief: no {@code AIConversation}
 * rows, no {@code CodingSubmission} rows (a fabricated ACCEPTED verdict would be a lie —
 * Judge0 is unreachable in this environment per G10), and no {@code Notification} rows
 * describing events that never happened. The six seeded announcements ARE real rows a
 * real (seeded) admin authored, so their fan-out into real notification rows via {@link
 * AnnouncementService#create} is genuine, not fabricated.
 */
@Component
@Profile("seed")
@ConditionalOnProperty(name = "smartcampus.seed.enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    // Activation-contract constants (PROJECT_PLAN.md / AGENT_CONTEXT.md Addendum 4 —
    // fixed exactly, another agent documents this same table in the README).
    private static final String ADMIN_EMAIL = "admin@smartcampus.local";
    private static final String ADMIN_PASSWORD = "Admin@Dev12345";
    private static final String FACULTY_PASSWORD = "Faculty@Dev12345";
    private static final String STUDENT_PASSWORD = "Student@Dev12345";

    private static final String ACADEMIC_YEAR = "2025-2026";
    private static final String SECTION = "A";
    private static final int SEMESTER = 3;
    private static final int ADMISSION_YEAR = 2024;

    // Fixed, always-in-the-past dates (see seedAttendance/seedExamsAndMarks javadoc for
    // why these are hardcoded rather than computed from "now").
    private static final LocalDate EXAM_DATE_INTERNAL_1 = LocalDate.of(2026, 2, 10);
    private static final LocalDate EXAM_DATE_INTERNAL_2 = LocalDate.of(2026, 4, 10);
    private static final LocalDate EXAM_DATE_SEMESTER = LocalDate.of(2026, 5, 20);
    private static final LocalDate ATTENDANCE_LAST_DAY = LocalDate.of(2026, 5, 15);
    private static final int ATTENDANCE_SCHOOL_DAYS = 20;

    private enum Tier {
        HIGH,
        MID,
        AT_RISK
    }

    private record DeptDef(String code, String name) {}

    private record SubjectDef(String code, String name, int credits, int semester) {}

    /**
     * {@code deptCode} is carried alongside the entity rather than read back off {@code
     * student.getDepartment().getCode()}: {@code Student.department} is a lazy {@code
     * @ManyToOne}, and every {@link Student} handled by this seeder is a detached entity
     * returned from a short-lived repository call (each Spring Data repository method is
     * its own transaction) — traversing a lazy association on it later, outside any
     * session, would throw {@code LazyInitializationException}. The department code is
     * already known at the point each student is created (see {@link #seedStudents}), so
     * it is captured directly instead. The same reasoning is why every "faculty as caller"
     * lookup in this file goes through {@link #userOf(Faculty)} rather than {@code
     * faculty.getUser()} plus a lazy traversal — see that method's javadoc.
     */
    private record SeededStudent(Student student, String deptCode, Tier tier) {}

    private static final List<DeptDef> DEPARTMENTS =
            List.of(
                    new DeptDef("CSE", "Computer Science and Engineering"),
                    new DeptDef("ECE", "Electronics and Communication Engineering"),
                    new DeptDef("MECH", "Mechanical Engineering"),
                    new DeptDef("IT", "Information Technology"));

    /** Two SEM-3 subjects (enrolled, taught, examined) + two SEM-5 subjects (catalog only) per dept. */
    private static Map<String, List<SubjectDef>> subjectDefsByDept() {
        Map<String, List<SubjectDef>> map = new LinkedHashMap<>();
        map.put(
                "CSE",
                List.of(
                        new SubjectDef("CSE301", "Data Structures and Algorithms", 4, 3),
                        new SubjectDef("CSE302", "Object Oriented Programming", 3, 3),
                        new SubjectDef("CSE501", "Database Management Systems", 4, 5),
                        new SubjectDef("CSE502", "Operating Systems", 3, 5)));
        map.put(
                "ECE",
                List.of(
                        new SubjectDef("ECE301", "Electronic Devices and Circuits", 4, 3),
                        new SubjectDef("ECE302", "Digital Logic Design", 3, 3),
                        new SubjectDef("ECE501", "Signals and Systems", 4, 5),
                        new SubjectDef("ECE502", "Communication Systems", 3, 5)));
        map.put(
                "MECH",
                List.of(
                        new SubjectDef("MECH301", "Thermodynamics", 4, 3),
                        new SubjectDef("MECH302", "Strength of Materials", 3, 3),
                        new SubjectDef("MECH501", "Fluid Mechanics", 4, 5),
                        new SubjectDef("MECH502", "Manufacturing Technology", 3, 5)));
        map.put(
                "IT",
                List.of(
                        new SubjectDef("IT301", "Web Technologies", 4, 3),
                        new SubjectDef("IT302", "Computer Networks", 3, 3),
                        new SubjectDef("IT501", "Software Engineering", 4, 5),
                        new SubjectDef("IT502", "Cloud Computing", 3, 5)));
        return map;
    }

    private static final String[] STUDENT_NAMES = {
        "Aditi Sharma", "Rohan Verma", "Kabir Singh",
        "Meera Nair", "Arjun Reddy", "Priya Menon",
        "Sanjay Gupta", "Divya Krishnan", "Vikram Rao",
        "Ananya Joshi", "Farhan Ali", "Ishita Kapoor"
    };

    private static final String[] FACULTY_NAMES = {
        "Dr. Ramesh Iyer", "Dr. Sunita Rao", "Prof. Arvind Nair", "Dr. Meera Pillai"
    };

    // ------------------------------------------------------------------------------
    // Dependencies
    // ------------------------------------------------------------------------------

    private final Environment environment;

    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;
    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final SubjectRepository subjectRepository;
    private final SubjectService subjectService;

    private final UserRepository userRepository;
    private final AuthService authService;
    private final UserProvisioningService userProvisioningService;

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final FacultyRepository facultyRepository;
    private final FacultyService facultyService;

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final FacultySubjectAssignmentRepository facultySubjectAssignmentRepository;
    private final FacultySubjectAssignmentService facultySubjectAssignmentService;

    private final ExamRepository examRepository;
    private final ExamService examService;
    private final AttendanceService attendanceService;
    private final MarksService marksService;

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;
    private final JobRepository jobRepository;
    private final JobService jobService;

    private final CodingProblemRepository codingProblemRepository;
    private final CodingProblemService codingProblemService;
    private final ProblemTestCaseRepository problemTestCaseRepository;
    private final CodingContestRepository codingContestRepository;
    private final CodingContestService codingContestService;
    private final ContestProblemRepository contestProblemRepository;

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementService announcementService;

    public DevDataSeeder(
            Environment environment,
            DepartmentRepository departmentRepository,
            DepartmentService departmentService,
            CourseRepository courseRepository,
            CourseService courseService,
            SubjectRepository subjectRepository,
            SubjectService subjectService,
            UserRepository userRepository,
            AuthService authService,
            UserProvisioningService userProvisioningService,
            StudentRepository studentRepository,
            StudentService studentService,
            FacultyRepository facultyRepository,
            FacultyService facultyService,
            EnrollmentRepository enrollmentRepository,
            EnrollmentService enrollmentService,
            FacultySubjectAssignmentRepository facultySubjectAssignmentRepository,
            FacultySubjectAssignmentService facultySubjectAssignmentService,
            ExamRepository examRepository,
            ExamService examService,
            AttendanceService attendanceService,
            MarksService marksService,
            CompanyRepository companyRepository,
            CompanyService companyService,
            JobRepository jobRepository,
            JobService jobService,
            CodingProblemRepository codingProblemRepository,
            CodingProblemService codingProblemService,
            ProblemTestCaseRepository problemTestCaseRepository,
            CodingContestRepository codingContestRepository,
            CodingContestService codingContestService,
            ContestProblemRepository contestProblemRepository,
            AnnouncementRepository announcementRepository,
            AnnouncementService announcementService) {
        this.environment = environment;
        this.departmentRepository = departmentRepository;
        this.departmentService = departmentService;
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.subjectRepository = subjectRepository;
        this.subjectService = subjectService;
        this.userRepository = userRepository;
        this.authService = authService;
        this.userProvisioningService = userProvisioningService;
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.facultyRepository = facultyRepository;
        this.facultyService = facultyService;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
        this.facultySubjectAssignmentRepository = facultySubjectAssignmentRepository;
        this.facultySubjectAssignmentService = facultySubjectAssignmentService;
        this.examRepository = examRepository;
        this.examService = examService;
        this.attendanceService = attendanceService;
        this.marksService = marksService;
        this.companyRepository = companyRepository;
        this.companyService = companyService;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.codingProblemRepository = codingProblemRepository;
        this.codingProblemService = codingProblemService;
        this.problemTestCaseRepository = problemTestCaseRepository;
        this.codingContestRepository = codingContestRepository;
        this.codingContestService = codingContestService;
        this.contestProblemRepository = contestProblemRepository;
        this.announcementRepository = announcementRepository;
        this.announcementService = announcementService;
    }

    // ------------------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile)) {
                throw new IllegalStateException(
                        "DevDataSeeder refuses to run with the 'prod' profile active, even though"
                                + " the 'seed' profile and smartcampus.seed.enabled=true were also set."
                                + " This is a hard kill-switch, not a warning — fix the environment"
                                + " configuration and restart.");
            }
        }

        log.warn(
                """

                ================================================================================
                 DevDataSeeder: installing DEVELOPMENT-ONLY seed data and accounts.
                 These passwords are for local development ONLY. Never use them, or accounts
                 provisioned this way, in any real deployment.

                   ADMIN    %s                        / %s
                   FACULTY  faculty1..faculty4@smartcampus.local / %s
                   STUDENT  student1..student12@smartcampus.local / %s
                ================================================================================
                """
                        .formatted(ADMIN_EMAIL, ADMIN_PASSWORD, FACULTY_PASSWORD, STUDENT_PASSWORD));

        seed();

        log.info("DevDataSeeder: seeding pass complete.");
    }

    /**
     * Runs the whole seed pass. Public and side-effect-idempotent so a test can call it
     * twice in the same Spring context — see this class's javadoc.
     */
    public void seed() {
        Map<String, Department> departments = seedDepartments();
        Map<String, Course> courses = seedCourses(departments);
        Map<String, List<Subject>> activeSubjects = seedSubjects(courses);

        User admin = ensureAdmin(ADMIN_EMAIL, ADMIN_PASSWORD, "SmartCampus Admin");
        Map<String, Faculty> facultyByDept = seedFaculty(departments, admin);
        List<SeededStudent> students = seedStudents(departments, courses, admin);

        seedEnrollments(students, activeSubjects);
        seedFacultySubjectAssignments(facultyByDept, activeSubjects);
        seedExamsAndMarks(activeSubjects, students, facultyByDept);
        seedAttendance(activeSubjects, students, facultyByDept);

        seedCompaniesAndJobs(departments, admin);
        List<CodingProblem> problems = seedCodingProblems(admin);
        seedContests(admin, problems);
        seedAnnouncements(admin, departments);
    }

    // ------------------------------------------------------------------------------
    // Departments / courses / subjects
    // ------------------------------------------------------------------------------

    private Map<String, Department> seedDepartments() {
        Map<String, Department> result = new LinkedHashMap<>();
        for (DeptDef def : DEPARTMENTS) {
            result.put(def.code(), ensureDepartment(def.code(), def.name()));
        }
        return result;
    }

    private Department ensureDepartment(String code, String name) {
        return departmentRepository
                .findByCode(code)
                .orElseGet(
                        () -> {
                            departmentService.create(new DepartmentCreateRequest(code, name));
                            return departmentRepository
                                    .findByCode(code)
                                    .orElseThrow(() -> new IllegalStateException("Department not found after create: " + code));
                        });
    }

    private Map<String, Course> seedCourses(Map<String, Department> departments) {
        Map<String, Course> result = new LinkedHashMap<>();
        for (DeptDef def : DEPARTMENTS) {
            Department dept = departments.get(def.code());
            String courseCode = def.code() + "-BTECH";
            String courseName = "B.Tech " + def.name();
            result.put(def.code(), ensureCourse(courseCode, courseName, dept));
        }
        return result;
    }

    private Course ensureCourse(String code, String name, Department department) {
        return courseRepository
                .findByCode(code)
                .orElseGet(
                        () -> {
                            courseService.create(new CourseCreateRequest(code, name, department.getId(), 8));
                            return courseRepository
                                    .findByCode(code)
                                    .orElseThrow(() -> new IllegalStateException("Course not found after create: " + code));
                        });
    }

    /** Returns, per department code, the two SEM-3 subjects (period-1 and period-2) that carry enrollments. */
    private Map<String, List<Subject>> seedSubjects(Map<String, Course> courses) {
        Map<String, List<Subject>> activeByDept = new LinkedHashMap<>();
        Map<String, List<SubjectDef>> defsByDept = subjectDefsByDept();

        for (DeptDef def : DEPARTMENTS) {
            Course course = courses.get(def.code());
            List<Subject> active = new ArrayList<>();
            for (SubjectDef subjectDef : defsByDept.get(def.code())) {
                Subject subject = ensureSubject(subjectDef, course);
                if (subjectDef.semester() == SEMESTER) {
                    active.add(subject);
                }
            }
            activeByDept.put(def.code(), active);
        }
        return activeByDept;
    }

    private Subject ensureSubject(SubjectDef def, Course course) {
        return subjectRepository
                .findByCode(def.code())
                .orElseGet(
                        () -> {
                            subjectService.create(
                                    new SubjectCreateRequest(
                                            def.code(), def.name(), def.credits(), def.semester(), course.getId()));
                            return subjectRepository
                                    .findByCode(def.code())
                                    .orElseThrow(() -> new IllegalStateException("Subject not found after create: " + def.code()));
                        });
    }

    // ------------------------------------------------------------------------------
    // Accounts: admin / faculty / students
    // ------------------------------------------------------------------------------

    private User ensureAdmin(String email, String password, String fullName) {
        return userRepository
                .findByEmail(email)
                .orElseGet(
                        () -> {
                            userProvisioningService.provision(
                                    new ProvisionUserRequest(email, password, fullName, Role.ADMIN));
                            return userRepository
                                    .findByEmail(email)
                                    .orElseThrow(() -> new IllegalStateException("Admin not found after provision: " + email));
                        });
    }

    private Map<String, Faculty> seedFaculty(Map<String, Department> departments, User admin) {
        Map<String, Faculty> result = new LinkedHashMap<>();
        for (int i = 0; i < DEPARTMENTS.size(); i++) {
            DeptDef def = DEPARTMENTS.get(i);
            String email = "faculty" + (i + 1) + "@smartcampus.local";
            String employeeCode = "FAC-" + def.code() + "-001";
            Faculty faculty =
                    ensureFaculty(
                            email,
                            FACULTY_PASSWORD,
                            FACULTY_NAMES[i],
                            employeeCode,
                            departments.get(def.code()),
                            "Assistant Professor",
                            admin);
            result.put(def.code(), faculty);
        }
        return result;
    }

    private Faculty ensureFaculty(
            String email,
            String password,
            String fullName,
            String employeeCode,
            Department department,
            String designation,
            User admin) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseGet(
                                () -> {
                                    userProvisioningService.provision(
                                            new ProvisionUserRequest(email, password, fullName, Role.FACULTY));
                                    return userRepository
                                            .findByEmail(email)
                                            .orElseThrow(
                                                    () -> new IllegalStateException("Faculty user not found after provision: " + email));
                                });

        return facultyRepository
                .findByUserId(user.getId())
                .orElseGet(
                        () -> {
                            facultyService.create(
                                    new FacultyCreateRequest(user.getId(), employeeCode, department.getId(), designation),
                                    admin);
                            return facultyRepository
                                    .findByUserId(user.getId())
                                    .orElseThrow(
                                            () -> new IllegalStateException("Faculty profile not found after create: " + email));
                        });
    }

    /**
     * Returns a fresh, directly-queried {@link User} for this faculty's account, safe to
     * use as a {@code caller} argument anywhere afterward — including after the current
     * method returns and any transaction that produced {@code faculty} has closed.
     *
     * <p>{@code faculty.getUser()} is a lazy {@code @OneToOne}; on a detached {@link
     * Faculty} (which every {@code Faculty} handled by this seeder is, moments after any
     * repository call returns) traversing it further than {@code .getId()} would throw
     * {@code LazyInitializationException}. {@code .getId()} itself is safe even on an
     * uninitialized proxy — Hibernate proxies always know their own identifier without a
     * session — so this re-queries {@link User} directly by that id. {@link User} has no
     * lazy fields of its own (it carries no relationships), so the object this returns
     * stays safe to use indefinitely, detached or not.
     */
    private User userOf(Faculty faculty) {
        return userRepository
                .findById(faculty.getUser().getId())
                .orElseThrow(
                        () -> new IllegalStateException("User not found for faculty id " + faculty.getId()));
    }

    /**
     * 12 students, 3 per department, at SEMESTER/SECTION above. Within each department
     * triplet the tier order is fixed — index 0 HIGH (heads toward EXCELLENT), index 1 MID
     * (heads toward GOOD), index 2 AT_RISK (attendance deliberately below the 75%
     * threshold, so it classifies AT_RISK regardless of marks — see {@link
     * smartcampus.service.PerformanceClassifier}: AT_RISK is the unconditional catch-all
     * for anyone who does not meet AVERAGE's marks/attendance floor).
     */
    private List<SeededStudent> seedStudents(
            Map<String, Department> departments, Map<String, Course> courses, User admin) {
        List<SeededStudent> result = new ArrayList<>();
        Tier[] tiers = {Tier.HIGH, Tier.MID, Tier.AT_RISK};

        int studentIndex = 0;
        for (DeptDef def : DEPARTMENTS) {
            Department department = departments.get(def.code());
            Course course = courses.get(def.code());
            for (int slot = 0; slot < 3; slot++) {
                studentIndex++;
                String email = "student" + studentIndex + "@smartcampus.local";
                String registerNumber = def.code() + ADMISSION_YEAR + String.format("%03d", slot + 1);
                Student student =
                        ensureStudent(
                                email,
                                STUDENT_PASSWORD,
                                STUDENT_NAMES[studentIndex - 1],
                                registerNumber,
                                department,
                                course,
                                admin);
                result.add(new SeededStudent(student, def.code(), tiers[slot]));
            }
        }
        return result;
    }

    private Student ensureStudent(
            String email,
            String password,
            String fullName,
            String registerNumber,
            Department department,
            Course course,
            User admin) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseGet(
                                () -> {
                                    // AuthService.register hardcodes Role.STUDENT and creates the matching
                                    // PENDING Student row in the same transaction (G1) — exactly the pair
                                    // ensureStudent needs.
                                    authService.register(new RegisterRequest(email, password, fullName));
                                    return userRepository
                                            .findByEmail(email)
                                            .orElseThrow(
                                                    () -> new IllegalStateException("Student user not found after register: " + email));
                                });

        Student student =
                studentRepository
                        .findByUserId(user.getId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Student profile missing for user " + email + " — AuthService.register should"
                                                        + " have created it atomically."));

        if (student.getStatus() != StudentStatus.ACTIVE) {
            studentService.activate(
                    student.getId(),
                    new StudentActivateRequest(
                            registerNumber, department.getId(), course.getId(), SEMESTER, SECTION, ADMISSION_YEAR),
                    admin);
            student =
                    studentRepository
                            .findByUserId(user.getId())
                            .orElseThrow(() -> new IllegalStateException("Student not found after activate: " + email));
        }
        return student;
    }

    // ------------------------------------------------------------------------------
    // Enrollments / faculty-subject assignments
    // ------------------------------------------------------------------------------

    private void seedEnrollments(List<SeededStudent> students, Map<String, List<Subject>> activeSubjects) {
        for (SeededStudent seeded : students) {
            for (Subject subject : activeSubjects.get(seeded.deptCode())) {
                ensureEnrollment(seeded.student().getId(), subject.getId());
            }
        }
    }

    private void ensureEnrollment(Long studentId, Long subjectId) {
        if (enrollmentRepository
                .findByStudentIdAndSubjectIdAndAcademicYearAndSemester(studentId, subjectId, ACADEMIC_YEAR, SEMESTER)
                .isPresent()) {
            return;
        }
        enrollmentService.enroll(new EnrollmentRequest(studentId, subjectId, ACADEMIC_YEAR, SEMESTER, SECTION));
    }

    /**
     * Assigns each department's faculty to that department's two SEM-3 subjects, for the
     * exact same (academicYear, semester, section) tuple the enrollments above use. This
     * is load-bearing: {@code AcademicAccessGuard} denies by default, so a faculty write
     * (attendance, marks) against any tuple not created here is correctly rejected.
     */
    private void seedFacultySubjectAssignments(
            Map<String, Faculty> facultyByDept, Map<String, List<Subject>> activeSubjects) {
        for (DeptDef def : DEPARTMENTS) {
            Faculty faculty = facultyByDept.get(def.code());
            for (Subject subject : activeSubjects.get(def.code())) {
                ensureAssignment(faculty.getId(), subject.getId());
            }
        }
    }

    private void ensureAssignment(Long facultyId, Long subjectId) {
        if (facultySubjectAssignmentRepository
                .findByFacultyIdAndSubjectIdAndAcademicYearAndSemesterAndSection(
                        facultyId, subjectId, ACADEMIC_YEAR, SEMESTER, SECTION)
                .isPresent()) {
            return;
        }
        facultySubjectAssignmentService.assign(
                new FacultySubjectAssignmentRequest(facultyId, subjectId, ACADEMIC_YEAR, SEMESTER, SECTION));
    }

    // ------------------------------------------------------------------------------
    // Exams and marks
    // ------------------------------------------------------------------------------

    /**
     * Marks percentage per tier, held identical across both of a department's subjects for
     * simplicity: HIGH ~91.5% (clears EXCELLENT's 85% marks floor), MID ~70.5% (clears
     * GOOD's 70% floor), AT_RISK ~43.5% (fails even AVERAGE's 50% floor).
     */
    private void seedExamsAndMarks(
            Map<String, List<Subject>> activeSubjects, List<SeededStudent> students, Map<String, Faculty> facultyByDept) {
        record ExamDef(ExamType type, String titlePrefix, BigDecimal maxMarks, LocalDate date) {}
        List<ExamDef> examDefs =
                List.of(
                        new ExamDef(ExamType.INTERNAL_1, "Internal Assessment 1", new BigDecimal("50.00"), EXAM_DATE_INTERNAL_1),
                        new ExamDef(ExamType.INTERNAL_2, "Internal Assessment 2", new BigDecimal("50.00"), EXAM_DATE_INTERNAL_2),
                        new ExamDef(ExamType.SEMESTER, "Semester Examination", new BigDecimal("100.00"), EXAM_DATE_SEMESTER));

        for (DeptDef def : DEPARTMENTS) {
            Faculty faculty = facultyByDept.get(def.code());
            User facultyUser = userOf(faculty);
            List<Student> deptStudents =
                    students.stream()
                            .filter(s -> s.deptCode().equals(def.code()))
                            .map(SeededStudent::student)
                            .toList();
            Map<Long, Tier> tierByStudentId = new LinkedHashMap<>();
            for (SeededStudent seeded : students) {
                if (seeded.deptCode().equals(def.code())) {
                    tierByStudentId.put(seeded.student().getId(), seeded.tier());
                }
            }

            for (Subject subject : activeSubjects.get(def.code())) {
                for (ExamDef examDef : examDefs) {
                    String title = examDef.titlePrefix() + " - " + subject.getCode();
                    Exam exam =
                            ensureExam(
                                    subject, examDef.type(), title, examDef.date(), examDef.maxMarks(), facultyUser);

                    List<MarksEntry> entries = new ArrayList<>();
                    for (Student student : deptStudents) {
                        Tier tier = tierByStudentId.get(student.getId());
                        BigDecimal obtained = marksFor(examDef.type(), tier);
                        entries.add(new MarksEntry(student.getId(), obtained, null));
                    }
                    marksService.bulkUpsert(new MarksBulkRequest(exam.getId(), entries), facultyUser);
                }
            }
        }
    }

    private BigDecimal marksFor(ExamType type, Tier tier) {
        return switch (type) {
            case INTERNAL_1 ->
                    switch (tier) {
                        case HIGH -> new BigDecimal("45.00");
                        case MID -> new BigDecimal("35.00");
                        case AT_RISK -> new BigDecimal("20.00");
                    };
            case INTERNAL_2 ->
                    switch (tier) {
                        case HIGH -> new BigDecimal("46.00");
                        case MID -> new BigDecimal("36.00");
                        case AT_RISK -> new BigDecimal("22.00");
                    };
            default -> // SEMESTER
                    switch (tier) {
                        case HIGH -> new BigDecimal("92.00");
                        case MID -> new BigDecimal("70.00");
                        case AT_RISK -> new BigDecimal("45.00");
                    };
        };
    }

    private Exam ensureExam(
            Subject subject, ExamType type, String title, LocalDate date, BigDecimal maxMarks, User facultyCaller) {
        Optional<Exam> existing =
                examRepository.findBySubjectIdAndAcademicYearAndSemesterAndSectionAndExamTypeAndTitle(
                        subject.getId(), ACADEMIC_YEAR, SEMESTER, SECTION, type, title);
        if (existing.isPresent()) {
            return existing.get();
        }
        examService.create(
                new ExamCreateRequest(subject.getId(), title, type, ACADEMIC_YEAR, SEMESTER, SECTION, date, maxMarks),
                facultyCaller);
        return examRepository
                .findBySubjectIdAndAcademicYearAndSemesterAndSectionAndExamTypeAndTitle(
                        subject.getId(), ACADEMIC_YEAR, SEMESTER, SECTION, type, title)
                .orElseThrow(() -> new IllegalStateException("Exam not found after create: " + title));
    }

    // ------------------------------------------------------------------------------
    // Attendance
    // ------------------------------------------------------------------------------

    /**
     * {@link #ATTENDANCE_SCHOOL_DAYS} weekdays per enrolled subject, ending at the fixed
     * {@link #ATTENDANCE_LAST_DAY}. The date is a hardcoded 2026 calendar date rather than
     * computed from "now" so this dataset is exactly reproducible across repeated seeding
     * runs (idempotency) and always satisfies {@code AttendanceBulkRequest}'s {@code
     * @PastOrPresent} constraint for any boot from today onward.
     *
     * <p>Absences are front-loaded onto the first N of the 20 days per tier — HIGH misses
     * 1/20 (95%, clears EXCELLENT's 90% attendance floor), MID misses 3/20 (85%, clears
     * GOOD's 80% floor), AT_RISK misses 12/20 (40%, well below the 75% low-attendance
     * warning threshold AND below AVERAGE's 75% floor, so it falls through to AT_RISK).
     * The two subjects per department use periods 1 and 2 on the same dates so the same
     * roster is not double-booked into the same period.
     */
    private void seedAttendance(
            Map<String, List<Subject>> activeSubjects, List<SeededStudent> students, Map<String, Faculty> facultyByDept) {
        List<LocalDate> days = lastNWeekdays(ATTENDANCE_LAST_DAY, ATTENDANCE_SCHOOL_DAYS);

        for (DeptDef def : DEPARTMENTS) {
            Faculty faculty = facultyByDept.get(def.code());
            User facultyUser = userOf(faculty);
            List<SeededStudent> deptStudents =
                    students.stream().filter(s -> s.deptCode().equals(def.code())).toList();

            List<Subject> subjects = activeSubjects.get(def.code());
            for (int subjectIndex = 0; subjectIndex < subjects.size(); subjectIndex++) {
                Subject subject = subjects.get(subjectIndex);
                int period = subjectIndex + 1;

                for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
                    LocalDate date = days.get(dayIndex);
                    List<AttendanceMarkEntry> entries = new ArrayList<>();
                    for (SeededStudent seeded : deptStudents) {
                        int absenceCount = absenceCountFor(seeded.tier());
                        AttendanceStatus status = dayIndex < absenceCount ? AttendanceStatus.ABSENT : AttendanceStatus.PRESENT;
                        entries.add(new AttendanceMarkEntry(seeded.student().getId(), status, null));
                    }
                    attendanceService.bulkMark(
                            new AttendanceBulkRequest(
                                    subject.getId(), ACADEMIC_YEAR, SEMESTER, SECTION, date, period, entries),
                            facultyUser);
                }
            }
        }
    }

    private int absenceCountFor(Tier tier) {
        return switch (tier) {
            case HIGH -> 1;
            case MID -> 3;
            case AT_RISK -> 12;
        };
    }

    private List<LocalDate> lastNWeekdays(LocalDate lastDay, int n) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = lastDay;
        while (days.size() < n) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        // Oldest first, so day indices below read chronologically.
        return days.reversed();
    }

    // ------------------------------------------------------------------------------
    // Placement: companies and jobs
    // ------------------------------------------------------------------------------

    private void seedCompaniesAndJobs(Map<String, Department> departments, User admin) {
        Company techNova =
                ensureCompany(
                        new CompanyCreateRequest(
                                "TechNova Solutions",
                                "Software",
                                "https://technova.example.com",
                                "A software product company building cloud-native platforms.",
                                "Bengaluru, India",
                                "Nisha Kapoor",
                                "hr@technova.example.com",
                                "+91-9800000001"),
                        admin);
        Company circuitDynamics =
                ensureCompany(
                        new CompanyCreateRequest(
                                "Circuit Dynamics",
                                "Electronics",
                                "https://circuitdynamics.example.com",
                                "Designs and manufactures embedded systems and consumer electronics.",
                                "Pune, India",
                                "Rakesh Menon",
                                "careers@circuitdynamics.example.com",
                                "+91-9800000002"),
                        admin);
        Company buildRight =
                ensureCompany(
                        new CompanyCreateRequest(
                                "BuildRight Engineering",
                                "Manufacturing",
                                "https://buildright.example.com",
                                "Heavy machinery and mechanical design consultancy.",
                                "Chennai, India",
                                "Geeta Iyer",
                                "jobs@buildright.example.com",
                                "+91-9800000003"),
                        admin);
        Company globalInfo =
                ensureCompany(
                        new CompanyCreateRequest(
                                "Global InfoSystems",
                                "IT Services",
                                "https://globalinfosystems.example.com",
                                "IT consulting and managed services across banking and retail.",
                                "Hyderabad, India",
                                "Suresh Babu",
                                "talent@globalinfosystems.example.com",
                                "+91-9800000004"),
                        admin);

        LocalDateTime deadline1 = LocalDateTime.now().plusDays(30);
        LocalDateTime deadline2 = LocalDateTime.now().plusDays(45);
        LocalDateTime deadline3 = LocalDateTime.now().plusDays(20);

        ensureJob(
                new JobCreateRequest(
                        techNova.getId(),
                        "Software Engineer",
                        "Full-stack software engineering role building customer-facing products.",
                        "Bengaluru, India",
                        JobType.FULL_TIME,
                        5,
                        new BigDecimal("600000.00"),
                        new BigDecimal("1200000.00"),
                        "INR",
                        new BigDecimal("7.50"),
                        new BigDecimal("70.00"),
                        2028,
                        List.of(departments.get("CSE").getId(), departments.get("IT").getId()),
                        deadline1,
                        null,
                        JobStatus.OPEN),
                admin);

        ensureJob(
                new JobCreateRequest(
                        techNova.getId(),
                        "Backend Intern",
                        "Six-month backend engineering internship.",
                        "Bengaluru, India",
                        JobType.INTERNSHIP,
                        10,
                        new BigDecimal("25000.00"),
                        new BigDecimal("35000.00"),
                        "INR",
                        new BigDecimal("6.00"),
                        null,
                        null,
                        List.of(),
                        deadline2,
                        null,
                        JobStatus.OPEN),
                admin);

        ensureJob(
                new JobCreateRequest(
                        techNova.getId(),
                        "Data Analyst",
                        "Analytics role requiring a strong academic record.",
                        "Bengaluru, India",
                        JobType.FULL_TIME,
                        2,
                        new BigDecimal("700000.00"),
                        new BigDecimal("1000000.00"),
                        "INR",
                        new BigDecimal("8.00"),
                        new BigDecimal("80.00"),
                        2028,
                        List.of(departments.get("CSE").getId()),
                        deadline3,
                        null,
                        JobStatus.OPEN),
                admin);

        ensureJob(
                new JobCreateRequest(
                        circuitDynamics.getId(),
                        "Embedded Systems Engineer",
                        "Firmware and embedded hardware design for consumer electronics.",
                        "Pune, India",
                        JobType.FULL_TIME,
                        3,
                        new BigDecimal("550000.00"),
                        new BigDecimal("900000.00"),
                        "INR",
                        new BigDecimal("7.00"),
                        null,
                        2028,
                        List.of(departments.get("ECE").getId()),
                        deadline1,
                        null,
                        JobStatus.OPEN),
                admin);

        ensureJob(
                new JobCreateRequest(
                        buildRight.getId(),
                        "Mechanical Design Engineer",
                        "CAD-driven mechanical design for industrial machinery.",
                        "Chennai, India",
                        JobType.FULL_TIME,
                        4,
                        new BigDecimal("500000.00"),
                        new BigDecimal("850000.00"),
                        "INR",
                        new BigDecimal("6.50"),
                        null,
                        2028,
                        List.of(departments.get("MECH").getId()),
                        deadline2,
                        null,
                        JobStatus.OPEN),
                admin);

        ensureJob(
                new JobCreateRequest(
                        globalInfo.getId(),
                        "IT Support Analyst",
                        "First-line IT support and systems administration.",
                        "Hyderabad, India",
                        JobType.FULL_TIME,
                        6,
                        new BigDecimal("400000.00"),
                        new BigDecimal("650000.00"),
                        "INR",
                        new BigDecimal("6.00"),
                        new BigDecimal("60.00"),
                        null,
                        List.of(departments.get("IT").getId(), departments.get("CSE").getId()),
                        deadline1,
                        null,
                        JobStatus.OPEN),
                admin);
    }

    private Company ensureCompany(CompanyCreateRequest request, User admin) {
        return companyRepository
                .findByNameIgnoreCase(request.name())
                .orElseGet(
                        () -> {
                            companyService.create(request, admin);
                            return companyRepository
                                    .findByNameIgnoreCase(request.name())
                                    .orElseThrow(
                                            () -> new IllegalStateException("Company not found after create: " + request.name()));
                        });
    }

    private Job ensureJob(JobCreateRequest request, User admin) {
        Specification<Job> byCompanyAndTitle =
                (root, query, cb) ->
                        cb.and(
                                cb.equal(root.get("company").get("id"), request.companyId()),
                                cb.equal(root.get("title"), request.title()));
        Optional<Job> existing = jobRepository.findOne(byCompanyAndTitle);
        if (existing.isPresent()) {
            return existing.get();
        }
        jobService.create(request, admin);
        return jobRepository
                .findOne(byCompanyAndTitle)
                .orElseThrow(() -> new IllegalStateException("Job not found after create: " + request.title()));
    }

    // ------------------------------------------------------------------------------
    // Coding problems, test cases, contests
    // ------------------------------------------------------------------------------

    private record ProblemDef(
            String slug,
            String title,
            String description,
            String inputFormat,
            String outputFormat,
            String sampleInput,
            String sampleOutput,
            ProblemDifficulty difficulty,
            List<TestCaseRequest> testCases) {}

    private List<CodingProblem> seedCodingProblems(User admin) {
        List<ProblemDef> defs =
                List.of(
                        new ProblemDef(
                                "sum-of-two-numbers",
                                "Sum of Two Numbers",
                                "Read two space-separated integers A and B on one line and print their sum.",
                                "One line: two integers A and B.",
                                "One line: the integer A + B.",
                                "3 5",
                                "8",
                                ProblemDifficulty.EASY,
                                List.of(
                                        new TestCaseRequest(1, "3 5", "8", true, 1),
                                        new TestCaseRequest(2, "-4 10", "6", true, 1),
                                        new TestCaseRequest(3, "0 0", "0", false, 1),
                                        new TestCaseRequest(4, "100000 250000", "350000", false, 1),
                                        new TestCaseRequest(5, "-7 -8", "-15", false, 1))),
                        new ProblemDef(
                                "reverse-a-string",
                                "Reverse a String",
                                "Read one line of text and print it reversed.",
                                "One line: a string S (no leading/trailing spaces).",
                                "One line: S reversed.",
                                "hello",
                                "olleh",
                                ProblemDifficulty.EASY,
                                List.of(
                                        new TestCaseRequest(1, "hello", "olleh", true, 1),
                                        new TestCaseRequest(2, "smartcampus", "supmactrams", true, 1),
                                        new TestCaseRequest(3, "a", "a", false, 1),
                                        new TestCaseRequest(4, "racecar", "racecar", false, 1),
                                        new TestCaseRequest(5, "coding", "gnidoc", false, 1))),
                        new ProblemDef(
                                "find-maximum-in-array",
                                "Find Maximum in Array",
                                "Read an integer N, then N space-separated integers on the next line. Print the maximum value.",
                                "Line 1: N. Line 2: N integers.",
                                "One line: the maximum of the N integers.",
                                "5\n3 7 2 9 4",
                                "9",
                                ProblemDifficulty.EASY,
                                List.of(
                                        new TestCaseRequest(1, "5\n3 7 2 9 4", "9", true, 1),
                                        new TestCaseRequest(2, "3\n-1 -5 -2", "-1", true, 1),
                                        new TestCaseRequest(3, "1\n42", "42", false, 1),
                                        new TestCaseRequest(4, "6\n1 1 1 1 1 9", "9", false, 1),
                                        new TestCaseRequest(5, "4\n0 0 0 0", "0", false, 1))),
                        new ProblemDef(
                                "check-palindrome",
                                "Check Palindrome",
                                "Read one line of lowercase text and print YES if it reads the same forwards and"
                                        + " backwards, otherwise print NO.",
                                "One line: a lowercase string S.",
                                "One line: YES or NO.",
                                "madam",
                                "YES",
                                ProblemDifficulty.EASY,
                                List.of(
                                        new TestCaseRequest(1, "madam", "YES", true, 1),
                                        new TestCaseRequest(2, "hello", "NO", true, 1),
                                        new TestCaseRequest(3, "a", "YES", false, 1),
                                        new TestCaseRequest(4, "level", "YES", false, 1),
                                        new TestCaseRequest(5, "smartcampus", "NO", false, 1))),
                        new ProblemDef(
                                "fibonacci-sequence",
                                "Fibonacci Sequence",
                                "Read a single non-negative integer N and print the N-th Fibonacci number, where"
                                        + " F(0)=0 and F(1)=1.",
                                "One line: N.",
                                "One line: F(N).",
                                "7",
                                "13",
                                ProblemDifficulty.MEDIUM,
                                List.of(
                                        new TestCaseRequest(1, "7", "13", true, 1),
                                        new TestCaseRequest(2, "0", "0", true, 1),
                                        new TestCaseRequest(3, "1", "1", false, 1),
                                        new TestCaseRequest(4, "10", "55", false, 1),
                                        new TestCaseRequest(5, "15", "610", false, 1))),
                        new ProblemDef(
                                "binary-search",
                                "Binary Search",
                                "Read N, then N sorted space-separated integers, then a target value T. Print the"
                                        + " 0-based index of T in the array, or -1 if T is not present.",
                                "Line 1: N. Line 2: N sorted integers. Line 3: T.",
                                "One line: the index of T, or -1.",
                                "5\n1 3 5 7 9\n7",
                                "3",
                                ProblemDifficulty.MEDIUM,
                                List.of(
                                        new TestCaseRequest(1, "5\n1 3 5 7 9\n7", "3", true, 1),
                                        new TestCaseRequest(2, "5\n1 3 5 7 9\n4", "-1", true, 1),
                                        new TestCaseRequest(3, "1\n10\n10", "0", false, 1),
                                        new TestCaseRequest(4, "4\n2 4 6 8\n2", "0", false, 1),
                                        new TestCaseRequest(5, "4\n2 4 6 8\n8", "3", false, 1))),
                        new ProblemDef(
                                "count-vowels",
                                "Count Vowels",
                                "Read one line of lowercase text and print the number of vowel characters"
                                        + " (a, e, i, o, u) it contains.",
                                "One line: a lowercase string S.",
                                "One line: the vowel count.",
                                "smartcampus",
                                "3",
                                ProblemDifficulty.EASY,
                                List.of(
                                        new TestCaseRequest(1, "smartcampus", "3", true, 1),
                                        new TestCaseRequest(2, "xyz", "0", true, 1),
                                        new TestCaseRequest(3, "aeiou", "5", false, 1),
                                        new TestCaseRequest(4, "education", "5", false, 1),
                                        new TestCaseRequest(5, "rhythm", "0", false, 1))),
                        new ProblemDef(
                                "matrix-transpose",
                                "Matrix Transpose",
                                "Read R and C, then an R-by-C matrix of integers (R lines of C integers each)."
                                        + " Print its transpose as C lines of R space-separated integers.",
                                "Line 1: R C. Next R lines: C integers each.",
                                "C lines: R space-separated integers each.",
                                "2 3\n1 2 3\n4 5 6",
                                "1 4\n2 5\n3 6",
                                ProblemDifficulty.HARD,
                                List.of(
                                        new TestCaseRequest(1, "2 3\n1 2 3\n4 5 6", "1 4\n2 5\n3 6", true, 1),
                                        new TestCaseRequest(2, "1 1\n7", "7", true, 1),
                                        new TestCaseRequest(3, "3 2\n1 2\n3 4\n5 6", "1 3 5\n2 4 6", false, 1),
                                        new TestCaseRequest(4, "2 2\n1 0\n0 1", "1 0\n0 1", false, 1),
                                        new TestCaseRequest(5, "1 3\n9 8 7", "9\n8\n7", false, 1))));

        List<CodingProblem> problems = new ArrayList<>();
        for (ProblemDef def : defs) {
            CodingProblem problem =
                    ensureProblem(
                            new ProblemCreateRequest(
                                    def.slug(),
                                    def.title(),
                                    def.description(),
                                    def.inputFormat(),
                                    def.outputFormat(),
                                    null,
                                    def.sampleInput(),
                                    def.sampleOutput(),
                                    def.difficulty(),
                                    2000,
                                    262144,
                                    List.of("seed"),
                                    true),
                            admin);
            for (TestCaseRequest testCase : def.testCases()) {
                ensureTestCase(problem, testCase, admin);
            }
            problems.add(problem);
        }
        return problems;
    }

    private CodingProblem ensureProblem(ProblemCreateRequest request, User admin) {
        return codingProblemRepository
                .findBySlug(request.slug())
                .orElseGet(
                        () -> {
                            codingProblemService.create(request, admin);
                            return codingProblemRepository
                                    .findBySlug(request.slug())
                                    .orElseThrow(
                                            () -> new IllegalStateException("Problem not found after create: " + request.slug()));
                        });
    }

    private void ensureTestCase(CodingProblem problem, TestCaseRequest request, User admin) {
        if (problemTestCaseRepository.existsByProblemIdAndOrdinal(problem.getId(), request.ordinal())) {
            return;
        }
        codingProblemService.createTestCase(problem.getId(), request, admin);
    }

    private void seedContests(User admin, List<CodingProblem> problems) {
        // problems[0..3] -> "easy four" used by the upcoming practice contest;
        // problems[4..7] -> the "hard four" used by the already-ended contest.
        CodingContest upcoming =
                ensureContest(
                        new ContestCreateRequest(
                                "practice-contest-2026",
                                "SmartCampus Practice Contest 2026",
                                "An upcoming practice contest covering fundamentals.",
                                LocalDateTime.now().plusDays(7),
                                LocalDateTime.now().plusDays(7).plusHours(3),
                                ContestStatus.PUBLISHED,
                                10),
                        admin);
        ensureContestProblem(upcoming, problems.get(0), 1, 100, admin);
        ensureContestProblem(upcoming, problems.get(1), 2, 100, admin);

        CodingContest ended =
                ensureContest(
                        new ContestCreateRequest(
                                "autumn-challenge-2026",
                                "Autumn Coding Challenge 2026",
                                "A concluded contest across medium/hard problems.",
                                LocalDateTime.now().minusDays(10),
                                LocalDateTime.now().minusDays(10).plusHours(3),
                                ContestStatus.PUBLISHED,
                                10),
                        admin);
        ensureContestProblem(ended, problems.get(4), 1, 150, admin);
        ensureContestProblem(ended, problems.get(5), 2, 200, admin);
    }

    private CodingContest ensureContest(ContestCreateRequest request, User admin) {
        return codingContestRepository
                .findBySlug(request.slug())
                .orElseGet(
                        () -> {
                            codingContestService.create(request, admin);
                            return codingContestRepository
                                    .findBySlug(request.slug())
                                    .orElseThrow(
                                            () -> new IllegalStateException("Contest not found after create: " + request.slug()));
                        });
    }

    private void ensureContestProblem(
            CodingContest contest, CodingProblem problem, int ordinal, int points, User admin) {
        if (contestProblemRepository.existsByContestIdAndProblemId(contest.getId(), problem.getId())) {
            return;
        }
        codingContestService.addProblem(
                contest.getId(), new ContestProblemRequest(problem.getId(), ordinal, points), admin);
    }

    // ------------------------------------------------------------------------------
    // Announcements
    // ------------------------------------------------------------------------------

    private void seedAnnouncements(User admin, Map<String, Department> departments) {
        ensureAnnouncement(
                new AnnouncementCreateRequest(
                        "Welcome to SmartCampus ERP",
                        "Welcome to the SmartCampus ERP portal. This board carries official"
                                + " announcements from the administration — academic, placement and"
                                + " campus-wide notices will all appear here.",
                        AnnouncementAudience.ALL,
                        null,
                        NotificationPriority.NORMAL,
                        null),
                admin);
        ensureAnnouncement(
                new AnnouncementCreateRequest(
                        "Campus Closed for Founder's Day",
                        "The campus will remain closed for Founder's Day. Regular classes resume the"
                                + " following working day.",
                        AnnouncementAudience.ALL,
                        null,
                        NotificationPriority.LOW,
                        null),
                admin);
        ensureAnnouncement(
                new AnnouncementCreateRequest(
                        "Semester Examination Schedule Released",
                        "The semester examination schedule for the current academic year has been"
                                + " published. Students should check their subject-wise exam dates and"
                                + " report to the examination hall 15 minutes early.",
                        AnnouncementAudience.STUDENTS,
                        null,
                        NotificationPriority.HIGH,
                        null),
                admin);
        ensureAnnouncement(
                new AnnouncementCreateRequest(
                        "Marks Entry Deadline Reminder",
                        "All faculty are reminded to complete marks entry for the current internal"
                                + " assessment cycle before the end of this week.",
                        AnnouncementAudience.FACULTY,
                        null,
                        NotificationPriority.HIGH,
                        null),
                admin);
        ensureAnnouncement(
                new AnnouncementCreateRequest(
                        "CSE Department Technical Workshop",
                        "The Computer Science and Engineering department is organizing a two-day"
                                + " workshop on cloud computing fundamentals. Interested students and"
                                + " faculty may register with the department office.",
                        AnnouncementAudience.DEPARTMENT,
                        departments.get("CSE").getId(),
                        NotificationPriority.NORMAL,
                        null),
                admin);
        ensureAnnouncement(
                new AnnouncementCreateRequest(
                        "IT Department Lab Maintenance Notice",
                        "The Information Technology department's networking lab will be undergoing"
                                + " scheduled maintenance. Lab sessions will be relocated for the duration —"
                                + " check with your faculty for the alternate venue.",
                        AnnouncementAudience.DEPARTMENT,
                        departments.get("IT").getId(),
                        NotificationPriority.NORMAL,
                        null),
                admin);
    }

    private void ensureAnnouncement(AnnouncementCreateRequest request, User admin) {
        Specification<Announcement> byTitle = (root, query, cb) -> cb.equal(root.get("title"), request.title());
        if (announcementRepository.findOne(byTitle).isPresent()) {
            return;
        }
        announcementService.create(request, admin);
    }
}
