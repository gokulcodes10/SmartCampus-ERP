package smartcampus.dto;

/** Successful-login payload: a signed JWT plus the safe summary of the account it was issued for. */
public record AuthResponse(String token, UserResponse user) {
}
