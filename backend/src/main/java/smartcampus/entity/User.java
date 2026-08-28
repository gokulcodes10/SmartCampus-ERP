package smartcampus.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A registered account: student, faculty, or admin.
 *
 * <p>Maps exactly to the {@code users} table created by {@code V2__auth.sql} -
 * {@code spring.jpa.hibernate.ddl-auto=validate} refuses to boot on any drift between
 * this mapping and that migration (PROJECT_PLAN.md clarification G8).
 *
 * <p>Implements {@link UserDetails} directly so it plugs straight into
 * {@code JwtAuthenticationFilter}, {@code UserDetailsServiceImpl}, and any
 * {@code AuthenticationProvider} the login flow wires up, with no separate adapter
 * type. {@link #getPassword()} exists only for Spring Security's own comparisons; it
 * is annotated {@link JsonIgnore} so an accidental Jackson serialization of the entity
 * itself can never leak the hash. That is defense in depth only - the real safeguard
 * is that response DTOs (owned by the controller layer) never include this field, per
 * the project rule that no password, hash, or reset token ever appears in an API
 * response.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "password")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @JsonIgnore
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---------------------------------------------------------------------
    // UserDetails
    // ---------------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Spring Security's "username" is this account's email - there is no separate login name. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
