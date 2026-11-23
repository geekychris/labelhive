package com.datalabel.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local development only (profile {@code debug}): no sign-in; {@link CurrentUser}
 * resolves identity from the {@code X-User-Id} header or falls back to the seeded
 * {@code demo} admin.
 *
 * <p>It refuses to start next to anything that looks like a deployment: when
 * {@code OIDC_ISSUER} is set or the {@code deployed}/{@code prod} profile is active.
 */
@Configuration
@Profile("debug")
public class DebugSecurityConfig {

    public DebugSecurityConfig(Environment env) {
        check(env);
    }

    /** Throws when the debug identity would be enabled in a deployed setting. */
    static void check(Environment env) {
        String issuer = env.getProperty("datalabel.oidc.issuer", "");
        if (issuer == null || issuer.isBlank()) issuer = env.getProperty("OIDC_ISSUER", "");
        if (issuer != null && !issuer.isBlank()) {
            throw new IllegalStateException("The debug profile (X-User-Id identity, no sign-in) cannot run with "
                    + "OIDC_ISSUER set; remove the debug profile.");
        }
        if (env.acceptsProfiles(Profiles.of("deployed", "prod", "production"))) {
            throw new IllegalStateException("The debug profile (X-User-Id identity, no sign-in) cannot be combined "
                    + "with the deployed/prod profile.");
        }
    }

    @Bean
    public SecurityFilterChain debugFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .build();
    }
}
