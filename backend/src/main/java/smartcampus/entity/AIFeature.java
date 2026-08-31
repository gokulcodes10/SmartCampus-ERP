package smartcampus.entity;

/**
 * Which AI entry point a conversation or request-log row belongs to.
 *
 * <p>Stored as the string name (not the ordinal) in {@code ai_conversations.feature}
 * and {@code ai_request_logs.feature} — see {@code V6__ai.sql}, which also enforces
 * these exact six values with a {@code CHECK} constraint on both tables. Recorded so a
 * conversation-history screen can filter ("my study plans" vs "my chats") without
 * re-parsing message text.
 */
public enum AIFeature {
    CHAT,
    STUDY_PLAN,
    TOPIC_EXPLANATION,
    PRACTICE_QUESTIONS,
    MCQ,
    REVISION_SCHEDULE
}
