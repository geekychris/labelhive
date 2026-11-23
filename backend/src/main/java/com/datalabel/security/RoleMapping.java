package com.datalabel.security;

import com.datalabel.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.*;

/**
 * The provider's {@code roles} claim (this app's role names, e.g. {@code ["labeler"]})
 * to Spring authorities and the app's own {@link Role}.
 *
 * <ul>
 *   <li>{@code admin}   → {@code ROLE_ADMIN} (+ {@code ROLE_WORKER}: admins may label too); app role ADMIN</li>
 *   <li>{@code labeler} → {@code ROLE_LABELER} + {@code ROLE_WORKER}; app role WORKER</li>
 * </ul>
 * Unknown role names are ignored; a user with no known role is refused at sign-in.
 */
public final class RoleMapping {

    public static final String ADMIN = "admin";
    public static final String LABELER = "labeler";
    public static final List<String> KNOWN = List.of(ADMIN, LABELER);

    private RoleMapping() {}

    /** The known role names in the claim value (a JSON array, or a single string), lower-cased, ordered. */
    public static List<String> knownRoles(Object claim) {
        Collection<?> raw;
        if (claim instanceof Collection<?> c) raw = c;
        else if (claim instanceof String s) raw = List.of(s.split("[,\\s]+"));
        else raw = List.of();
        Set<String> out = new TreeSet<>();
        for (Object o : raw) {
            if (o == null) continue;
            String r = o.toString().trim().toLowerCase(Locale.ROOT);
            if (KNOWN.contains(r)) out.add(r);
        }
        return List.copyOf(out);
    }

    public static Set<GrantedAuthority> authorities(List<String> knownRoles) {
        Set<GrantedAuthority> out = new LinkedHashSet<>();
        for (String r : knownRoles) {
            out.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase(Locale.ROOT)));
            out.add(new SimpleGrantedAuthority("ROLE_WORKER"));
        }
        return out;
    }

    /** The app-side role stored on the user record; null when no known role. */
    public static Role appRole(List<String> knownRoles) {
        if (knownRoles.contains(ADMIN)) return Role.ADMIN;
        if (knownRoles.contains(LABELER)) return Role.WORKER;
        return null;
    }

    /** The provider role names an app-side role corresponds to (for /api/me in debug mode). */
    public static List<String> fromAppRole(Role role) {
        if (role == null) return List.of();
        return role == Role.ADMIN ? List.of(ADMIN) : List.of(LABELER);
    }
}
