# Testing promo-x-core

```bash
cd promo-x-core
./mvnw test
```

The tests start PostgreSQL and Redis with Testcontainers, so Docker must be running. Security is mocked
with `@WithMockJwt`, so no identity provider is needed. If `./mvnw` isn't executable on your machine, run it as
`sh mvnw`.

One test class, or one test of it:

```bash
./mvnw test -Dtest=PromotionEvaluationIntegrationTest
./mvnw test -Dtest='RequestLoggingIntegrationTest#accessDenied_ShouldBeLoggedWithUser'
```

## Output

The console shows each test class as a tree of its tests, by their `@DisplayName`. The application's logs aren't
printed there: they are in each class's report, `target/surefire-reports/TEST-<class>.xml`, in its `system-out`.

Surefire may print `ForkStarter IOException: No value present` after the results. It comes from the tree reporter
(`maven-surefire-junit5-tree-reporter`) and doesn't affect the tests or their results.

## Writing integration tests

- Extend `IntegrationTestBase`: a `@SpringBootTest` with `MockMvc`, the `test` profile, and the PostgreSQL and
  Redis containers. It empties every table and cache before each test.
- Authenticate with `@WithMockJwt(username = ..., roles = ...)`, or `@WithMockAnonymousUser` for a request without a
  token.
- The containers are started once and shared by every test context. A test that changes the context, with
  `@MockitoBean` or its own `@Import`, gets a context of its own, which takes a few seconds more to start.

## CI

CI (`.github/workflows/promo-x-core-ci.yml`) has two jobs: the tests, and a smoke test that applies the
migrations, starts the application against real PostgreSQL, Redis and Keycloak, and checks `/actuator/health`.
