import { InjectionToken } from '@angular/core';

/** promo-x-core's base URL, e.g. `http://localhost:8000`, without a trailing slash. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');
