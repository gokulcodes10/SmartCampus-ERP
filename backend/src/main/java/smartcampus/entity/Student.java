package smartcampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One row per STUDENT user; PENDING at registration, ACTIVE once an admin assigns
 * department, course and register number.
 *
 * <p>Maps exactly to the {@code students} table created by {@code V3__academic.sql}.
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>Self-registration (AuthService.register, Phase 2) creates the {@link User} row and MUST
 * also insert the matching pending student row here: department_id, course_id,
 * current_semester, section and register_number all NULL, status = 'PENDING'. An admin
 * activation endpoint (Phase 3) then sets all four and flips status to 'ACTIVE'. The
 * database CHECK constraint makes that transition impossible to do halfway
 * (PROJECT_PLAN.md clarification G1).
 */
@Entity
@Table(name = "students")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "register_number", nullable = true, unique = true, length = 20)
    private String registerNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = true)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = true)
    private Course course;

    @Column(name = "current_semester", nullable = true)
    private Integer currentSemester;

    @Column(name = "section", nullable = true, length = 10)
    private String section;

    @Column(name = "admission_year", nullable = true)
    private Integer admissionYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StudentStatus status = StudentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
