package com.datalabel.security;

import com.datalabel.controller.AuthController;
import com.datalabel.controller.MeController;
import com.datalabel.domain.AppUser;
import com.datalabel.domain.Role;
import com.datalabel.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The deployed security model: OIDC sign-in, 401 for API, redirect for browsers, roles, no debug header. */
@WebMvcTest(controllers = {MeController.class, AuthController.class})
@Import({OidcSecurityConfig.class, CurrentUser.class})
@TestPropertySource(properties = {
        "datalabel.oidc.issuer=https://auth.hitorro.com",
        "datalabel.oidc.backchannel-url=https://auth.hitorro.com:8443",
        "datalabel.oidc.client-id=labelhive",
        "datalabel.oidc.client-secret=test-only-not-a-secret",
})
class OidcSecurityWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;

    @BeforeEach void setup() {
        when(users.findByOidcSubject("pau_alice")).thenReturn(Optional.of(
                AppUser.builder().id(12L).username("alice").oidcSubject("pau_alice").role(Role.WORKER).build()));
        when(users.findByOidcSubject("pau_chris")).thenReturn(Optional.of(
                AppUser.builder().id(1L).username("chris").oidcSubject("pau_chris").role(Role.ADMIN).build()));
    }

    private static RequestPostProcessor as(String sub, String username, String role) {
        OidcIdToken token = OidcIdToken.withTokenValue("id-token").issuer("https://auth.hitorro.com").subject(sub)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", username).claim("roles", List.of(role)).build();
        var authorities = RoleMapping.authorities(RoleMapping.knownRoles(List.of(role)));
        return oidcLogin().clientRegistration(OidcSecurityConfig.registration("https://auth.hitorro.com",
                        "https://auth.hitorro.com:8443", "labelhive", "test-only-not-a-secret"))
                .oidcUser(new DefaultOidcUser(authorities, token, "preferred_username"));
    }

    private static RequestPostProcessor lanPort8443() {
        return r -> { r.setScheme("https"); r.setSecure(true); r.setServerName("labelhive.hitorro.com"); r.setServerPort(8443); return r; };
    }

    @Test void unauthenticatedApiCallIs401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Accept", "text/html")).andExpect(status().isUnauthorized());
    }

    @Test void unauthenticatedBrowserIsSentToTheProvider() throws Exception {
        mvc.perform(get("/work/3").header("Accept", "text/html"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/cloudstack"));
    }

    @Test void loginStartRedirectsToTheIssuerWithPkce() throws Exception {
        mvc.perform(get("/oauth2/authorization/cloudstack"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://auth.hitorro.com/oidc/authorize?"),
                        containsString("client_id=labelhive"),
                        containsString("code_challenge_method=S256"),
                        containsString("scope=openid%20profile%20email"))));
    }

    @Test void loginStartFollowsTheLanPort() throws Exception {
        mvc.perform(get("/oauth2/authorization/cloudstack").with(lanPort8443()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://auth.hitorro.com:8443/oidc/authorize?"),
                        containsString("redirect_uri=https://labelhive.hitorro.com:8443/login/oauth2/code/cloudstack"))));
    }

    @Test void debugHeaderIsIgnored() throws Exception {
        mvc.perform(get("/api/me").header("X-User-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("X-User-Id", "demo")).andExpect(status().isUnauthorized());
        // A signed-in labeler stays herself whatever the header says.
        mvc.perform(get("/api/me").header("X-User-Id", "chris").with(as("pau_alice", "alice", "labeler")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test void meReportsUsernameAndRoles() throws Exception {
        mvc.perform(get("/api/me").with(as("pau_alice", "alice", "labeler")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("labeler"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.role").value("WORKER"))
                .andExpect(jsonPath("$.sub").value("pau_alice"))
                .andExpect(jsonPath("$.debug").value(false));
        mvc.perform(get("/api/me").with(as("pau_chris", "chris", "admin")))
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/auth/me").with(as("pau_chris", "chris", "admin")))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("chris"));
    }

    @Test void labelersCannotReachAdminApisOrMcp() throws Exception {
        var alice = as("pau_alice", "alice", "labeler");
        mvc.perform(get("/api/admin/concerns").with(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs/1/units").with(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/jobs/1/reports/monitor").with(alice)).andExpect(status().isForbidden());
        mvc.perform(get("/sse").with(alice)).andExpect(status().isForbidden());
        mvc.perform(post("/api/jobs").with(alice).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void labelerAndAdminPassTheWorkerRoutes() throws Exception {
        // No controller is mounted for these in this slice: 404 means security let it through.
        for (var who : List.of(as("pau_alice", "alice", "labeler"), as("pau_chris", "chris", "admin"))) {
            mvc.perform(get("/api/jobs").with(who)).andExpect(status().isNotFound());
            mvc.perform(get("/api/jobs/1/tasks/next").with(who)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/admin/concerns").with(as("pau_chris", "chris", "admin"))).andExpect(status().isNotFound());
    }

    @Test void mcpNeedsAuthentication() throws Exception {
        mvc.perform(get("/sse")).andExpect(status().isUnauthorized());
        mvc.perform(post("/mcp/message")).andExpect(status().isUnauthorized());
    }

    @Test void healthIsOpen() throws Exception {
        // No actuator in this slice: anything but 401/302 means it is not behind sign-in.
        int s = mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(s).isNotIn(401, 302, 403);
    }

    @Test void stateChangingApiCallsNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/api/admin/concerns/1/dismiss").with(as("pau_chris", "chris", "admin")))
                .andExpect(status().isForbidden());
    }

    @Test void logoutGoesToTheProvidersEndSession() throws Exception {
        mvc.perform(post("/logout").with(as("pau_chris", "chris", "admin")).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://auth.hitorro.com/oidc/end_session?"),
                        containsString("id_token_hint=id-token"),
                        containsString("post_logout_redirect_uri=http://localhost/"))));
        mvc.perform(post("/logout").with(as("pau_chris", "chris", "admin")).with(csrf()).with(lanPort8443()))
                .andExpect(header().string("Location", allOf(
                        startsWith("https://auth.hitorro.com:8443/oidc/end_session?"),
                        containsString("post_logout_redirect_uri=https://labelhive.hitorro.com:8443/"))));
    }
}
