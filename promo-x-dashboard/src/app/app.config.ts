import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { authInterceptor, provideAuth } from './infrastructure/auth';
import { API_BASE_URL } from './infrastructure/http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAuth({ ...environment.auth, bearerTokenUrls: [environment.apiUrl] }),
    provideHttpClient(withInterceptors([authInterceptor])),
    { provide: API_BASE_URL, useValue: environment.apiUrl },
    provideRouter(routes, withComponentInputBinding()),
  ],
};
