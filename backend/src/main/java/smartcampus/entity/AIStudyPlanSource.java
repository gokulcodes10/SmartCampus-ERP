package smartcampus.entity;

/**
 * Whether an {@link AIStudyPlan} was produced by the model or created by the student by
 * hand.
 *
 * <p>Stored as the string name (not the ordinal) in {@code ai_study_plans.source} — see
 * {@code V6__ai.sql}, which also enforces these exact two values with a {@code CHECK}
 * constraint, together with {@code chk_ai_study_plans_model_matches_source}: an {@link
 * #AI_GENERATED} plan must carry a non-null {@code model} and a {@link #STUDENT_CREATED}
 * plan must carry a null one. No Phase 6 endpoint creates {@link #STUDENT_CREATED} rows
 * — the value exists for Phase 10 reuse.
 */
public enum AIStudyPlanSource {
    AI_GENERATED,
    STUDENT_CREATED
}
