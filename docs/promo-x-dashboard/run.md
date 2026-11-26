# Running promo-x-dashboard

promo-x-dashboard is the staff web app for promo-x-core, written in Angular. It needs Keycloak and promo-x-core
running, as described in [Running promo-x](../run.md).

## Prerequisites

- Node.js and Yarn 1 (the project pins `yarn@1.22.22` in `package.json`).
- Keycloak with the dashboard's client, below.
- promo-x-core on http://localhost:8000 with the `dev` profile, which allows the dashboard's origin
  (http://localhost:4200) through CORS. See [Running promo-x-core](../promo-x-core/run.md).

## Keycloak client

The dashboard logs in with the authorization code flow and PKCE, so the user signs in on Keycloak's page before the
app opens. In the realm promo-x-core trusts (`promo-x-dev` by default), create a client:

| Setting | Value |
|---|---|
| Client ID | `promo-x-web` |
| Client authentication | Off (a public client) |
| Authentication flow | Standard flow |
| Valid redirect URIs | `http://localhost:4200/*` |
| Valid post logout redirect URIs | `http://localhost:4200/*` |
| Web origins | `http://localhost:4200` |
| PKCE method (Advanced) | `S256` |

The access token needs the user's realm roles in a top-level `roles` claim, the same mapper promo-x-core needs.
Give each user the roles of what they work on: `promotion-manager`, `employee`, `auditor` or `admin`. The dashboard
hides what a user's roles don't allow, and promo-x-core enforces them.

## Run

```bash
cd promo-x-dashboard
yarn install
yarn start
```

The dashboard runs on http://localhost:4200 and reloads when a source file changes. It redirects to Keycloak to log
in first.

## Configuration

The URLs are compiled into the app, from the environment files in `src/environments/`:

| File | Used by |
|---|---|
| `environment.development.ts` | `yarn start` |
| `environment.ts` | `yarn build`, the production build |

| Setting | Default | Meaning |
|---|---|---|
| `apiUrl` | `http://localhost:8000` | promo-x-core's base URL. Only requests to it get the user's token |
| `auth.url` | `http://localhost:8080` | Keycloak's base URL |
| `auth.realm` | `promo-x-dev` | The realm |
| `auth.clientId` | `promo-x-web` | The client above |

## Build

```bash
yarn build
```

The production build goes to `dist/promo-x-dashboard/browser/`: static files any web server can serve. Edit
`environment.ts` for the target environment before building, since the URLs are part of the build. The server must
answer every path the app doesn't have as a file with `index.html`, so a page opened by its URL, such as a
storefront's, loads the app. Add the dashboard's URL to the client's redirect URIs and web origins in Keycloak, and
to promo-x-core's CORS origins (`app.cors.allowed-origins`).
