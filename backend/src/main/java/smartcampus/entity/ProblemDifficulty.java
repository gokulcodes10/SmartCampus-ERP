package smartcampus.entity;

/**
 * The difficulty tier of a coding problem.
 *
 * <p>Stored as the string name (not the ordinal) in {@code coding_problems.difficulty}.
 * Also used to weight the global leaderboard's difficulty points (§60 - the actual point
 * values are configuration, not literals baked in here).
 */
public enum ProblemDifficulty {
    EASY,
    MEDIUM,
    HARD
}
