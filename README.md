# demo-order-service

This is a code sample showing a simple order processing and management microservice example in Java with Spring Boot. Some things are mocked/faked for demo purposes and are called out in comments.

Imagine this is one of several microservices making up the backend of an online store. Other services might include a user account service, a login/authorization service (IdP), a payment service, a notification service, etc.

### Tech stack
- Java 21
- Spring Boot
- PostgreSQL
- Docker

## Security

This service uses Spring Security with JWT authentication. The access token is expected to be provided via an HTTP-only cookie set by an external login service. For non-browser clients, an Authorization: Bearer header is also supported as a fallback.

- Method security is enabled; admin endpoints are protected with `@PreAuthorize("hasRole('ADMIN')")`.
- Stateless API: no server-side sessions.
- Actuator endpoints `/actuator/health`, `/actuator/info`, and `/actuator/metrics` are publicly accessible.
- CSRF is disabled for this API service. When using cookies in a browser, ensure the login service sets tokens with `HttpOnly`, `Secure`, and an appropriate `SameSite` attribute to mitigate CSRF.

## Build and Run Tests
If you really, *really* want to build this locally and run the tests, you'll need to do the following:
1. Clone boot-demo-parent
   1. Run `mvn install` in the root of boot-demo-parent to install the parent pom into `~/.m2/repository/...`
2. Clone demo-shared-common
   1. Run `mvn install` in the root of demo-shared-common to install the jar into `~/.m2/repository/...`
3. Now you can build this project and run the tests