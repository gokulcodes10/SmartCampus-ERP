package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.FacultySubjectAssignment;

/**
 * Persistence access for {@link FacultySubjectAssignment}.
 *
 * <p>Every faculty authorization check in the application routes through this table
 * (PROJECT_PLAN.md clarification G2). The unique key serves both as a primary lookup
 * and as the foundation for authorization checks: "is this faculty assigned to this
 * subject (optionally + year/semester/section)?" via leftmost-prefix queries.
 * {@link JpaSpecificationExecutor} supports dynamic filtering and pagination.
 */
public interface FacultySubjectAssignmentRepository extends JpaRepository<FacultySubjectAssignment, Long>,
    JpaSpecificationExecutor<FacultySubjectAssignment> {

    Optional<FacultySubjectAssignment> findByFacultyIdAndSubjectIdAndAcademicYearAndSemesterAndSection(
            Long facultyId, Long subjectId, String academicYear, Integer semester, String section);

    List<FacultySubjectAssignment> findByFacultyId(Long facultyId);

    List<FacultySubjectAssignment> findBySubjectIdAndAcademicYearAndSemesterAndSection(
            Long subjectId, String academicYear, Integer semester, String section);

    List<FacultySubjectAssignment> findByFacultyIdAndSubjectId(Long facultyId, Long subjectId);
}
