# promo-x

A promotion engine that other systems call to price a cart. A storefront, checkout or order service sends
the cart, and promo-x evaluates the storefront's promotion rules against it. Every rule that matches applies its
financial action, such as a percentage off a product, a flat order discount or free shipping. The response is
the repriced cart, with what changed and which rules caused it.

Rules can be limited to a number of uses, which are reserved during checkout and confirmed or released once the
order is placed or abandoned. Every applied promotion is audited, and usage statistics are available per storefront.

Built as a modular monolith with Java 25, Spring Boot 4 and Spring Modulith, on PostgreSQL and Redis.

## Documentation

- [Architecture](docs/architecture.md): the system design. Who calls promo-x, storefronts and catalog, rules,
  conditions and actions, how a cart is evaluated, usages and their lifecycle, roles.
- [promo-x-core architecture](docs/promo-x-core/architecture.md): the backend. Modules and events, the code
  structure (controllers, LookupApis, CommandHandlers, validators, services, repositories), the evaluation flow in
  code, usage limits, persistence, caching, security, runtime and testing.
- [promo-x-core API](docs/promo-x-core/api.md): every endpoint with its request and response fields, the condition
  and action types, and the error formats (validation, business, not found and framework errors).
- [Running promo-x](docs/run.md): Keycloak, then each service.
- [Running promo-x-core](docs/promo-x-core/run.md): its database and Redis, migrations, environment variables and
  profiles, and its logs: the fields, turning on DEBUG, and reading them in Docker, Elastic Beanstalk and Kubernetes.

## Repository layout

```
promo-x-core/         The engine (Spring Boot application)
common-infra/         Infrastructure shared by promo-x services
  dev/                  Keycloak for local development
  ci/keycloak/          Keycloak realm used by the CI smoke test
docs/                 System design and how to run it
  promo-x-core/         Backend design, API, and how to run it
```

## Running locally

Prerequisites: JDK 25 and Docker. Start Keycloak, then the services, as described in
[Running promo-x](docs/run.md).

## Testing

```bash
cd promo-x-core
./mvnw test
```

The tests start PostgreSQL and Redis with Testcontainers, so Docker must be running. Security is mocked
with `@WithMockJwt`, so no identity provider is needed.

CI (`.github/workflows/promo-x-core-ci.yml`) has two jobs: the tests, and a smoke test that applies the
migrations, starts the application against real PostgreSQL, Redis and Keycloak, and checks `/actuator/health`.

## License

[MIT](LICENSE)
