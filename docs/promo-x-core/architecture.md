# promo-x-core architecture

How the promo-x-core backend implements the [system design](../architecture.md).

## Tech stack

| Area | Choice |
|---|---|
| Language and runtime | Java 25, virtual threads (`spring.threads.virtual.enabled`) |
| Framework | Spring Boot 4.1, Spring Modulith 2.1 |
| Database | PostgreSQL 18 via Spring Data JPA / Hibernate 7, migrations with Flyway |
| Cache, counters, locks | Redis 8 via Spring Data Redis; distributed locks from Spring Integration's `RedisLockRegistry` |
| JSON | Jackson 3 (`tools.jackson`) |
| Security | Spring Security OAuth2 resource server (JWT), Keycloak as the identity provider |
| Tests | JUnit 5, Testcontainers, Spring Modulith test scenarios, Awaitility |

## Modules

The application is a modular monolith. Each top-level package under `com.promox.core` is a Spring Modulith module,
and `ModularityTest` fails the build when a module reaches into another one's internals.

| Module | Owns | Public API | Publishes |
|---|---|---|---|
| `storefront` | Storefronts | `StorefrontLookupApi` | |
| `category` | Storefront category catalog | `CategoryLookupApi` | `CategoryUpdatedEvent` |
| `product` | Storefront product catalog, products with their categories | `ProductLookupApi` | `ProductUpdatedEvent` |
| `promotion` | Rules, evaluation engine, usages, usage stats | `PromotionLookupApi` | `PromotionRule*Event`, `PromotionRulesAppliedEvent`, `PromotionRuleUsageExpiredEvent` |
| `infrastructure` | Security, Redis cache, web error handling | | |

Modules call each other only through the `*LookupApi` interfaces in their root package and the records in
their `event` packages. Everything else lives under `internal`:

```
<module>/
  <Module>LookupApi.java     Public read API for other modules (and the module's own controller)
  event/                     Events other modules may listen to
  internal/
    <Module>Mapper.java      Entity ↔ DTO mapping
    web/                     REST controllers (and web-only response mappers)
    service/                 Command handlers, lookup API implementations, services, validators,
                             event listeners, scheduled jobs
    persistence/             JPA entities, repositories (PostgreSQL and Redis), query projections
    dto/                     Requests, responses and internal DTOs
    exception/               Module exceptions and their ProblemDetail handlers
```

### Events

Events are published inside the transaction that caused them and handled by `@ApplicationModuleListener`s, which run
after that transaction commits, asynchronously and in a transaction of their own. Spring Modulith stores each
publication in the database (`event_publication`) until its listener completes, so a crash doesn't lose one.

| Event | Listener | Effect |
|---|---|---|
| `PromotionRuleCreatedEvent`, `PromotionRuleUpdatedEvent`, `PromotionRuleDeleteEvent`, `PromotionRuleUsageUpdatedEvent` | `PromotionRuleCacheEventListener` | Evicts that storefront's cached rules |
| `PromotionRulesAppliedEvent` | `PromotionRuleUsageEventListener` | Lowers the applied rules' `remaining_uses` in the database |
| `PromotionRuleUsageExpiredEvent` | `PromotionRuleUsageEventListener` | Gives the expired usage's uses back in the database |
| `CategoryUpdatedEvent` | `PromotionCategoryEventListener` | Deactivates active rules that refer to the category's old external ID |
| `CategoryUpdatedEvent` | `product`'s `CategoryEventListener` | Evicts the cached products in that category |
| `ProductUpdatedEvent` | `ProductEventListener` | Evicts the cached product under its old and new external ID |

## Code structure

Every module is built from the same layers. Reads and writes take separate paths: reads go through a
**LookupApi**, and anything that changes state goes through a **CommandHandler**.

