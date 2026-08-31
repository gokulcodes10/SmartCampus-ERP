package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One roster row in a {@link MarksEntrySheetResponse} — an actively-enrolled student,
 * with their existing mark for this exam if one has been entered. {@code marksId} /
 * {@code marksObtained} / {@code remarks} are all null when the student has not been
 * marked yet, exactly like {@code AttendanceRosterEntry} does for attendance.
 */
public record MarksEntrySheetEntry(
        Long studentId,
        String registerNumber,
        String studentName,
        Long marksId,
        BigDecimal marksObtained,
        String remarks) {}
