Maybe FE will need Authz logic to hide/show button, or Lumen-BE will need to provide api/metadata about actions to FE

Server refreshing the access-token: -resilience, +latency

Server can store access-token + id + refresh-token in a cookie, making it stateless at the cost of sending/parsing cookie. Cookie probably is gigantic (org.keycloak.adapters.CookieTokenStore)

Storing in cookie means that there is no single sign-out. Rely on access-token expiration.

Session storage requires configuration requires shared state between instances, maybe infinispan (memory, replication) but Keycloak also requires the same in HA

FE session is as secure as BE, assuming no bugs in FE, HTTPS only and strict about redirects. Some CSRF needed.

We can probably can use the Keycloak authz without server side authn

Given that we are building a SPA, it will need to understand 401/302 from the REST apis, hence we can still serve the index.html from Nginx and on the first request from FE -> BE, the BE will return 401/302

Can we share the solution with RSR and Flow?

Latter
------
How we will implement one-off tokens/shares?