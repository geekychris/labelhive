package com.datalabel.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

import static org.assertj.core.api.Assertions.assertThat;

class PortFollowingAuthorizationRequestResolverTest {

    private final PortFollowingAuthorizationRequestResolver resolver = new PortFollowingAuthorizationRequestResolver(
            new InMemoryClientRegistrationRepository(OidcSecurityConfig.registration(
                    "https://auth.hitorro.com", "https://auth.hitorro.com:8443", "labelhive", "s")));

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/oauth2/authorization/cloudstack");
        r.setServletPath("/oauth2/authorization/cloudstack");
        r.setScheme(scheme);
        r.setServerName(host);
        r.setServerPort(port);
        r.setSecure("https".equals(scheme));
        return r;
    }

    @Test void explicitNon443HttpsPortIsFollowed() {
        OAuth2AuthorizationRequest req = resolver.resolve(request("https", "labelhive.hitorro.com", 8443));

        assertThat(req.getAuthorizationUri()).isEqualTo("https://auth.hitorro.com:8443/oidc/authorize");
        assertThat(req.getAuthorizationRequestUri()).startsWith("https://auth.hitorro.com:8443/oidc/authorize?");
        assertThat(req.getRedirectUri()).isEqualTo("https://labelhive.hitorro.com:8443/login/oauth2/code/cloudstack");
    }

    @Test void standardHttpsPortLeavesTheIssuerAlone() {
        OAuth2AuthorizationRequest req = resolver.resolve(request("https", "labelhive.geekychris.com", 443));

        assertThat(req.getAuthorizationUri()).isEqualTo("https://auth.hitorro.com/oidc/authorize");
        assertThat(req.getAuthorizationRequestUri()).startsWith("https://auth.hitorro.com/oidc/authorize?");
        assertThat(req.getRedirectUri()).isEqualTo("https://labelhive.geekychris.com/login/oauth2/code/cloudstack");
    }

    @Test void plainHttpIsNotFollowed() {
        OAuth2AuthorizationRequest req = resolver.resolve(request("http", "mp2.local", 31850));
        assertThat(req.getAuthorizationUri()).isEqualTo("https://auth.hitorro.com/oidc/authorize");
    }

    @Test void pkceS256AndOidcScopesAreAlwaysSent() {
        for (int port : new int[]{443, 8443}) {
            OAuth2AuthorizationRequest req = resolver.resolve(request("https", "labelhive.hitorro.com", port));
            assertThat(req.getAdditionalParameters()).containsEntry(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256")
                    .containsKey(PkceParameterNames.CODE_CHALLENGE);
            assertThat(req.getAttributes()).containsKey(PkceParameterNames.CODE_VERIFIER);
            assertThat(req.getAuthorizationRequestUri()).contains("code_challenge_method=S256").contains("nonce=");
            assertThat(req.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
            assertThat(req.getClientId()).isEqualTo("labelhive");
        }
    }

    @Test void otherPathsResolveToNothing() {
        MockHttpServletRequest r = request("https", "labelhive.hitorro.com", 8443);
        r.setRequestURI("/work");
        r.setServletPath("/work");
        assertThat(resolver.resolve(r)).isNull();
    }

    @Test void withPortKeepsTheEncodedQuery() {
        String u = "https://auth.hitorro.com/oidc/end_session?post_logout_redirect_uri=https%3A%2F%2Flabelhive.hitorro.com%3A8443%2F";
        assertThat(PortFollowingAuthorizationRequestResolver.withPort(u, 8443))
                .isEqualTo("https://auth.hitorro.com:8443/oidc/end_session?post_logout_redirect_uri=https%3A%2F%2Flabelhive.hitorro.com%3A8443%2F");
        assertThat(PortFollowingAuthorizationRequestResolver.withPort(u, -1)).isEqualTo(u);
    }
}
