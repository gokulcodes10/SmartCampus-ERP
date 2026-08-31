package smartcampus.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import smartcampus.entity.Attendance;
import smartcampus.entity.AttendanceStatus;
import smartcampus.entity.Faculty;

/**
 * Response representation of a single {@link Attendance} row. The JSON field is
 * {@code date}, mapped from the entity's {@code attendanceDate} — every frontend
 * screen (roster, "my attendance", corrections) reads and writes that name.
 */
public record AttendanceResponse(
        Long id,
        Long studentId,
        String studentRegisterNumber,
        String studentName,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        LocalDate date,
        Integer period,
        AttendanceStatus status,
        String remarks,
        Long markedByFacultyId,
        String markedByFacultyName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code student}, {@code subject} and (when present)
     * {@code markedByFaculty} associations, so the caller must still be inside the
     * persistence context that loaded {@code attendance}.
     */
    public static AttendanceResponse from(Attendance attendance) {
        Faculty markedBy = attendance.getMarkedByFaculty();
        return new AttendanceResponse(
                attendance.getId(),
                attendance.getStudent().getId(),
                attendance.getStudent().getRegisterNumber(),
                attendance.getStudent().getUser().getFullName(),
                attendance.getSubject().getId(),
                attendance.getSubject().getCode(),
                attendance.getSubject().getName(),
                attendance.getAcademicYear(),
                attendance.getSemester(),
                attendance.getSection(),
                attendance.getAttendanceDate(),
                attendance.getPeriod(),
                attendance.getStatus(),
                attendance.getRemarks(),
                markedBy != null ? markedBy.getId() : null,
                markedBy != null ? markedBy.getUser().getFullName() : null,
                attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }
}
