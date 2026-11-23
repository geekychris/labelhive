package com.datalabel.controller;

import com.datalabel.domain.AppUser;
import com.datalabel.security.CurrentUser;
import com.datalabel.security.RoleMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/me}: who is signed in, for the UI and for deployment checks:
 * {@code {"username":"alice","roles":["labeler"],"name":...,"email":...,"sub":...,"id":12,"role":"WORKER"}}.
 * {@code roles} are the provider's role names for this app (admin, labeler).
 * 401 when nobody is signed in.
 */
@RestController
public class MeController {

    private final CurrentUser currentUser;

    public MeController(CurrentUser currentUser) { this.currentUser = currentUser; }

    @GetMapping("/api/me")
    public Map<String, Object> me(HttpServletRequest req) {
        AppUser u = currentUser.resolve(req);
        if (u == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return describe(u, currentUser.isDebugMode());
    }

    /** The signed-in user's provider role names (the ID token's roles claim; the app role in debug mode). */
    static List<String> roles(AppUser u) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            return RoleMapping.knownRoles(oidc.getClaims().get("roles"));
        }
        return RoleMapping.fromAppRole(u.getRole());
    }

    static Map<String, Object> describe(AppUser u, boolean debug) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("roles", roles(u));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            m.put("sub", oidc.getSubject());
            if (oidc.getFullName() != null) m.put("name", oidc.getFullName());
            if (oidc.getEmail() != null) m.put("email", oidc.getEmail());
        }
        m.put("id", u.getId());
        m.put("role", u.getRole());
        m.put("debug", debug);
        return m;
    }
}
