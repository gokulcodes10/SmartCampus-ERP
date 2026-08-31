package smartcampus.entity;

/**
 * Whether an {@link AIStudyPlan} is a general study plan or a revision schedule — same
 * table structure, different generation prompt and title.
 *
 * <p>Stored as the string name (not the ordinal) in {@code ai_study_plans.plan_type} —
 * see {@code V6__ai.sql}, which also enforces these exact two values with a {@code
 * CHECK} constraint.
 */
public enum AIStudyPlanType {
    STUDY_PLAN,
    REVISION_SCHEDULE
}
