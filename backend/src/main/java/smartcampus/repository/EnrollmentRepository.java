package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Enrollment;

/**
 * Persistence access for {@link Enrollment}.
 *
 * <p>The roster that attendance and marks entry read from in Phase 4. A student cannot
 * be enrolled in the same subject twice in the same academic year/semester (enforced by
 * unique constraint in the database). {@link JpaSpecificationExecutor} supports dynamic
 * filtering and pagination. {@link #findBySubjectIdAndAcademicYearAndSemesterAndSection}
 * backs roster queries (attendance/marks entry screens).
 */
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long>,
    JpaSpecificationExecutor<Enrollment> {

    Optional<Enrollment> findByStudentIdAndSubjectIdAndAcademicYearAndSemester(
            Long studentId, Long subjectId, String academicYear, Integer semester);

    List<Enrollment> findByStudentId(Long studentId);

    List<Enrollment> findBySubjectIdAndAcademicYearAndSemesterAndSection(
            Long subjectId, String academicYear, Integer semester, String section);
}
