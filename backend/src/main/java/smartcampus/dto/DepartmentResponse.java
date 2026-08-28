package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Department;

/**
 * Response representation of a {@code Department}, safe to return in list, detail,
 * create, and update endpoints.
 */
public record DepartmentResponse(
        Long id,
        String code,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
