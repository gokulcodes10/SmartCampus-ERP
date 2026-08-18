/**
 * JPA entities and persistence-level enums — the domain model.
 *
 * <p>Each entity maps to a table created by a Flyway migration under
 * {@code classpath:db/migration}. Because {@code spring.jpa.hibernate.ddl-auto=validate},
 * an entity and its migration must agree exactly; Hibernate never creates or alters
 * schema. Entities hold state and invariants, not application workflows.
 */
package smartcampus.entity;
