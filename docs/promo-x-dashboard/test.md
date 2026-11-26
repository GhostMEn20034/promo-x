# Testing promo-x-dashboard

The unit tests run with Vitest, in Node.js with jsdom, so no browser, Keycloak or promo-x-core is needed.

```bash
cd promo-x-dashboard
yarn test               # in a terminal: watch mode, re-runs the tests when a file changes
yarn test --watch=false # run once
```

Only the tests whose suite or test name matches a pattern, or the specs under one folder:

```bash
yarn test --watch=false --filter='usage audit'
yarn test --watch=false --include='src/app/promotion/**/*.spec.ts'
```

The specs sit next to the code they test, as `*.spec.ts`.

The tests don't cover the production build or the formatting. Check them with:

```bash
yarn build
npx prettier --check src   # --write to fix the formatting
```
