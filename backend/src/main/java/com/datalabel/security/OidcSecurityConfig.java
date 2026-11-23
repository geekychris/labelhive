package com.datalabel.security;

import com.datalabel.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sign-in with OpenID Connect against the site's cloud-stack login service
 * (registrationId {@code cloudstack}; authorization code + PKCE). Every profile
 * except {@code debug} uses it, and the app refuses to start without
 * {@code OIDC_ISSUER} and {@code OIDC_CLIENT_SECRET}.
 *
 * <p>The ClientRegistration is built here from the environment rather than by
 * discovery, so the app starts while the provider is briefly unreachable; the
 * issuer is still set so the ID token's {@code iss} is validated.
 *
 * <p>Access: {@code /actuator/health} is open; labelers (and admins) may list jobs
 * and work tasks; everything else under {@code /api}, and the MCP endpoints, is
 * admin-only. Unauthenticated API/MCP calls get 401, browser navigation is sent
 * to {@code /oauth2/authorization/cloudstack}.
 */
@Configuration
@Profile("!debug")
public class OidcSecurityConfig {

    public static final String REGISTRATION_ID = "cloudstack";
    public static final String LOGIN_PATH = "/oauth2/authorization/" + REGISTRATION_ID;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${datalabel.oidc.issuer:}") String issuer,
            @Value("${datalabel.oidc.backchannel-url:}") String backchannel,
            @Value("${datalabel.oidc.client-id:labelhive}") String clientId,
            @Value("${datalabel.oidc.client-secret:}") String clientSecret) {
        return new InMemoryClientRegistrationRepository(registration(issuer, backchannel, clientId, clientSecret));
    }

    static ClientRegistration registration(String issuer, String backchannel, String clientId, String clientSecret) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("OIDC_ISSUER is required (e.g. https://auth.hitorro.com). "
                    + "For local development without the login service run with the `debug` profile.");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("OIDC_CLIENT_SECRET is required when OIDC_ISSUER is set.");
        }
        String iss = trimSlash(issuer);
        String back = (backchannel == null || backchannel.isBlank()) ? iss : trimSlash(backchannel);
        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientName("cloud-stack")
                .clientId(clientId == null || clientId.isBlank() ? "labelhive" : clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(iss)
                .authorizationUri(iss + "/oidc/authorize")
                .tokenUri(back + "/oidc/token")
                .jwkSetUri(back + "/oidc/keys")
                // No userinfo call: every claim (roles included) is in the ID token.
                .userNameAttributeName("preferred_username")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", iss + "/oidc/end_session"))
                .build();
    }

    private static String trimSlash(String s) {
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    @Bean
    public CloudstackOidcUserService cloudstackOidcUserService(AppUserRepository users) {
        return new CloudstackOidcUserService(users);
    }

    @Bean
    public SecurityFilterChain oidcFilterChain(HttpSecurity http, ClientRegistrationRepository repo,
                                               CloudstackOidcUserService userService) throws Exception {
        RequestMatcher mcp = new OrRequestMatcher(
                new AntPathRequestMatcher("/sse"), new AntPathRequestMatcher("/sse/**"),
                new AntPathRequestMatcher("/mcp"), new AntPathRequestMatcher("/mcp/**"));
        RequestMatcher api = new OrRequestMatcher(new AntPathRequestMatcher("/api/**"), mcp);

        // The SPA reads XSRF-TOKEN and sends it as X-XSRF-TOKEN (or _csrf on the
        // sign-out form). The token is loaded on every request so the cookie exists.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        return http
                .csrf(c -> c
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler)
                        // MCP clients can't fetch a CSRF token; the session cookie is
                        // SameSite=Lax and the endpoints are admin-only.
                        .ignoringRequestMatchers(mcp))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/api/me", "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/jobs").hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers("/api/jobs/*/tasks/**").hasAnyRole("ADMIN", "WORKER")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .requestMatchers(mcp).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api)
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint(LOGIN_PATH),
                                AntPathRequestMatcher.antMatcher("/**")))
                .oauth2Login(o -> o
                        .authorizationEndpoint(ae -> ae.authorizationRequestResolver(
                                new PortFollowingAuthorizationRequestResolver(repo)))
                        .userInfoEndpoint(u -> u.oidcUserService(userService))
                        .defaultSuccessUrl("/", false)
                        .failureHandler((req, res, ex) -> {
                            boolean noRole = ex instanceof OAuth2AuthenticationException oae
                                    && CloudstackOidcUserService.NO_ROLE.equals(oae.getError().getErrorCode());
                            res.setStatus(noRole ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("text/html;charset=utf-8");
                            String msg = noRole
                                    ? "Your account has no LabelHive role (admin or labeler). Ask the site admin for access."
                                    : "Sign-in failed. <a href=\"" + LOGIN_PATH + "\">Try again</a>.";
                            res.getOutputStream().write(("<!doctype html><title>LabelHive</title><h1>LabelHive</h1><p>"
                                    + msg + "</p>").getBytes(StandardCharsets.UTF_8));
                        }))
                .logout(l -> l
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(new PortFollowingLogoutSuccessHandler(repo)))
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .build();
    }
}
