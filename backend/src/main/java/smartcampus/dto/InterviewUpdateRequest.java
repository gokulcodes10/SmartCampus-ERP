package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.InterviewMode;
import smartcampus.entity.InterviewType;

/**
 * PUT /api/interviews/{id}. Deliberately carries NO times and NO status — those have
 * their own endpoints ({@code /reschedule} and {@code /status}).
 */
public record InterviewUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull InterviewType interviewType,
        @Size(max = 150) String companyName,
        @Size(max = 100) String roundName,
        @NotNull InterviewMode mode,
        @Size(max = 500) String meetingLink,
        @Size(max = 255) String location,
        @Size(max = 150) String interviewerName,
        String notes) {}
