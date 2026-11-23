# LabelHive — sign-in and access

## Deployed: OpenID Connect against the site's login service

Every profile except `debug` signs users in with OpenID Connect against the
site's cloud-stack login service (an OIDC provider in the site proxy):
authorization code flow with PKCE (S256), Spring registrationId `cloudstack`,
scopes `openid profile email`. Deployments run the `deployed` profile.

| Env var | Required | |
|---|---|---|
| `OIDC_ISSUER` | yes | e.g. `https://auth.hitorro.com` (Belmont), `https://auth.geekychris.com` (Orlando). The app refuses to start without it (outside `debug`). The ID token's `iss` must equal it. |
| `OIDC_CLIENT_ID` | no (`labelhive`) | |
| `OIDC_CLIENT_SECRET` | yes | from the Kubernetes Secret `labelhive-oidc`, key `client-secret` |
| `OIDC_BACKCHANNEL_URL` | no (= issuer) | base for server-to-server calls (token, JWKS), e.g. `https://auth.hitorro.com:8443` with a pod `hostAliases` entry pointing `auth.<zone>` at the proxy, so sign-in works on the LAN with the WAN down |
| `DB_PASSWORD` | yes | no default anywhere; `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` as before |

Endpoints used (all built from the env vars, no discovery, so the app starts
while the provider is unreachable): authorize `OIDC_ISSUER/oidc/authorize`,
token `BACKCHANNEL/oidc/token`, keys `BACKCHANNEL/oidc/keys`, end session
`OIDC_ISSUER/oidc/end_session`. There is no userinfo call: every claim is in
the ID token.

**Redirect URI** `{baseUrl}/login/oauth2/code/cloudstack`, where `{baseUrl}`
is the public name the proxy forwards (`server.forward-headers-strategy=framework`;
the frontend nginx passes `X-Forwarded-Host`/`-Proto` on unchanged). So the
client must be registered with `https://labelhive.<zone>/login/oauth2/code/cloudstack`
(and at Belmont also the `:8443` variant).

**Following the request's port.** When the app is reached on an explicit
non-443 https port (Belmont's LAN path `https://labelhive.hitorro.com:8443`),
the authorization and end-session URLs get the same port
(`https://auth.hitorro.com:8443/oidc/authorize`), because the provider sits
on the same proxy. (`PortFollowingAuthorizationRequestResolver`,
`PortFollowingLogoutSuccessHandler`.)

### Roles

The ID token's `roles` claim carries this app's role names for the user:

| Provider role | Authorities | App role (`app_user.role`) | Can |
|---|---|---|---|
| `admin` | `ROLE_ADMIN`, `ROLE_WORKER` | `ADMIN` | everything: jobs, units, reports, concerns, gold, MCP |
| `labeler` | `ROLE_LABELER`, `ROLE_WORKER` | `WORKER` | list jobs, work tasks (`/api/jobs/*/tasks/**`), raise concerns |

A sign-in with neither role is refused (403 page "no LabelHive role"); the
provider normally stops such users before that. The provider is
authoritative: the app-side role is rewritten on every sign-in.

### Users

The app keeps a row per user in `app_user` (judgments, trust and gold
attribution point at it). On first sign-in it is created, keyed by the
token's `sub` (`app_user.oidc_subject`, migration V8) with
`username = preferred_username`; a later sign-in follows a changed username.
An existing row with the same username and no subject (e.g. a seeded demo
user) is adopted; a username already linked to another `sub` is refused.

### Access rules

| Path | Who |
|---|---|
| `/actuator/health` | anyone (probes) |
| `/api/me`, `/api/auth/me` | any signed-in user |
| `GET /api/jobs`, `/api/jobs/*/tasks/**` | admin, labeler |
| every other `/api/**` | admin |
| `/sse`, `/mcp/**` (MCP) | admin |
| anything else | signed in |

Unauthenticated API and MCP calls get **401**; the SPA reacts to a 401 by
sending the browser to `/oauth2/authorization/cloudstack`. Other browser
navigation is redirected there directly.

`GET /api/me` → `{"username":"alice","roles":["labeler"],"sub":"pau_…","name":…,"email":…,"id":12,"role":"WORKER","debug":false}`
(401 when not signed in). The header shows the user, their roles and a
**Sign out** button: `POST /logout` (CSRF token) ends the local session, then
RP-initiated logout at the provider (which also ends the site-wide sign-in),
back to `{baseUrl}/`.

**CSRF:** on, cookie-based (`XSRF-TOKEN` → `X-XSRF-TOKEN` header, or `_csrf`
on the sign-out form). The MCP endpoints are exempt (clients can't fetch a
token); they are admin-only and the session cookie is `SameSite=Lax`.

The proxy also sends `X-Cloudstack-User`/`X-Cloudstack-Roles`; the app does
not use them. The old `X-User-Id` header is ignored outside `debug`, and the
frontend nginx strips it.

### MCP and the Python SDK against a deployed instance

Pending. Both need a browser sign-in session today (MCP additionally the
admin role), which non-browser clients can't obtain: the login service
issues no tokens to scripts (no client-credentials or device flow), and the
site proxy in front (`exposure: login`) wants its own sign-in too. Use them
against a local `debug` instance. A bearer-token (resource server) path is
the follow-up once the provider can mint tokens for scripts.

## Local development: the `debug` profile

No sign-in. Identity comes from the `X-User-Id` header (numeric id or
username; the UI header has a user switcher) and defaults to the seeded
`demo` admin; the demo seeder creates `demo` (admin) and `alice`, `bob`,
`carol` (workers) plus six jobs.

It cannot leak into a deployment: the app **refuses to start** with the
`debug` profile when `OIDC_ISSUER` is set or the `deployed`/`prod` profile
is active (`DebugSecurityConfig`). The old `datalabel.debug-auth` /
`DATALABEL_DEBUG_AUTH` switch is gone; only the profile turns it on.
There is no form login or HTTP basic in any profile any more.
