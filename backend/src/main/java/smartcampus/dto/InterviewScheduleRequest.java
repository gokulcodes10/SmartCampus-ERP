package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import smartcampus.entity.InterviewMode;
import smartcampus.entity.InterviewType;

/**
 * POST /api/interviews. {@code studentId} is required for an ADMIN caller (who may
 * schedule for any student) and must be null or the caller's own student id for a
 * STUDENT caller — see {@code InterviewSchedulingService#schedule}.
 */
public record InterviewScheduleRequest(
        Long studentId,
        @NotBlank @Size(max = 200) String title,
        @NotNull InterviewType interviewType,
        @Size(max = 150) String companyName,
        @Size(max = 100) String roundName,
        @NotNull InterviewMode mode,
        @Size(max = 500) String meetingLink,
        @Size(max = 255) String location,
        @Size(max = 150) String interviewerName,
        @NotNull LocalDateTime scheduledStart,
        @NotNull LocalDateTime scheduledEnd,
        String notes) {}
