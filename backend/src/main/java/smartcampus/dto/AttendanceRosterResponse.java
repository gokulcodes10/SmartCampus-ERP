package smartcampus.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The enrolled roster for one class session (subject, academic year, semester,
 * section, date, period), pre-filled with any attendance already recorded so the UI
 * can distinguish "mark this session" from "correct this session".
 */
public record AttendanceRosterResponse(
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        LocalDate date,
        Integer period,
        boolean alreadyMarked,
        List<AttendanceRosterEntry> entries) {}
