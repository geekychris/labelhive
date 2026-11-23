package com.datalabel.security;

import com.datalabel.domain.AppUser;
import com.datalabel.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The app-side user behind a request. With OIDC (every profile but {@code debug})
 * it is the user record linked to the session's {@code sub}; the {@code X-User-Id}
 * header is ignored. Only the {@code debug} profile (local development, which
 * {@link DebugSecurityConfig} refuses to combine with a deployment) reads the header.
 */
@Component
public class CurrentUser {

    private final AppUserRepository users;
    private final boolean debugAuth;

    @Autowired
    public CurrentUser(AppUserRepository users, Environment env) {
        this(users, env.acceptsProfiles(Profiles.of("debug")));
    }

    CurrentUser(AppUserRepository users, boolean debugAuth) {
        this.users = users;
        this.debugAuth = debugAuth;
    }

    public AppUser require(HttpServletRequest req) {
        AppUser u = resolve(req);
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return u;
    }

    public AppUser resolve(HttpServletRequest req) {
        if (debugAuth) {
            String header = req == null ? null : req.getHeader("X-User-Id");
            if (header == null || header.isBlank()) {
                // Default to the seeded "demo" admin in debug mode so the UI works
                // out-of-the-box without setting headers.
                return users.findByUsername("demo").orElse(null);
            }
            try {
                return users.findById(Long.parseLong(header)).orElse(null);
            } catch (NumberFormatException nfe) {
                return users.findByUsername(header).orElse(null);
            }
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return users.findByOidcSubject(oidc.getSubject()).orElse(null);
        }
        return null;
    }

    public boolean isDebugMode() { return debugAuth; }
}
