/**
 * Authentication and authorization infrastructure.
 *
 * <p>JWT creation and verification, the authentication filter, the
 * {@code UserDetailsService} implementation, password encoding, the authenticated
 * principal, and the ownership checks that stop one user reading another user's data.
 * Endpoint rules are expressed on the {@code SecurityFilterChain} in
 * {@code smartcampus.config} and by method-level annotations on services.
 */
package smartcampus.security;
