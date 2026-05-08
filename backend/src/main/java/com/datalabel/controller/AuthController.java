package com.datalabel.controller;

import com.datalabel.domain.AppUser;
import com.datalabel.repository.AppUserRepository;
import com.datalabel.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUser currentUser;
    private final AppUserRepository users;

    public AuthController(CurrentUser currentUser, AppUserRepository users) {
        this.currentUser = currentUser; this.users = users;
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        AppUser u = currentUser.resolve(req);
        if (u == null) return Map.of("authenticated", false, "debug", currentUser.isDebugMode());
        return Map.of(
                "authenticated", true,
                "debug", currentUser.isDebugMode(),
                "id", u.getId(),
                "username", u.getUsername(),
                "role", u.getRole()
        );
    }

    /** Debug-only: list seeded users so the UI can offer a switcher. */
    @GetMapping("/debug-users")
    public List<Map<String, Object>> debugUsers() {
        if (!currentUser.isDebugMode()) return List.of();
        return users.findAll().stream().map(u -> Map.<String, Object>of(
                "id", u.getId(), "username", u.getUsername(), "role", u.getRole()
        )).toList();
    }
}
