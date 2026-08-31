package smartcampus.dto;

import java.time.LocalDate;
import java.util.List;

/** Result of a bulk attendance submission — how many rows were created vs. updated. */
public record AttendanceBulkResponse(
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        LocalDate date,
        Integer period,
        int createdCount,
        int updatedCount,
        List<AttendanceResponse> records) {}
