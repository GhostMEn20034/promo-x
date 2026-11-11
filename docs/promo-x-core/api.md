# promo-x-core HTTP API

The HTTP API of promo-x-core. The concepts behind it (storefronts, rules, usages) are explained in the
[system design](../architecture.md), and how the backend implements them in [architecture.md](architecture.md).

- [Conventions](#conventions)
- [Errors](#errors)
- [Promotion evaluation and usages](#promotion-evaluation-and-usages)
- [Promotion rules](#promotion-rules)
- [Conditions and actions](#conditions-and-actions)
- [Categories](#categories)
- [Products](#products)
- [Storefronts](#storefronts)
- [Health](#health)

## Conventions

| Topic | Convention |
|---|---|
| Base URL | The application listens on port 8000, e.g. `http://localhost:8000` |
| Authentication | Every endpoint except `/actuator/health` needs `Authorization: Bearer <JWT>` from the configured identity provider. The roles come from the token's top-level `roles` claim |
| Storefront | Endpoints that work inside a storefront take it in the `X-Storefront-Id` header (a storefront ID). Endpoints that take a resource ID in the path don't need it |
| Bodies | JSON, `Content-Type: application/json` |
| IDs | Storefronts, categories, products and rules have numeric IDs. Usages have UUIDs. Rules refer to products and categories by **external ID**, the ID your system uses |
| Money | Decimal JSON numbers (`100.00`), in the storefront's currency. promo-x doesn't convert currencies |
| Percentages | Fractions from 0 to 1: `0.20` is 20% |
| Timestamps | ISO-8601 in UTC, e.g. `2026-11-10T21:35:46.348323Z` |
| Dates | ISO-8601 dates, e.g. `2026-01-10`. Days are UTC days |
| Paging | `page` is 1-based (`page=1` is the first page), `size` is the page size (20 by default), `sort=property,asc\|desc`. In the response, `number` is 0-based |

### Roles

| Role | For |
|---|---|
| `integration-client` | Systems that evaluate carts and confirm or cancel usages |
| `promotion-manager` | Staff managing promotion rules |
| `employee` | Staff managing the catalog |
| `auditor` | Read-only reporting |
| `admin` | Administrators |

## Errors

### Error responses

Errors raised by promo-x itself are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details, with
`Content-Type: application/problem+json`:

```json
{
  "type": "https://api.example.com/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more validation errors occurred.",
  "instance": "/storefront/",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-11-10T21:35:47.046190267Z",
  "errors": [
    {
      "category": "VALIDATION",
      "source": "REQUEST_BODY",
      "path": "name",
      "code": "NOT_BLANK",
      "message": "must not be blank",
      "errorContext": null
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `type` | URI identifying the kind of problem. The domain is a placeholder for now |
| `title` | Short summary of the kind of problem, the same for every error of that kind |
| `status` | The HTTP status |
| `detail` | Human-readable explanation of this occurrence |
| `instance` | The request path |
| `code` | Machine-readable code for the whole error: `VALIDATION_ERROR`, or a business or not-found code. Branch on this, not on the texts |
| `timestamp` | When the error happened |
| `errors` | The individual problems, at least one |

Each item in `errors`:

| Field | Meaning |
|---|---|
| `category` | `VALIDATION` (the request is malformed) or `BUSINESS` (the request is well-formed, but the current data doesn't allow it) |
| `source` | Where the problem is: `REQUEST_BODY`, `QUERY_PARAMETER`, `PATH_VARIABLE`, `HEADER`, `COOKIE`, `DOMAIN` (business rules) or `UNKNOWN` |
| `path` | The field the problem is about, or null when it's about the whole request. Nested fields use dots and indexes, such as `items[0].externalId`; a JSON value of the wrong type is reported as `items.[0].quantity` |
| `code` | Machine-readable code of this problem |
| `message` | Human-readable explanation |
| `errorContext` | Extra data about the problem, such as the IDs that weren't found, or null |

### Validation errors (400)

The request can't be processed as sent: a field is missing or out of range, a value has the wrong type, or the JSON
is malformed. `code` is always `VALIDATION_ERROR`, and every item has `category: VALIDATION`.

```json
{
  "type": "https://api.example.com/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more validation errors occurred.",
  "instance": "/promotion/evaluate",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-11-10T21:35:47.055842631Z",
  "errors": [
    {
      "category": "VALIDATION",
      "source": "REQUEST_BODY",
      "path": "shippingCost",
      "code": "DECIMAL_MIN",
      "message": "Shipping cost must be positive",
      "errorContext": null
    },
    {
      "category": "VALIDATION",
      "source": "REQUEST_BODY",
      "path": "items",
      "code": "SIZE",
      "message": "size must be between 1 and 128",
      "errorContext": null
    }
  ]
}
```

| Item `code` | Meaning |
|---|---|
| `NOT_NULL` | The field is required |
| `NOT_BLANK` | The text is required and can't be only spaces |
| `NOT_EMPTY` | The list is required and can't be empty |
| `SIZE` | Text length or list size outside the allowed range |
| `POSITIVE` | Must be greater than 0 |
| `MIN` | Below the minimum |
| `DECIMAL_MIN`, `DECIMAL_MAX` | Decimal below the minimum or above the maximum |
| `ASSERT_TRUE` | A check across fields failed, e.g. `validRange` when `from` is after `to` |
| `TYPE_MISMATCH` | The value has the wrong type, e.g. text where a number is expected, or a path ID that isn't a number. For a value that must be one of a fixed set, such as an operator, `errorContext.allowedValues` lists them |
| `MISSING_REQUIRED_FIELD` | A condition or action is missing a value it needs, or the value is blank |
| `UNSUPPORTED_OPERATOR` | The condition doesn't support this operator (see each condition's operators below) |
| `INVALID_VALUE_RANGE` | A condition or action value is out of range, e.g. a percentage above 1 |
| `UNKNOWN_TYPE` | A condition or action has a `type` that doesn't exist. `errorContext.allowedTypes` lists the valid ones |
| `MISSING_TYPE` | A condition or action has no `type` |
| `INVALID_VALUE` | An object couldn't be built from the given values, for another reason |
| `INVALID_JSON` | The body isn't valid JSON. `path` is null |

For errors in a condition or action, `path` points at the object that rejected the value, such as `conditionTree`,
`conditionTree.children.[0]` or `action`, and `message` says what's wrong:

```json
{
  "category": "VALIDATION",
  "source": "REQUEST_BODY",
  "path": "conditionTree.children.[0]",
  "code": "UNSUPPORTED_OPERATOR",
  "message": "Operator 'GREATER_THAN_OR_EQUAL' is not supported by PromoCodeConditionNode.",
  "errorContext": null
}
```

### Business errors (409)

The request is valid, but the data it refers to doesn't allow it: an external ID doesn't exist, a value is already
taken, or a usage is in the wrong state. `title` is `Business rule violation`, `code` names the rule that was
broken, and the items have `category: BUSINESS` and `source: DOMAIN`.

```json
{
  "type": "https://api.example.com/problems/business-rule-violation",
  "title": "Business rule violation",
  "status": 409,
  "detail": "Validation failed: specified product external IDs do not exist",
  "instance": "/promotion/evaluate",
  "code": "PRODUCT_EXTERNAL_IDS_NOT_FOUND",
  "timestamp": "2026-11-10T21:35:47.012345678Z",
  "errors": [
    {
      "category": "BUSINESS",
      "source": "DOMAIN",
      "path": "items",
      "code": "PRODUCT_EXTERNAL_IDS_NOT_FOUND",
      "message": "One or more product external IDs do not exist",
      "errorContext": {
        "existingProductExternalIds": ["PROD-KB-01"],
        "missingProductExternalIds": ["PROD-NOPE"]
      }
    }
  ]
}
```

| `code` | When | `errorContext` |
|---|---|---|
| `PRODUCT_EXTERNAL_IDS_NOT_FOUND` | A rule or a cart refers to product external IDs that don't exist in the storefront | `missingProductExternalIds`, `existingProductExternalIds` |
| `CATEGORY_EXTERNAL_IDS_NOT_FOUND` | A rule refers to category external IDs that don't exist in the storefront | `missingCategoryExternalIds`, `existingCategoryExternalIds` |
| `CATEGORY_IDS_NOT_FOUND` | A product refers to category IDs that don't exist in the storefront. In a bulk insert, there's one item per product, with `path: items[<index>].categoryIds` | `missingCategoryIds`, `existingCategoryIds` |
| `EXTERNAL_ID_ALREADY_EXISTS` | A product with this external ID already exists in the storefront | In a bulk insert, the external ID |
| `EXTERNAL_ID_VALIDATION_FAILED` | A bulk insert has external ID conflicts. Its items are `DUPLICATE_EXTERNAL_ID_IN_BATCH` (`externalId`, `firstOccurredAtIndex`) and `EXTERNAL_ID_ALREADY_EXISTS`, each with `path: items[<index>].externalId` | Per item |
| `CATEGORY_ALREADY_EXISTS` | A category with this external ID already exists in the storefront. `path: externalId` | |
| `PARENT_CATEGORY_NOT_FOUND` | The category's `parentId` doesn't exist. `path: parentId` | |
| `INVALID_STOREFRONT` | The `X-Storefront-Id` storefront doesn't exist (creating categories and products). `path: storefrontId` | |
| `INVALID_SORT_PROPERTY` | The rule list was sorted by a property that isn't allowed | `invalidProperty`, `allowedProperties` |
| `PROMOTION_RULE_USAGE_NOT_RESERVED` | Confirming or canceling a usage that isn't `RESERVED` | `status`: the usage's current status |

### Not found (404)

The resource in the path doesn't exist. These have the same body as business errors, with their own `type` and
`title`:

```json
{
  "type": "https://api.example.com/problems/promotion-not-found",
  "title": "Promotion not found",
  "status": 404,
  "detail": "Promotion Rule with id '999999' was not found.",
  "instance": "/promotion/999999",
  "code": "PROMOTION_NOT_FOUND",
  "timestamp": "2026-11-10T21:35:47.030000000Z",
  "errors": [
    {
      "category": "BUSINESS",
      "source": "DOMAIN",
      "path": null,
      "code": "PROMOTION_NOT_FOUND",
      "message": "Promotion Rule does not exist.",
      "errorContext": null
    }
  ]
}
```

| `code` | `title` |
|---|---|
| `PROMOTION_NOT_FOUND` | Promotion not found |
| `PROMOTION_RULE_USAGE_NOT_FOUND` | Promotion rule usage not found |
| `STOREFRONT_NOT_FOUND` | Storefront not found |
| `CATEGORY_NOT_FOUND` | Category not found |
| `PRODUCT_NOT_FOUND` | Product not found |

### Other errors

These come from Spring Security and Spring Boot, not from promo-x, so they don't use the problem format above.

| Status | When | Response |
|---|---|---|
| 401 | No token, or the token is invalid or expired | Empty body. `WWW-Authenticate: Bearer ...` says why |
| 403 | The token is valid, but none of its roles may call this endpoint | Empty body. `WWW-Authenticate: Bearer error="insufficient_scope", ...` |
| 400 | A required header, such as `X-Storefront-Id`, is missing | Spring Boot error body, see below |
| 404 | No endpoint at this path | Spring Boot error body |
| 405 | The path exists, but not with this HTTP method | Spring Boot error body |
| 500 | An unexpected server error | Spring Boot error body |

Spring Boot's error body:

```json
{"timestamp": "2026-11-10T21:37:26.988Z", "status": 400, "error": "Bad Request", "path": "/promotion/"}
```

The role check runs after the request has been read and validated, so a caller without the right role who sends an
invalid body gets the 400, not the 403.

## Promotion evaluation and usages

### `POST /promotion/evaluate`

Prices a cart: evaluates the storefront's active rules against it and applies the matching ones.

- **Roles:** `integration-client`, `promotion-manager`
- **Headers:** `X-Storefront-Id`

Request:

```json
{
  "sessionId": "cart-7f3a",
  "customerId": "customer-42",
  "promoCodes": ["SUMMER"],
  "items": [
    { "productExternalId": "PROD-KB-01", "quantity": 2, "unitPrice": 100.00 },
    { "productExternalId": "PROD-MS-01", "quantity": 1, "unitPrice": 25.00 }
  ],
  "shippingCost": 10.00,
  "reserveUsage": true
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `sessionId` | string | no | Your identifier for the cart or session (cart ID, session cookie, device ID). Stored in the usage audit |
| `customerId` | string | no | Your customer ID. Null means an anonymous customer, which `CUSTOMER_AUTHENTICATION_STATE` conditions check |
| `promoCodes` | string[] | yes | The promo codes the customer entered. Send `[]` for none |
| `items` | object[] | yes, 1–128 | The cart's lines |
| `items[].productExternalId` | string | yes | The product's external ID. It must exist in the storefront's catalog |
| `items[].quantity` | integer | yes, ≥ 1 | How many units |
| `items[].unitPrice` | decimal | yes, ≥ 0 | The price of one unit, before promotions |
| `shippingCost` | decimal | yes, ≥ 0 | The order's shipping cost, before promotions |
| `reserveUsage` | boolean | no, default `true` | `true`: uses of usage-limited rules are held until you confirm or cancel the usage. `false`: they count as used right away, for callers that don't confirm orders |

Response `200`:

```json
{
  "sessionId": "cart-7f3a",
  "customerId": "customer-42",
  "promoCodes": ["SUMMER"],
  "oldTotal": 235.0,
  "newTotal": 195.0,
  "totalDiscount": 40.0,
  "itemsSubtotal": 225.0,
  "itemsDiscount": 40.0,
  "itemsTotal": 185.0,
  "oldShippingCost": 10.0,
  "shippingDiscount": 0,
  "newShippingCost": 10.0,
  "items": [
    {
      "productExternalId": "PROD-KB-01",
      "productSku": "SKU-KB-01",
      "categories": ["CAT-KEYBOARDS"],
      "collections": [],
      "quantity": 2,
      "unitPrice": 100.0,
      "priceBeforeDiscount": 200.0,
      "discountAmount": 40.0,
      "priceAfterDiscount": 160.0
    },
    {
      "productExternalId": "PROD-MS-01",
      "productSku": "SKU-MS-01",
      "categories": [],
      "collections": [],
      "quantity": 1,
      "unitPrice": 25.0,
      "priceBeforeDiscount": 25.0,
      "discountAmount": 0,
      "priceAfterDiscount": 25.0
    }
  ],
  "matchedRules": [{ "ruleId": 1, "ruleName": "20% off keyboards with SUMMER" }],
  "appliedRules": [{ "ruleId": 1, "ruleName": "20% off keyboards with SUMMER" }],
  "usage": {
    "id": "5cbc1006-9c90-4252-b39e-d9511bc88cc3",
    "status": "RESERVED",
    "expiresAt": "2026-11-10T21:50:46.657587636Z"
  }
}
```

| Field | Meaning |
|---|---|
| `sessionId`, `customerId`, `promoCodes` | Echoed from the request |
| `itemsSubtotal` | Sum of `quantity × unitPrice` over all lines, before promotions |
| `itemsDiscount` | Total discount on the lines |
| `itemsTotal` | `itemsSubtotal − itemsDiscount` |
| `oldShippingCost` | The request's `shippingCost` |
| `shippingDiscount` | Discount on shipping |
| `newShippingCost` | `oldShippingCost − shippingDiscount` |
| `oldTotal` | `itemsSubtotal + oldShippingCost`: what the order cost before promotions |
| `totalDiscount` | `itemsDiscount + shippingDiscount` |
| `newTotal` | `itemsTotal + newShippingCost`: what the customer pays |
| `items[].productSku` | The product's SKU from the catalog |
| `items[].categories` | External IDs of the product's categories, from the catalog. Category conditions match against these |
| `items[].collections` | The product's collections. Always empty, as collections aren't implemented yet |
| `items[].priceBeforeDiscount` | `quantity × unitPrice` |
| `items[].discountAmount` | Discount on this line, from all applied rules |
| `items[].priceAfterDiscount` | `priceBeforeDiscount − discountAmount` |
| `matchedRules` | Rules whose conditions matched, highest priority first, whether or not their action applied |
| `appliedRules` | Rules whose action changed the cart, in the order they were applied |
| `usage` | The usage recorded for this evaluation, or null when no rule was applied. Keep `usage.id` to confirm or cancel it |
| `usage.status` | `RESERVED` when `reserveUsage` was true and an applied rule has a usage limit, otherwise `USED` |
| `usage.expiresAt` | When a `RESERVED` usage expires unless it's confirmed (15 minutes by default). Null if it was never reserved |

A rule that matches but can't change anything, for example because it has no uses left or its target product isn't
in the cart, appears in `matchedRules` but not in `appliedRules`.

Errors: `400` validation, `409` `PRODUCT_EXTERNAL_IDS_NOT_FOUND` (with `path: items`).

### `POST /promotion/usage/{usageId}/confirm`

Marks a reserved usage as used, for example once the order is placed. Call it before `usage.expiresAt`.

- **Roles:** `integration-client`, `promotion-manager`

Response `200`, the usage:

```json
{ "id": "5cbc1006-9c90-4252-b39e-d9511bc88cc3", "status": "USED", "expiresAt": "2026-11-10T21:50:46.657588Z" }
```

`expiresAt` keeps the original deadline.

Errors: `404` `PROMOTION_RULE_USAGE_NOT_FOUND`, `409` `PROMOTION_RULE_USAGE_NOT_RESERVED` (already confirmed,
canceled or expired; `errorContext.status` is the current status).

### `POST /promotion/usage/{usageId}/cancel`

Cancels a reserved usage, for example when the order is abandoned, and gives its held uses back to the rules.

- **Roles:** `integration-client`, `promotion-manager`

Response `200`, the usage, with `status: CANCELED`. Errors: as for confirm.

### `GET /promotion/usage/stats`

Statistics of the storefront's confirmed (`USED`) usages over a period.

- **Roles:** `auditor`, `promotion-manager`, `admin`
- **Headers:** `X-Storefront-Id`

| Query parameter | Required | Meaning |
|---|---|---|
| `from` | yes | Start of the period, a timestamp, included |
| `to` | yes | End of the period, a timestamp, excluded. Must not be before `from` |

A `+` in a timestamp offset must be sent as `%2B`, or use `Z`. Usages are counted by when they were evaluated, not
when they were confirmed.

```http
GET /promotion/usage/stats?from=2026-01-10T00:00:00Z&to=2026-01-13T00:00:00Z
```

Response `200`:

```json
{
  "totals": {
    "usageCount": 3,
    "enteredPromoCodeCount": 3,
    "registeredCustomerUsageCount": 1,
    "anonymousCustomerUsageCount": 2,
    "averageTotalDiscount": 10.0,
    "averageSubtotalDiscount": 8.3333,
    "averageShippingDiscount": 1.6667
  },
  "dailyCounts": [
    { "date": "2026-01-10", "usageCount": 1 },
    { "date": "2026-01-11", "usageCount": 1 },
    { "date": "2026-01-12", "usageCount": 1 }
  ]
}
```

| Field | Meaning |
|---|---|
| `totals.usageCount` | Number of usages |
| `totals.enteredPromoCodeCount` | Total promo codes entered across those usages, including codes that matched no rule |
| `totals.registeredCustomerUsageCount` | Usages with a `customerId` |
| `totals.anonymousCustomerUsageCount` | Usages without one |
| `totals.averageTotalDiscount` | Average order discount per usage, `oldTotal − newTotal`. Null when there is no usage |
| `totals.averageSubtotalDiscount` | Average discount on the lines per usage. Null when there is no usage |
| `totals.averageShippingDiscount` | Average shipping discount per usage. Null when there is no usage |
| `dailyCounts` | One entry for every UTC day the period touches, oldest first, with `usageCount: 0` for days without usage |

Averages are rounded to 4 decimal places. Responses are cached for 30 minutes per storefront and period, so recent
usages can take that long to appear.

Errors: `400` validation with `source: QUERY_PARAMETER` (`from`/`to` missing: `NOT_NULL`; `from` after `to`:
`ASSERT_TRUE` with `path: validRange`).

## Promotion rules

### The rule object

Returned by create, update and the usage-limit change:

```json
{
  "id": 1,
  "storefrontId": 1,
  "name": "20% off keyboards with SUMMER",
  "priority": 100,
  "isShortCircuit": false,
  "isActive": true,
  "hasUsageLimit": true,
  "usageLimit": 500,
  "remainingUses": 500,
  "conditionTree": {
    "node": {
      "type": "COMPOSITE",
      "operator": "AND",
      "children": [
        {
          "type": "PROMO_CODE",
          "code": "SUMMER",
          "operator": "EQUALS",
          "spec": {
            "displayName": "Promo Code Matching",
            "description": "Evaluates whether a specific promotional code has been applied to the current checkout session.",
            "supportedOperators": ["EQUALS", "NOT_EQUALS"],
            "operatorsDisplayName": { "EQUALS": "Matches Promo Code", "NOT_EQUALS": "Does Not Match Promo Code" }
          }
        },
        {
          "type": "SUBTOTAL",
          "threshold": 100.0,
          "operator": "GREATER_THAN_OR_EQUAL",
          "subtotalType": "GROSS",
          "spec": { "displayName": "Cart Subtotal", "...": "..." }
        }
      ]
    },
    "spec": {
      "displayName": "Condition Group",
      "description": "Combines multiple conditions together using logical AND or OR matching rules.",
      "supportedOperators": [],
      "operatorsDisplayName": {}
    }
  },
  "action": {
    "action": {
      "type": "PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION",
      "targetProductExternalId": "PROD-KB-01",
      "discountPercentage": 0.2
    },
    "spec": {
      "displayName": "Discount on specific Product",
      "description": "Applies a percentage discount rate (0.00 to 1.00) to all matching Product line items. ..."
    }
  },
  "createdAt": "2026-11-10T21:35:46.348323Z",
  "updatedAt": "2026-11-10T21:35:46.348335Z",
  "productExternalIds": ["PROD-KB-01"]
}
```

| Field | Meaning |
|---|---|
| `id`, `storefrontId` | The rule and the storefront it belongs to |
| `name` | Display name |
| `priority` | Rules are evaluated from the highest priority down |
| `isShortCircuit` | When true, once this rule changes the cart no lower-priority rule is evaluated |
| `isActive` | Only active rules are evaluated. Rules are deactivated automatically when a category they refer to changes its external ID |
| `hasUsageLimit` | Whether the rule may only be applied a limited number of times |
| `usageLimit` | The total number of uses allowed |
| `remainingUses` | Uses left. Lowered shortly after each evaluation that applies the rule, so it can lag behind by a moment |
| `conditionTree.node` | The condition tree as sent, with each nested condition's `spec` added |
| `conditionTree.spec`, `action.spec` | Display metadata for the type: `displayName`, `description`, and for conditions the `supportedOperators` and their display names. Useful for building a rule editor |
| `action.action` | The action as sent |
| `productExternalIds` | Every product external ID the rule refers to, from its conditions and its action |
| `createdAt`, `updatedAt` | Timestamps |

### `POST /promotion/`

Creates a rule. The product and category external IDs it refers to must exist in the storefront.

- **Roles:** `promotion-manager`
- **Headers:** `X-Storefront-Id`

```json
{
  "name": "20% off keyboards with SUMMER",
  "priority": 100,
  "isShortCircuit": false,
  "isActive": true,
  "hasUsageLimit": true,
  "usageLimit": 500,
  "remainingUses": 500,
  "conditionTree": {
    "type": "COMPOSITE",
    "operator": "AND",
    "children": [
      { "type": "PROMO_CODE", "code": "SUMMER", "operator": "EQUALS" },
      { "type": "SUBTOTAL", "threshold": 100.00, "operator": "GREATER_THAN_OR_EQUAL", "subtotalType": "GROSS" }
    ]
  },
  "action": {
    "type": "PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION",
    "targetProductExternalId": "PROD-KB-01",
    "discountPercentage": 0.20
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes, ≤ 255 | Display name |
| `priority` | integer | yes, > 0 | Higher is evaluated first |
| `isShortCircuit` | boolean | no, default `false` | Stop evaluating lower-priority rules once this one changes the cart |
| `isActive` | boolean | no, default `true` | Whether the rule is evaluated |
| `hasUsageLimit` | boolean | yes | Whether the rule has a usage limit |
| `usageLimit` | integer | no, default `1`, > 0 | Total uses allowed. Only matters when `hasUsageLimit` is true |
| `remainingUses` | integer | no, default `usageLimit`, > 0 | Uses left to start with. Capped at `usageLimit` |
| `conditionTree` | object | yes | A [condition](#conditions); use `COMPOSITE` to combine several |
| `action` | object | yes | An [action](#actions) |

Response `201`, the [rule object](#the-rule-object). Errors: `400` validation, `409`
`PRODUCT_EXTERNAL_IDS_NOT_FOUND`, `CATEGORY_EXTERNAL_IDS_NOT_FOUND`.

### `PUT /promotion/{id}`

Replaces a rule's name, priority, flags, conditions and action. Usage limits are changed with
`PATCH /promotion/{id}/usage-limits` instead.

- **Roles:** `promotion-manager`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes, ≤ 255 | Display name |
| `priority` | integer | yes, > 0 | Higher is evaluated first |
| `isShortCircuit` | boolean | yes | Stop evaluating lower-priority rules once this one changes the cart |
| `isActive` | boolean | yes | Whether the rule is evaluated |
| `conditionTree` | object | yes | A [condition](#conditions) |
| `action` | object | yes | An [action](#actions) |

Unlike create, every field is required: a missing `isShortCircuit` or `isActive` fails with `NOT_NULL` instead of
taking a default.

Response `200`, the [rule object](#the-rule-object). Errors: `400`, `404` `PROMOTION_NOT_FOUND`, `409` as for create.

### `PATCH /promotion/{id}/usage-limits`

Turns a rule's usage limit on or off and changes its numbers.

- **Roles:** `promotion-manager`

```json
{ "hasUsageLimit": true, "usageLimit": 1000, "remainingUses": 1000, "mutateRemainingUses": true }
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `hasUsageLimit` | boolean | yes | Whether the rule has a usage limit |
| `usageLimit` | integer | no, default `1`, > 0 | Total uses allowed |
| `mutateRemainingUses` | boolean | no, default `false` | Whether to overwrite the uses left. When false, `remainingUses` is ignored and the current count is kept |
| `remainingUses` | integer | no, default `usageLimit`, ≥ 0 | New uses left, when `mutateRemainingUses` is true. Capped at `usageLimit` |

Response `200`, the [rule object](#the-rule-object). Errors: `400`, `404` `PROMOTION_NOT_FOUND`.

### `GET /promotion/{id}`

- **Roles:** `promotion-manager`, `auditor`, `employee`

Response `200`: the [rule object](#the-rule-object), with `products` (the full products the rule refers to) instead
of `productExternalIds`:

```json
{
  "id": 1,
  "...": "...",
  "products": [
    {
      "id": 1,
      "externalId": "PROD-KB-01",
      "sku": "SKU-KB-01",
      "name": "Mechanical keyboard",
      "storefrontId": 1,
      "createdAt": "2026-11-10T21:35:46.175261Z",
      "updatedAt": "2026-11-10T21:35:46.175280Z",
      "categoryIds": [2]
    }
  ]
}
```

Errors: `404` `PROMOTION_NOT_FOUND`.

### `GET /promotion/`

Lists the storefront's rules, newest first by default.

- **Roles:** `promotion-manager`, `auditor`, `employee`
- **Headers:** `X-Storefront-Id`

| Query parameter | Meaning |
|---|---|
| `createdFrom`, `createdTo` | Only rules created in this period, both included (timestamps) |
| `isActive` | Only active (`true`) or inactive (`false`) rules |
| `isShortCircuit` | Only short-circuit (`true`) or other (`false`) rules |
| `page`, `size` | Paging; `page` starts at 1 |
| `sort` | `createdAt` (default, `desc`), `updatedAt` or `priority`, e.g. `sort=priority,desc` |

Response `200`, a page of rule summaries:

```json
{
  "content": [
    {
      "id": 1,
      "storefrontId": 1,
      "name": "20% off keyboards with SUMMER",
      "priority": 100,
      "isShortCircuit": false,
      "isActive": true,
      "createdAt": "2026-11-10T21:35:46.348323Z",
      "updatedAt": "2026-11-10T21:35:46.348335Z"
    }
  ],
  "number": 0,
  "size": 10,
  "numberOfElements": 1,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false,
  "pageable": { "...": "..." },
  "sort": { "empty": false, "sorted": true, "unsorted": false }
}
```

`number` is the 0-based page index, `size` the page size, `numberOfElements` the items on this page and
`totalElements`/`totalPages` the totals.

Errors: `409` `INVALID_SORT_PROPERTY`.

### `DELETE /promotion/{id}`

Deletes a rule.

- **Roles:** `promotion-manager`

Response `204`, no body. Errors: `404` `PROMOTION_NOT_FOUND`.

## Conditions and actions

A condition or action is a JSON object whose `type` says what it is; the other fields depend on the type.

### Conditions

Operators: `EQUALS`, `NOT_EQUALS`, `GREATER_THAN_OR_EQUAL`. Each condition supports only some of them.

| `type` | Fields | Operators | Matches when |
|---|---|---|---|
| `COMPOSITE` | `operator`: `AND` \| `OR`; `children`: conditions, at least one | (`AND`/`OR`) | All (`AND`) or any (`OR`) children match. Nest composites for complex rules |
| `PROMO_CODE` | `code` | `EQUALS`, `NOT_EQUALS` | The code was entered (or wasn't) |
| `SUBTOTAL` | `threshold` (decimal ≥ 0), `subtotalType`: `GROSS` \| `NET` | `EQUALS`, `NOT_EQUALS`, `GREATER_THAN_OR_EQUAL` | The items subtotal compares to the threshold. `GROSS`: before any discount. `NET`: after the discounts of rules applied so far |
| `CUSTOMER_AUTHENTICATION_STATE` | (operator only) | `EQUALS`, `NOT_EQUALS` | `EQUALS`: the customer is registered (`customerId` sent). `NOT_EQUALS`: anonymous |
| `SINGLE_PRODUCT_EXTERNAL_ID_MATCH` | `productExternalId` | `EQUALS`, `NOT_EQUALS` | The product is (isn't) in the cart |
| `PRODUCT_EXTERNAL_ID_ANY_MATCH` | `productExternalIds`: string[], not empty | `EQUALS`, `NOT_EQUALS` | Any of the products is (none is) in the cart |
| `PRODUCT_EXTERNAL_ID_QUANTITY` | `productExternalId`, `requiredQuantity` (≥ 1) | `EQUALS`, `GREATER_THAN_OR_EQUAL` | The product's total quantity in the cart compares to `requiredQuantity` |
| `SINGLE_PRODUCT_SKU_MATCH` | `sku` | `EQUALS`, `NOT_EQUALS` | A product with this SKU is (isn't) in the cart |
| `PRODUCT_SKU_ANY_MATCH` | `skus`: string[], not empty | `EQUALS`, `NOT_EQUALS` | Any of the SKUs is (none is) in the cart |
| `PRODUCT_SKU_QUANTITY` | `sku`, `requiredQuantity` (≥ 1) | `EQUALS`, `GREATER_THAN_OR_EQUAL` | The SKU's total quantity compares to `requiredQuantity` |
| `SINGLE_PRODUCT_CATEGORY_MATCH` | `category`: a category external ID | `EQUALS`, `NOT_EQUALS` | A product from the category is (isn't) in the cart |
| `PRODUCT_CATEGORY_ANY_MATCH` | `categories`: category external IDs, not empty | `EQUALS` | A product from any of the categories is in the cart |
| `SINGLE_PRODUCT_COLLECTION_MATCH` | `collection` | `EQUALS`, `NOT_EQUALS` | A product from the collection is (isn't) in the cart |
| `PRODUCT_COLLECTION_ANY_MATCH` | `collections`: string[], not empty | (none) | A product from any of the collections is in the cart |

Product collections aren't implemented yet: products have no collections, so the collection conditions don't match
anything for now.

### Actions

| `type` | Fields | Effect |
|---|---|---|
| `PRODUCT_EXTERNAL_ID_PERCENTAGE_DISCOUNT_ACTION` | `targetProductExternalId`, `discountPercentage` (> 0, ≤ 1) | Percentage off every line of the product |
| `PRODUCT_PERCENTAGE_DISCOUNT_ACTION` | `targetSku`, `discountPercentage` (> 0, ≤ 1) | Percentage off every line with the SKU |
| `PRODUCT_EXTERNAL_ID_BUY_X_GET_X_FREE_ACTION` | `targetProductExternalId`, `buyQuantity` (≥ 1), `freeQuantity` (≥ 1) | For every `buyQuantity` units bought, `freeQuantity` more of the same product are free, repeating |
| `PRODUCT_SKU_BUY_X_GET_X_FREE_ACTION` | `targetSku`, `buyQuantity` (≥ 1), `freeQuantity` (≥ 1) | The same, by SKU |
| `ORDER_PERCENTAGE_DISCOUNT` | `discountPercentage` (> 0, ≤ 1) | Percentage off every line of the order |
| `ORDER_FLAT_DISCOUNT` | `discountAmount` (> 0) | A fixed amount off the order, spread across the lines in proportion to their prices |
| `FREE_SHIPPING_ACTION` | (none) | Shipping costs nothing |
| `SHIPPING_PERCENTAGE_DISCOUNT` | `percentage` (> 0, ≤ 1) | Percentage off shipping |

An action that changes nothing, such as free shipping when shipping is already free or a discount on a product that
isn't in the cart, counts as not applied.

## Categories

The category object:

```json
{
  "id": 2,
  "externalId": "CAT-KEYBOARDS",
  "name": "Keyboards",
  "parent": {
    "id": 1,
    "externalId": "CAT-ELEC",
    "name": "Electronics",
    "parentId": null,
    "storefrontId": 1
  },
  "storefrontId": 1
}
```

| Field | Meaning |
|---|---|
| `id` | promo-x's ID of the category |
| `externalId` | Your ID of the category, unique within the storefront. Rules refer to categories by it |
| `name` | Display name |
| `parent` | The parent category, or null for a top-level category. `parent.parentId` is the parent's own parent, if any |
| `storefrontId` | The storefront it belongs to |

### `POST /category/`

- **Roles:** `employee`
- **Headers:** `X-Storefront-Id`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes, 2–50 | Display name |
| `externalId` | string | yes, 1–512 | Your ID of the category |
| `parentId` | integer | no | promo-x's ID of the parent category |

Response `201`, the category. Errors: `400`, `409` `INVALID_STOREFRONT`, `CATEGORY_ALREADY_EXISTS`,
`PARENT_CATEGORY_NOT_FOUND`.

### `PUT /category/{id}`

Replaces the category's fields, same body as create. Changing `externalId` deactivates the active rules that refer to
the old external ID.

- **Roles:** `employee`

Response `200`, the category. Errors: `400`, `404` `CATEGORY_NOT_FOUND`, `409` `CATEGORY_ALREADY_EXISTS`,
`PARENT_CATEGORY_NOT_FOUND`.

### `GET /category/{id}`

- **Roles:** `employee`, `auditor`

Response `200`, the category. Errors: `404` `CATEGORY_NOT_FOUND`.

## Products

The product object, as returned by create and update:

```json
{
  "id": 1,
  "externalId": "PROD-KB-01",
  "sku": "SKU-KB-01",
  "name": "Mechanical keyboard",
  "storefrontId": 1,
  "createdAt": "2026-11-10T21:35:46.175261Z",
  "updatedAt": "2026-11-10T21:35:46.175280Z",
  "categoryIds": [2]
}
```

| Field | Meaning |
|---|---|
| `id` | promo-x's ID of the product |
| `externalId` | Your ID of the product, unique within the storefront. Carts and rules refer to products by it |
| `sku` | The product's SKU. SKU conditions and actions match on it |
| `name` | Display name |
| `storefrontId` | The storefront it belongs to |
| `categoryIds` | promo-x's IDs of the product's categories |
| `createdAt`, `updatedAt` | Timestamps |

### `POST /product/`

- **Roles:** `employee`
- **Headers:** `X-Storefront-Id`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `externalId` | string | yes, ≤ 255 | Your ID of the product |
| `sku` | string | yes, ≤ 255 | The SKU |
| `name` | string | yes, ≤ 255 | Display name |
| `categoryIds` | integer[] | no | promo-x's IDs of its categories, each > 0 |

Response `201`, the product. Errors: `400`, `409` `INVALID_STOREFRONT`, `EXTERNAL_ID_ALREADY_EXISTS`,
`CATEGORY_IDS_NOT_FOUND`.

### `POST /product/bulk-insert`

Adds up to 1000 products at once. Either all of them are added or none.

- **Roles:** `employee`
- **Headers:** `X-Storefront-Id`

```json
{ "items": [ { "externalId": "PROD-MS-01", "sku": "SKU-MS-01", "name": "Mouse", "categoryIds": [] } ] }
```

`items` takes 1–1000 products, each like the body of `POST /product/`.

Response `204`, no body. Errors: `400`, `409` `INVALID_STOREFRONT`, `EXTERNAL_ID_VALIDATION_FAILED` (with an item per
duplicated or already existing external ID), `CATEGORY_IDS_NOT_FOUND` (with an item per product).

### `PUT /product/{id}`

Replaces the product's fields, same body as create.

- **Roles:** `admin`

Response `200`, the product. Errors: `400`, `404` `PRODUCT_NOT_FOUND`, `409`.

### `GET /product/{id}`

- **Roles:** `employee`, `auditor`

Response `200`: the product, with `categories` (the full [category objects](#categories)) instead of `categoryIds`.
Errors: `404` `PRODUCT_NOT_FOUND`.

## Storefronts

The storefront object:

```json
{ "id": 1, "name": "Main Store", "website": "https://shop.example.com" }
```

| Field | Meaning |
|---|---|
| `id` | The storefront's ID, sent as `X-Storefront-Id` |
| `name` | Display name |
| `website` | The storefront's website, or null |

### `POST /storefront/`

- **Roles:** `admin`

| Field | Type | Required | Meaning |
|---|---|---|---|
| `name` | string | yes, ≤ 50 | Display name |
| `website` | string | no, 1–255 | Website |

Response `201`, the storefront. Errors: `400`.

### `PUT /storefront/{id}`

Replaces the storefront's fields, same body as create.

- **Roles:** `admin`

Response `200`, the storefront. Errors: `400`, `404` `STOREFRONT_NOT_FOUND`.

### `GET /storefront/{id}`

- **Roles:** any authenticated caller

Response `200`, the storefront. Errors: `404` `STOREFRONT_NOT_FOUND`.

## Health

### `GET /actuator/health`

Public; no token needed.

```json
{ "status": "UP", "groups": ["liveness", "readiness"] }
```

`status` is `UP` when the application and its dependencies (database, Redis) are healthy, and the response is `503`
when one is down. Callers with a valid token also get the details of each component.
