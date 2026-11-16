import { HttpInterceptorFn } from '@angular/common/http';
import { includeBearerTokenInterceptor } from 'keycloak-angular';

/**
 * Adds `Authorization: Bearer <access token>` to requests to the URLs in `AuthConfig.bearerTokenUrls`, refreshing
 * the token first when it is about to expire. Other requests are passed through untouched.
 */
export const authInterceptor: HttpInterceptorFn = includeBearerTokenInterceptor;
