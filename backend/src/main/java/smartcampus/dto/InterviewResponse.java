package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Interview;
import smartcampus.entity.InterviewMode;
import smartcampus.entity.InterviewOutcome;
import smartcampus.entity.InterviewStatus;
import smartcampus.entity.InterviewType;
import smartcampus.entity.Student;

/**
 * Response representation of a scheduled {@code Interview}. FLAT — the referenced
 * student is denormalized into {@code studentId}/{@code studentName}/
 * {@code studentRegisterNumber} scalars, never a nested object, matching the convention
 * {@code ExamResponse} already established.
 */
public record InterviewResponse(
        Long id,
        Long studentId,
        String studentName,
        String studentRegisterNumber,
        String title,
        InterviewType interviewType,
        String companyName,
        String roundName,
        InterviewMode mode,
        String meetingLink,
        String location,
        String interviewerName,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        InterviewStatus status,
        InterviewOutcome outcome,
        String feedback,
        String notes,
        String cancellationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code student} and {@code student.user} associations, so the
     * caller must still be inside the persistence context that loaded {@code interview}
     * (i.e. call this from within the {@code @Transactional} service method, not after
     * it returns).
     */
    public static InterviewResponse from(Interview i) {
        Student student = i.getStudent();
        return new InterviewResponse(
                i.getId(),
                student.getId(),
                student.getUser().getFullName(),
                student.getRegisterNumber(),
                i.getTitle(),
                i.getInterviewType(),
                i.getCompanyName(),
                i.getRoundName(),
                i.getMode(),
                i.getMeetingLink(),
                i.getLocation(),
                i.getInterviewerName(),
                i.getScheduledStart(),
                i.getScheduledEnd(),
                i.getStatus(),
                i.getOutcome(),
                i.getFeedback(),
                i.getNotes(),
                i.getCancellationReason(),
                i.getCreatedAt(),
                i.getUpdatedAt());
    }
}
