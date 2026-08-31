package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.Resume;

/**
 * Persistence access for {@link Resume}.
 *
 * <p>Supports querying and paginating resumes by student ownership and checking
 * duplicate titles.
 */
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    /**
     * Finds all resumes owned by a student with pagination support.
     *
     * @param studentId the student ID
     * @param pageable pagination parameters
     * @return a page of resumes owned by the student
     */
    Page<Resume> findByStudentId(Long studentId, Pageable pageable);

    /**
     * Finds a resume by ID if it belongs to the given student.
     *
     * <p>Used to enforce student ownership (404 if not found or not owned).
     *
     * @param id the resume ID
     * @param studentId the student ID
     * @return the resume if it exists and belongs to the student
     */
    Optional<Resume> findByIdAndStudentId(Long id, Long studentId);

    /**
     * Checks if a resume with the given title already exists for a student.
     *
     * <p>Guards against duplicate resume titles, which would be indistinguishable in
     * the apply form dropdown.
     *
     * @param studentId the student ID
     * @param title the resume title
     * @return true if a resume with this title exists for the student
     */
    boolean existsByStudentIdAndTitle(Long studentId, String title);

    /**
     * Counts the total number of resumes owned by a student.
     *
     * <p>Used to enforce the maximum resume limit (MAX_RESUMES_PER_STUDENT).
     *
     * @param studentId the student ID
     * @return the count of resumes owned by the student
     */
    long countByStudentId(Long studentId);
}
