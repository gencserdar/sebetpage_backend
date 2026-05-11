package com.serdar.gateway.security;

/**
 * Principal object attached to the Spring Security context after the JWT filter
 * validates the request. Controllers pull the user id and session id from here.
 *
 * sessionId is the sessions.id that was embedded as the "sid" claim in the
 * access token at login time. It lets single-device logout target exactly one
 * row without an additional cookie or DB lookup.
 */
public record AuthenticatedUser(long id, String email, String nickname, String role, long sessionId) {
    public String roleAuthority() { return "ROLE_" + role; }
}
