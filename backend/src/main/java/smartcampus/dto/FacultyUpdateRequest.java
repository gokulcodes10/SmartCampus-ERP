package smartcampus.dto;

import jakarta.validation.constraints.Size;
import smartcampus.entity.FacultyStatus;

/**
 * Admin edit of a faculty profile. Partial-update semantics: a field left {@code
 * null} is left unchanged.
 */
public record FacultyUpdateRequest(
        @Size(max = 20, message = "Employee code must be at most 20 characters.")
        String employeeCode,

        Long departmentId,

        @Size(max = 100, message = "Designation must be at most 100 characters.")
        String designation,

        FacultyStatus status) {
}
