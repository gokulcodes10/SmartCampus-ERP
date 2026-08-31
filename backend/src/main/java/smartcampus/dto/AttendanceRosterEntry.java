package smartcampus.dto;

import smartcampus.entity.AttendanceStatus;

/**
 * One student's row on a class roster session ({@code GET /api/attendance/roster}).
 * {@code attendanceId}/{@code status}/{@code remarks} are {@code null} when the
 * student has not been marked yet for this (subject, date, period).
 */
public record AttendanceRosterEntry(
        Long studentId,
        String registerNumber,
        String studentName,
        Long attendanceId,
        AttendanceStatus status,
        String remarks) {}
