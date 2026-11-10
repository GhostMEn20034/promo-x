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

## Repository layout

```
promo-x-core/         The engine (Spring Boot application)
common-infra/         Infrastructure shared by promo-x services
  dev/                  Keycloak for local development
  ci/keycloak/          Keycloak realm used by the CI smoke test
docs/                 System design
  promo-x-core/         Backend design
```

## Running locally

Prerequisites: JDK 25 and Docker.

1. **Start Keycloak** (http://localhost:8080, admin / admin). Its compose file uses an external network:

   ```bash
   docker network create promo-x-network
   docker compose -f common-infra/dev/docker-compose.dev.yaml up -d
   ```

   Create a realm with your clients and roles, and a mapper that puts the realm roles in a top-level `roles` claim.

2. **Start PostgreSQL and Redis** from `promo-x-core`, with the variables below in `promo-x-core/.env`:

   ```bash
   cd promo-x-core
   docker compose -f docker-compose-dev.yaml up -d
   ```

3. **Export the variables** for the migration and the app (Spring doesn't read `.env` itself), then
   **apply the migrations**. Flyway runs through the Maven plugin, not at application startup:

   ```bash
   set -a; source .env; set +a
   ./mvnw flyway:migrate
   ```

4. **Run the application** on http://localhost:8000:

   ```bash
   ./mvnw spring-boot:run
   ```

If `./mvnw` isn't executable on your machine, run it as `sh mvnw`.

### Environment variables

| Variable | Description |
|---|---|
| `SQL_HOST` | PostgreSQL host and port, e.g. `localhost:5432` |
| `SQL_DATABASE`, `SQL_USER`, `SQL_PASSWORD` | Application database and credentials |
| `POSTGRES_PASSWORD` | Superuser password of the dev PostgreSQL container (Docker Compose only) |
| `REDIS_HOST`, `REDIS_PASSWORD` | Redis host and port, e.g. `localhost:6379`, and its password |
| `IDP_ISSUER_URL` | JWT issuer, e.g. `http://localhost:8080/realms/<realm>` |
| `SQL_POOL_MAX_SIZE` | Database connections per instance (default 10). Size it for the database server, about (DB cores × 2) + disks, shared by all instances |
| `SQL_POOL_MIN_IDLE` | Idle connections kept open (defaults to `SQL_POOL_MAX_SIZE`, a fixed-size pool) |

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