![Code structure of a module: the controller sends writes and actions to the CommandHandler, which runs validators, and reads to the LookupApi, which other modules also use; the CommandHandler, LookupApi and event listeners or jobs call the services, which use the JPA repositories, Redis repositories and cached reads and publish the module's events](../media/promo-x-core/code-structure.jpg)

| Layer | Responsibility | Examples |
|---|---|---|
| **Controller** | HTTP only: binds and validates the request, checks roles (`@PreAuthorize`), calls a CommandHandler or a LookupApi, maps the result to the response | `PromotionController`, `CategoryController` |
| **LookupApi** | Looking up and retrieving data. Read-only: it never changes state. The interface sits in the module's root package and is the only way other modules read this module's data. Its implementation maps entities to DTOs and is where read caches go | `ProductLookupApi.findAllByExternalIds`, `PromotionLookupApi.getUsageStats` |
| **CommandHandler** | Write operations and actions that change the server's state: create, update, delete, evaluate, confirm, cancel. It runs the use case: validate, call the services, map the result. It usually sets the transaction boundary | `PromotionCommandHandler.create`, `PromotionCommandHandler.evaluate` |
| **Validator** | Business checks that need data, often another module's: do these product or category IDs exist in the storefront, are these external IDs unique. Fails with a business exception, which becomes a 4xx `ProblemDetail`. Never writes | `PromotionProductValidator`, `PromotionCategoryValidator`, `ProductCategoryValidator`, `ProductValidator` |
| **Service** | The module's domain logic and its data: owns the repositories, runs transactions, publishes the module's events. Internal: only the module's own handlers, lookup APIs, listeners and jobs call it | `PromotionService`, `PromotionRuleUsageService`, `CategoryService` |
| **Repository** | Storage access. JPA repositories for PostgreSQL; Redis-backed classes for counters and caches | `PromotionRepository`, `PromotionRuleUsageCounterRepository` (Redis), `PromotionRuleCacheReader` (cached reads) |
| **Mapper** | Converts entities and projections to DTOs, and DTOs to web responses | `PromotionMapper`, `PromotionWebMapper` |
| **Event listener, Job** | Entry points that aren't HTTP: react to another module's events or run on a schedule, and call the module's services | `PromotionCategoryEventListener`, `PromotionRuleUsageExpirationJob` |

### Rules

- **Controllers depend only on their module's CommandHandler and LookupApi.** They don't call services or
  repositories.
- **Other modules may only read**, through the LookupApi. A module never changes another module's data directly; it
  publishes an event, and the owning module's listener makes the change. For example, when a category's external ID
  changes, `category` publishes `CategoryUpdatedEvent` and `promotion` deactivates its own rules.
- **Validation that needs another module** goes through that module's LookupApi, inside a Validator, so the
  CommandHandler stays a readable list of steps.
- **Events are published by services**, inside the transaction that made the change, so they are only delivered if
  it commits.

### Example: creating a promotion rule

1. `PromotionController.createPromotionRule` receives the request; `@PreAuthorize` requires `promotion-manager`.
2. `PromotionCommandHandler.create` runs the use case in one transaction:
   - collects the product external IDs from the condition tree and the action;
   - `PromotionProductValidator` checks they exist in the storefront, through `ProductLookupApi`;
   - `PromotionCategoryValidator` checks the condition tree's category external IDs exist, through `CategoryLookupApi`;
   - `PromotionService.create` saves the rule and publishes `PromotionRuleCreatedEvent`;
   - `PromotionMapper` maps the saved rule to a DTO.
3. The controller maps the DTO to the web response with `PromotionWebMapper`.
4. After the commit, `PromotionRuleCacheEventListener` evicts that storefront's cached rules.

Reading a rule needs no validators and changes nothing: `PromotionController` → `PromotionLookupApi.findById`,
which loads the rule through `PromotionService` and its products through `ProductLookupApi`, then maps them.

## Evaluating a cart

`POST /promotion/evaluate` is handled by `PromotionCommandHandler.evaluate`. It's deliberately **not** one
transaction: waiting for the rules cache must not hold a database connection.

1. **Resolve the products**: `PromotionProductValidator` checks every cart line's product exists in the storefront
   and fetches it with its categories through `ProductLookupApi`, which reads them from Redis (`RedisBatchCache`:
   one `MGET`, one database query for the misses).
2. **Build an `EngineWorkspace`**: the cart's lines, prices, shipping cost, customer and promo codes, plus the
   running discounts and a log of matched and applied rules.
3. **Load the active rules**: `PromotionRuleCacheReader.findAllActiveSortedByPriority` returns
   `PromotionRuleBaseProjection` records sorted by priority, cached per storefront.
4. **Apply the rules** in `PromotionService.applyRules`, the only transactional step:
   - `PromoRuleEngine.calculate` evaluates each rule's condition tree against the workspace and applies its action.
     A usage-limited rule first takes a use from its Redis counter, and gives it back if the action changes nothing.
     A short-circuit rule stops the loop once it changed the cart.
   - If any rule was applied, `PromotionRuleUsageService.recordUsage` saves the usage, its audit and the audited
     order lines, and a `PromotionRulesAppliedEvent` is published.
   - If the transaction rolls back, the uses taken from Redis are given back.
5. **Map the workspace** to `PromotionEvaluationResponse`.

Conditions (`ConditionNode`) and actions (`ActionStrategy`) are records registered by type name in
`ConditionNodeRegistry` and `ActionStrategyRegistry`. Adding one means a new record with a `TYPE_NAME` and a
`SPEC`, listed in its registry; Jackson uses the registries to read and write the `type` property.

## Usage limits

A usage limit is counted in two places:

| Where | Role |
|---|---|
| Redis `promotion-rule:remaining-uses:<ruleId>` | The fast, atomic gate used during evaluation. A Lua script decrements it only while it is above zero |
| PostgreSQL `promotion_rule.remaining_uses` | The durable value. A missing Redis counter is seeded from it |

How each change reaches both:

| Change | Redis | Database |
|---|---|---|
| Rule applied | Decremented during evaluation | Lowered by the `PromotionRulesAppliedEvent` listener |
| Usage canceled | Incremented after the commit | Restored in the cancel transaction |
| Usage expired | Incremented after the commit | Restored by the `PromotionRuleUsageExpiredEvent` listener |
| Usage limit changed | Counter deleted after the commit, so it's seeded again | Updated directly |

`PromotionRuleUsageExpirationJob` runs every minute and expires overdue `RESERVED` usages in batches. It locks the
rows it picks (`SELECT ... FOR UPDATE`), so a concurrent confirm or cancel, or the same job on another instance,
waits and then sees the new status instead of handling the usage twice.

## Persistence

- **Schema:** Flyway migrations in `src/main/resources/db/migration`, applied with the Flyway Maven plugin
  (`./mvnw flyway:migrate`), not at startup (`spring.flyway.enabled: false`). Hibernate only validates the schema
  (`ddl-auto: validate`).
- **Rules:** a rule's condition tree and action are `jsonb` columns, written and read with the application's
  Jackson mapper (Hibernate's `Jackson3JsonFormatMapper`, set up in `PromotionJacksonConfig`). The external IDs they
  refer to are also extracted into `rule_product` and `rule_category`, so rules can be found by category and
  validated against the catalog.
- **Usages:** `promotion_rule_usage` holds the status and the applied and limited rule IDs as arrays.
  `promotion_rule_usage_audit` and its child tables hold the audit, one row per usage, sharing its primary key.
- **Usage stats:** two native queries on the audit table, one aggregating the totals and one counting usages per UTC
  day with `generate_series`, indexed by `(storefront_id, created_at)`.
- **Sessions:** `spring.jpa.open-in-view` is off, so a connection goes back to the pool when its transaction ends.
  Whatever a response needs must be loaded inside the transaction.

## Caching

All caches are in Redis. `RedisCacheConfig` replaces Boot's cache manager, so `spring.cache.redis.*` properties
don't apply; TTLs are set per cache in `promox.cache.ttls`.

| Cache | Key | Value | TTL | Evicted by |
|---|---|---|---|---|
| `promotion-rules` | storefront ID | Active rules, sorted by priority | 1h | Rule created, updated, deleted or its usage limit changed |
| `promotion-usage-stats` | storefront, `from`, `to` | Usage stats response | 30m | Expiry only |
| `products-with-categories` | product | Product with its categories | 1h | Product or category updated |

- **Locking on a miss:** `@Cacheable(sync = true)` goes through `KeyLockingRedisCache`. On a miss it takes a lock for
  that one cache entry from `RedisLockRegistry`, on this instance and across instances, so only one caller loads the
  value and the others wait for it. The lock's TTL is `promox.cache.lock-ttl` (30s) and must stay longer than the
  slowest load.
- **Writes are immediate:** the cache writer sends `put`/`evict` to Redis before returning, so a value loaded under
  the lock is stored before the lock is released.
- **Serialization:** Spring cache values use Java serialization with the application's class loader, so they come
  back as exactly their type. Every cached class, and everything it holds, must be `Serializable`; cached classes are
  records, so adding or removing a field keeps old entries readable.
- **Batch reads:** `products-with-categories` goes through `RedisBatchCache` instead, which reads many keys in one
  `MGET` and stores JSON with a serializer for the known value type.

## Security

- **Tokens:** an OAuth2 resource server accepting JWTs from `IDP_ISSUER_URL`. The decoder is created on first use,
  so the application starts even if the identity provider is down.
- **Roles:** `JwtAuthoritiesConverter` maps the token's top-level `roles` claim to `ROLE_<role>` authorities. Keycloak's
  `realm_access` and `resource_access` are ignored, and scopes don't become authorities.
- **Access rules:** method security (`@PreAuthorize`) on the controllers, with role names from `Roles`.
- **Health:** `/actuator/health` is public; its details are shown only to authenticated callers.

## HTTP API

The endpoints, request and response fields and error formats are documented in [api.md](api.md).

Error responses are built in one place per kind:

| Class | Handles | Response |
|---|---|---|
| `ApiValidationExceptionHandler` | Bean Validation failures, wrong value types, unreadable JSON | 400, `code: VALIDATION_ERROR` |
| `ApiExceptionHandler` | Any `BusinessException` | 409, `title: Business rule violation` |
| `<Module>ExceptionHandler` (`@Order(HIGHEST_PRECEDENCE)`) | The module's `*NotFoundException`s | 404 |

A business error is a `BusinessException` subclass carrying a `code` and a list of `BusinessError`s (`path`, `code`,
`message`, `errorContext`), so validators and services report problems without knowing about HTTP.
Errors outside the application's handlers, such as 401 and 403 from Spring Security or 404 for an unknown path, keep
the framework's own responses.

## Runtime

- **Virtual threads:** every request runs on a virtual thread, so the database connection pool, not a thread pool,
  limits concurrent database work.
- **Connection pool:** HikariCP, sized by `SQL_POOL_MAX_SIZE` (default 10) and `SQL_POOL_MIN_IDLE` (defaults to the
  max, a fixed-size pool). Size it for the database server, about (DB cores × 2) + disks, shared by every instance.
- **Several instances** can run against the same PostgreSQL and Redis: cache locks, usage counters and the
  expiration job's row locks all work across instances.

The connection pool settings, like every environment variable, are listed in the [README](../../README.md#environment-variables).

## Testing

- **`IntegrationTestBase`** starts the full application against PostgreSQL and Redis containers (Testcontainers),
  applies the Flyway migrations once, and empties every table and cache before each test.
- **Security:** `@WithMockJwt(roles = ...)` and `@WithMockAnonymousUser` set up the caller, so tests need no
  identity provider. `JwtAuthoritiesConverterTest` covers the token-to-roles mapping.
- **Asynchronous work:** event listeners are awaited with Spring Modulith's `Scenario`, other conditions with
  Awaitility.
- **Module boundaries:** `ModularityTest` verifies the Spring Modulith module structure.
