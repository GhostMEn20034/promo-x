// Public API of the auth module. Other modules import from here only, never from `internal/`.
export { AuthApi, type AuthUser } from './auth.api';
export { type AuthConfig } from './auth.config';
export { authInterceptor } from './auth.interceptor';
export { authRoutes, FORBIDDEN_PATH } from './auth.routes';
export { provideAuth } from './provide-auth';
export { Role } from './role';
export { roleGuard } from './role.guard';
