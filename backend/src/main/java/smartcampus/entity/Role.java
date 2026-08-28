package smartcampus.entity;

/**
 * The three account types the platform supports.
 *
 * <p>Stored as the string name (not the ordinal) in {@code users.role} - see
 * {@code V2__auth.sql}, which also enforces these exact three values with a
 * {@code CHECK} constraint. Self-registration (§74 Phase 2, clarification G1)
 * is restricted to {@link #STUDENT}; {@link #FACULTY} and {@link #ADMIN}
 * accounts are provisioned by an existing admin.
 */
public enum Role {
    STUDENT,
    FACULTY,
    ADMIN
}
