package com.serdar.gateway.security;

/**
 * Principal object attached to the Spring Security context after the JWT filter
 * validates the request. Controllers pull the user id from here.
 */
public record AuthenticatedUser(long id, String email, String nickname, String role) {
    public String roleAuthority() { return "ROLE_" + role; }
}
