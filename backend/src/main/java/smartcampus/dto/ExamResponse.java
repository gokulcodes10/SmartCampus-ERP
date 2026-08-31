package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.entity.Faculty;
import smartcampus.entity.Subject;

/**
 * Response representation of an {@code Exam}, safe to return from create, detail, list
 * and update endpoints. Carries denormalized subject/faculty display fields so a caller
 * does not need a second round trip to render an exam row, matching the convention
 * {@code EnrollmentResponse} already established.
 */
public record ExamResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer subjectCredits,
        Long facultyId,
        String facultyName,
        String title,
        ExamType examType,
        String academicYear,
        Integer semester,
        String section,
        LocalDate examDate,
        BigDecimal maximumMarks,
        ExamStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code subject} and {@code faculty} associations, so the caller
     * must still be inside the persistence context that loaded {@code exam} (i.e. call
     * this from within the {@code @Transactional} service method, not after it
     * returns).
     */
    public static ExamResponse from(Exam exam) {
        Subject subject = exam.getSubject();
        Faculty faculty = exam.getFaculty();
        return new ExamResponse(
                exam.getId(),
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.getCredits(),
                faculty != null ? faculty.getId() : null,
                faculty != null ? faculty.getUser().getFullName() : null,
                exam.getTitle(),
                exam.getExamType(),
                exam.getAcademicYear(),
                exam.getSemester(),
                exam.getSection(),
                exam.getExamDate(),
                exam.getMaximumMarks(),
                exam.getStatus(),
                exam.getCreatedAt(),
                exam.getUpdatedAt());
    }
}
