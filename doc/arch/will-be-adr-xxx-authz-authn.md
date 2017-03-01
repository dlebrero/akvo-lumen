Maybe FE will need Authz logic to hide/show button, or Lumen-BE will need to provide api/metadata about actions to FE

Server refreshing the access-token: -resilience, +latency

Server can store access-token + id + refresh-token in a cookie, making it stateless at the cost of sending/parsing cookie. Cookie probably is gigantic (org.keycloak.adapters.CookieTokenStore)

Storing in cookie means that there is no single sign-out. Rely on access-token expiration.

Session storage requires configuration requires shared state between instances, maybe infinispan (memory, replication) but Keycloak also requires the same in HA

FE session is as secure as BE, assuming no bugs in FE, HTTPS only and strict about redirects. Some CSRF needed.

We can probably can use the Keycloak authz without server side authn

Given that we are building a SPA, it will need to understand 401/302 from the REST apis, hence we can still serve the index.html from Nginx and on the first request from FE -> BE, the BE will return 401/302

Can we share the solution with RSR and Flow?

As Immutant uses Undertow, the request does not have a httpservletrequest/response (unless we mount a servlet /Users/dlebrero/.m2/repository/org/immutant/web/2.1.5/web-2.1.5.jar!/immutant/web/internal/servlet.clj:185?), 
hence we cannot use the Keycloak Servlet adapter, but this also seems to mean that we need to use the Undertow specific adapter. 

But as we are using Ring and not J2EE, we are not going to use the J2EE web security standard anyway, 
hence what is the value of using an Undertow specific integration?

The Keycloak Undertow adapter seems fragile, the code contains two "workarounds" due to Undertow API changes. 
Probably more to be expected in future Undertow versions.

Undertow specific means that we cannot use it in Flow, but we cannot use it either in RSR anyway.

The immutant-web example from Ivan creates a io.undertow.server.HttpHandler, 
so it must be the first in the middleware chain. Right now is wrap-hide-errors.

If we create a ring compatible keycloak middleware, can we reusing any of the Keycloak adapter code?

The JavaScript client handles the UMA flow for the stateless service + request entitlements. https://keycloak.gitbooks.io/documentation/content/authorization_services/topics/enforcer/js-adapter.html

Latter
------
How we will implement one-off tokens/shares?