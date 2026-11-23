package com.datalabel.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * PKCE (S256) on every authorization request, and the request's port on the
 * browser-facing authorization URI: when the app is reached on an explicit
 * non-443 https port (Belmont's LAN path {@code https://labelhive.hitorro.com:8443}),
 * the provider is on the same proxy and port ({@code https://auth.hitorro.com:8443}).
 */
public class PortFollowingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public PortFollowingAuthorizationRequestResolver(ClientRegistrationRepository repo) {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return followPort(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return followPort(request, delegate.resolve(request, clientRegistrationId));
    }

    /** The explicit non-443 https port the request came in on, or -1. */
    static int explicitPort(HttpServletRequest request) {
        if (!"https".equalsIgnoreCase(request.getScheme())) return -1;
        int port = request.getServerPort();
        return (port > 0 && port != 443) ? port : -1;
    }

    /** {@code uri} with {@code port} (or unchanged when port is -1). Expects an encoded URI. */
    static String withPort(String uri, int port) {
        if (port < 0 || uri == null) return uri;
        return UriComponentsBuilder.fromUriString(uri).port(port).build(true).toUriString();
    }

    static OAuth2AuthorizationRequest followPort(HttpServletRequest request, OAuth2AuthorizationRequest req) {
        if (req == null) return null;
        int port = explicitPort(request);
        if (port < 0) return req;
        return OAuth2AuthorizationRequest.from(req)
                .authorizationUri(withPort(req.getAuthorizationUri(), port))
                .authorizationRequestUri(withPort(req.getAuthorizationRequestUri(), port))
                .build();
    }
}
