package com.cocoding.playstate.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Locale;

public final class LibraryUserIds {

    private LibraryUserIds() {}

    public static String require(Authentication authentication) {
        String id = optional(authentication);
        if (id == null) {
            throw new IllegalStateException("Expected authenticated user");
        }
        return id;
    }

    
    public static String optional(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
