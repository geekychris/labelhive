package com.datalabel.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The debug (X-User-Id) identity refuses to start next to a deployment. */
class DebugSecurityConfigTest {

    @Test void plainLocalDebugStarts() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("debug");
        assertThatCode(() -> DebugSecurityConfig.check(env)).doesNotThrowAnyException();
    }

    @Test void refusesWhenAnIssuerIsConfigured() {
        MockEnvironment env = new MockEnvironment().withProperty("datalabel.oidc.issuer", "https://auth.hitorro.com");
        env.setActiveProfiles("debug");
        assertThatThrownBy(() -> DebugSecurityConfig.check(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesWhenOidcIssuerEnvIsSet() {
        MockEnvironment env = new MockEnvironment().withProperty("OIDC_ISSUER", "https://auth.geekychris.com");
        env.setActiveProfiles("debug");
        assertThatThrownBy(() -> DebugSecurityConfig.check(env)).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesWithTheDeployedOrProdProfile() {
        for (String p : new String[]{"deployed", "prod"}) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("debug", p);
            assertThatThrownBy(() -> DebugSecurityConfig.check(env)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void oidcModeRequiresIssuerAndSecret() {
        assertThatThrownBy(() -> OidcSecurityConfig.registration("", "", "labelhive", "s"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OIDC_ISSUER");
        assertThatThrownBy(() -> OidcSecurityConfig.registration("https://auth.hitorro.com", "", "labelhive", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OIDC_CLIENT_SECRET");
    }
}
