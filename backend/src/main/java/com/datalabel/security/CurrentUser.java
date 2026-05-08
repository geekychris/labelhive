package com.datalabel.security;

import com.datalabel.domain.AppUser;
import com.datalabel.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class CurrentUser {

    private final AppUserRepository users;
    private final boolean debugAuth;

    public CurrentUser(AppUserRepository users,
                       @Value("${datalabel.debug-auth:false}") boolean debugAuth) {
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
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        return users.findByUsername(auth.getName()).orElse(null);
    }

    public boolean isDebugMode() { return debugAuth; }
}
