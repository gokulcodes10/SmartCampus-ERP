package smartcampus.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.Role;
import smartcampus.entity.StudentStatus;
import smartcampus.entity.User;

/**
 * Persistence access for querying recipient user IDs for notification fan-out.
 * The domain type is {@code User}, but queries may target any entity to gather
 * enabled user IDs by role, department, student status, etc.
 *
 * <p><strong>DO NOT add methods to the existing
 * {@code UserRepository}.</strong> This is a separate interface to avoid
 * collisions with other agents' work.
 */
public interface NotificationRecipientRepository extends Repository<User, Long> {

    @Query("select u.id from User u where u.enabled = true")
    List<Long> findAllEnabledUserIds();

    @Query("select u.id from User u where u.enabled = true and u.role = :role")
    List<Long> findEnabledUserIdsByRole(@Param("role") Role role);

    @Query(
            "select s.user.id from Student s where s.user.enabled = true and s.department.id = :departmentId")
    List<Long> findEnabledStudentUserIdsByDepartment(@Param("departmentId") Long departmentId);

    @Query(
            "select f.user.id from Faculty f where f.user.enabled = true and f.department.id = :departmentId")
    List<Long> findEnabledFacultyUserIdsByDepartment(@Param("departmentId") Long departmentId);

    @Query(
            "select s.user.id from Student s where s.user.enabled = true and s.status = :status")
    List<Long> findEnabledStudentUserIdsByStatus(@Param("status") StudentStatus status);

    @Query(
            "select s.user.id from Student s where s.user.enabled = true and s.status = :status "
                    + "and s.department.id in :departmentIds")
    List<Long> findEnabledStudentUserIdsByStatusAndDepartments(
            @Param("status") StudentStatus status,
            @Param("departmentIds") Collection<Long> departmentIds);
}
