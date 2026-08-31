package smartcampus.entity;

/**
 * The speaker of one turn inside an {@link AIConversation}.
 *
 * <p>Stored as the string name (not the ordinal) in {@code ai_messages.role} — see
 * {@code V6__ai.sql}, which also enforces these exact three values with a {@code CHECK}
 * constraint. Only a {@link #SYSTEM} turn may be marked {@code grounded}, and only an
 * {@link #ASSISTANT} turn is required to carry a non-null {@code model}
 * (the anti-fabrication constraint, §69).
 */
public enum AIMessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}
