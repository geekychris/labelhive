package com.datalabel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * RP-initiated logout ({@code end_session}, post-logout redirect {@code {baseUrl}/})
 * on the same explicit https port as the request, like the authorization URI.
 */
public class PortFollowingLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

    public PortFollowingLogoutSuccessHandler(ClientRegistrationRepository repo) {
        super(repo);
        setPostLogoutRedirectUri("{baseUrl}/");
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        String target = super.determineTargetUrl(request, response, authentication);
        // Only the provider's end-session URL is rewritten; a plain local "/" stays.
        if (target != null && target.startsWith("http")) {
            return PortFollowingAuthorizationRequestResolver.withPort(target,
                    PortFollowingAuthorizationRequestResolver.explicitPort(request));
        }
        return target;
    }
}
