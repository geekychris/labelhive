package com.datalabel.security;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Role;
import com.datalabel.repository.AppUserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the verified ID token into the session principal: maps the {@code roles}
 * claim (refusing a sign-in without a known role), and creates or updates the
 * app-side {@link AppUser} keyed by {@code sub} (username = {@code preferred_username}).
 */
public class CloudstackOidcUserService extends OidcUserService {

    public static final String NO_ROLE = "labelhive_no_role";
    static final String OIDC_PASSWORD_PLACEHOLDER = "!oidc";

    private final AppUserRepository users;

    public CloudstackOidcUserService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest req) throws OAuth2AuthenticationException {
        OidcUser base = super.loadUser(req);
        return toPrincipal(base);
    }

    OidcUser toPrincipal(OidcUser base) {
        List<String> roles = RoleMapping.knownRoles(base.getClaims().get("roles"));
        if (roles.isEmpty()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(NO_ROLE,
                    "No LabelHive role (admin or labeler) for this account", null));
        }
        String username = base.getPreferredUsername();
        if (username == null || username.isBlank()) username = base.getSubject();
        provision(base.getSubject(), username, RoleMapping.appRole(roles));

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(base.getAuthorities());
        authorities.addAll(RoleMapping.authorities(roles));
        String nameAttr = base.getPreferredUsername() != null ? "preferred_username" : "sub";
        return new DefaultOidcUser(authorities, base.getIdToken(), base.getUserInfo(), nameAttr);
    }

    /** Find the user by `sub`, else adopt an unlinked record with the same username, else create one. */
    AppUser provision(String subject, String username, Role role) {
        AppUser u = users.findByOidcSubject(subject).orElse(null);
        if (u == null) {
            u = users.findByUsername(username).orElse(null);
            if (u != null && u.getOidcSubject() != null && !u.getOidcSubject().equals(subject)) {
                throw new OAuth2AuthenticationException(new OAuth2Error("labelhive_username_conflict",
                        "Username " + username + " belongs to another account", null));
            }
            if (u == null) {
                u = AppUser.builder().username(username).passwordHash(OIDC_PASSWORD_PLACEHOLDER).build();
            }
            u.setOidcSubject(subject);
        } else if (!username.equals(u.getUsername())) {
            AppUser other = users.findByUsername(username).orElse(null);
            if (other != null && !other.getId().equals(u.getId())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("labelhive_username_conflict",
                        "Username " + username + " belongs to another account", null));
            }
            u.setUsername(username);
        }
        u.setRole(role);   // the provider is authoritative for roles
        return users.save(u);
    }
}
