import { InjectionToken } from '@angular/core';

/**
 * promo-x-core's base URL, e.g. `http://localhost:8000`. Clients append the endpoint paths exactly as
 * `docs/promo-x-core/api.md` writes them, such as `/api/storefront/{id}`.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');
