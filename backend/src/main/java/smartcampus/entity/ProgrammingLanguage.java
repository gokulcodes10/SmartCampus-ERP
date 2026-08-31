package smartcampus.entity;

/**
 * The programming languages the coding module accepts.
 *
 * <p>Stored as the string name (not the ordinal) in {@code coding_submissions.language}.
 * The project supports exactly these two languages (README "Coding playground for Java
 * and C++"); there is no per-problem allowlist column because every problem accepts
 * both.
 */
public enum ProgrammingLanguage {
    JAVA,
    CPP
}
