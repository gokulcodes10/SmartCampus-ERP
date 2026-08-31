package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import smartcampus.entity.ExamType;

/** Response for {@code GET /api/marks/entry-sheet} — the faculty marks-entry screen's data. */
public record MarksEntrySheetResponse(
        Long examId,
        String examTitle,
        ExamType examType,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        LocalDate examDate,
        BigDecimal maximumMarks,
        int studentCount,
        int enteredCount,
        List<MarksEntrySheetEntry> entries) {}
