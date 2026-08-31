package smartcampus.dto;

/**
 * The requested difficulty tier for a practice-questions or MCQ generation request.
 *
 * <p>Request vocabulary only — this is never persisted. The generated items are stored
 * as an {@code AIMessage.content} answer, not as structured rows carrying a difficulty
 * column.
 */
public enum AIDifficulty {
    EASY,
    MEDIUM,
    HARD
}
