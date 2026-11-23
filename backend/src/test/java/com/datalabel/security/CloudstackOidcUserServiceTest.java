package com.datalabel.security;

import com.datalabel.domain.AppUser;
import com.datalabel.domain.Role;
import com.datalabel.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CloudstackOidcUserServiceTest {

    private AppUserRepository users;
    private CloudstackOidcUserService service;

    @BeforeEach void setup() {
        users = mock(AppUserRepository.class);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            if (u.getId() == null) u.setId(7L);
            return u;
        });
        when(users.findByOidcSubject(any())).thenReturn(Optional.empty());
        when(users.findByUsername(any())).thenReturn(Optional.empty());
        service = new CloudstackOidcUserService(users);
    }

    static OidcUser token(String sub, String username, Object roles) {
        var b = OidcIdToken.withTokenValue("t").issuer("https://auth.hitorro.com").subject(sub)
                .audience(List.of("labelhive")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", username).claim("name", "Alice A");
        if (roles != null) b.claim("roles", roles);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), b.build(), "preferred_username");
    }

    private static List<String> names(OidcUser u) {
        return u.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
    }

    @Test void labelerSignInCreatesAWorkerRecordKeyedBySub() {
        OidcUser p = service.toPrincipal(token("pau_1", "alice", List.of("labeler")));

        assertThat(p.getName()).isEqualTo("alice");
        assertThat(names(p)).containsExactly("OIDC_USER", "ROLE_LABELER", "ROLE_WORKER");
        verify(users).save(argThat(u -> u.getUsername().equals("alice") && u.getOidcSubject().equals("pau_1")
                && u.getRole() == Role.WORKER && "!oidc".equals(u.getPasswordHash())));
    }

    @Test void adminSignInGetsAdmin() {
        OidcUser p = service.toPrincipal(token("pau_2", "chris", List.of("admin")));
        assertThat(names(p)).contains("ROLE_ADMIN");
        verify(users).save(argThat(u -> u.getRole() == Role.ADMIN));
    }

    @Test void noKnownRoleIsRefused() {
        assertThatThrownBy(() -> service.toPrincipal(token("pau_3", "eve", List.of("requester"))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(CloudstackOidcUserService.NO_ROLE));
        assertThatThrownBy(() -> service.toPrincipal(token("pau_3", "eve", null)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(users, never()).save(any());
    }

    @Test void existingUserFollowsUsernameAndRoleChanges() {
        AppUser existing = AppUser.builder().id(5L).username("alice").oidcSubject("pau_1")
                .passwordHash("!oidc").role(Role.ADMIN).build();
        when(users.findByOidcSubject("pau_1")).thenReturn(Optional.of(existing));

        service.toPrincipal(token("pau_1", "alice2", List.of("labeler")));

        assertThat(existing.getUsername()).isEqualTo("alice2");
        assertThat(existing.getRole()).isEqualTo(Role.WORKER);
    }

    @Test void anUnlinkedRecordWithTheSameUsernameIsAdopted() {
        AppUser seeded = AppUser.builder().id(3L).username("bob").passwordHash("x").role(Role.WORKER).build();
        when(users.findByUsername("bob")).thenReturn(Optional.of(seeded));

        service.toPrincipal(token("pau_9", "bob", List.of("labeler")));

        assertThat(seeded.getOidcSubject()).isEqualTo("pau_9");
    }

    @Test void aUsernameLinkedToAnotherSubjectIsRefused() {
        AppUser other = AppUser.builder().id(3L).username("bob").oidcSubject("pau_other")
                .passwordHash("!oidc").role(Role.WORKER).build();
        when(users.findByUsername("bob")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.toPrincipal(token("pau_9", "bob", List.of("labeler"))))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test void claimsAreKept() {
        OidcUser p = service.toPrincipal(token("pau_1", "alice", List.of("labeler")));
        assertThat(p.getSubject()).isEqualTo("pau_1");
        assertThat(p.getClaims()).containsEntry("roles", List.of("labeler"));
        assertThat(Map.copyOf(p.getClaims())).containsKey("name");
    }
}
