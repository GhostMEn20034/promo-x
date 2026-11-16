import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { authInterceptor, provideAuth } from './infrastructure/auth';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAuth({ ...environment.auth, bearerTokenUrls: [environment.apiUrl] }),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
  ],
};
