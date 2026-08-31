package smartcampus.entity;

/**
 * The lifecycle state of an {@link AIStudyPlan}.
 *
 * <p>Stored as the string name (not the ordinal) in {@code ai_study_plans.status} — see
 * {@code V6__ai.sql}, which also enforces these exact three values with a {@code CHECK}
 * constraint.
 */
public enum AIStudyPlanStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED
}
