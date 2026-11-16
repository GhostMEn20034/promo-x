import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../infrastructure/auth';

/** The top bar: the app's name, and the signed-in user with a sign-out button. */
@Component({
  selector: 'app-header',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="border-b border-gray-200 bg-white">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-6 py-3">
        <a class="text-lg font-semibold text-gray-900" routerLink="/">promo-x</a>

        @if (auth.user(); as user) {
          <div class="flex min-w-0 items-center gap-4">
            <div class="min-w-0 text-right text-sm leading-tight">
              <p class="truncate font-medium text-gray-900">{{ user.name ?? user.username }}</p>
              @if (user.email) {
                <p class="truncate text-gray-500">{{ user.email }}</p>
              }
            </div>
            <button
              type="button"
              class="shrink-0 rounded-md border border-gray-300 px-3 py-1.5 text-sm hover:bg-gray-50"
              (click)="auth.logout()"
            >
              Sign out
            </button>
          </div>
        } @else {
          <button
            type="button"
            class="rounded-md border border-gray-300 px-3 py-1.5 text-sm hover:bg-gray-50"
            (click)="auth.login()"
          >
            Sign in
          </button>
        }
      </div>
    </header>
  `,
})
export class Header {
  protected readonly auth = inject(AuthApi);
}
