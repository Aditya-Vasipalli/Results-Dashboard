package edu.vit.results.config;

import edu.vit.results.domain.AppUser;
import edu.vit.results.domain.Role;

/** The signed-in user, re-read from the database on every request. */
public record AuthUser(Long id, String name, String email, Role role, String prn) {

    public static AuthUser of(AppUser u) {
        return new AuthUser(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getPrn());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
