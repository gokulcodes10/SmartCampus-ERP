package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;

/**
 * Persistence access for {@link Student}.
 *
 * <p>{@code register_number} is unique but nullable (NULL for PENDING students).
 * {@link JpaSpecificationExecutor} supports dynamic filtering and pagination for admin
 * listing and search screens. {@link #findByUserId} backs the student profile lookup.
 * {@link #findByStatus} backs the admin pending-activation queue.
 */
public interface StudentRepository extends JpaRepository<Student, Long>,
    JpaSpecificationExecutor<Student> {

    Optional<Student> findByUserId(Long userId);

    boolean existsByRegisterNumber(String registerNumber);

    List<Student> findByStatus(StudentStatus status);

    List<Student> findByDepartmentIdAndCourseIdAndCurrentSemesterAndSection(
            Long departmentId, Long courseId, Integer currentSemester, String section);
}
