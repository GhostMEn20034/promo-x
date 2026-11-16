import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthApi } from '../../auth.api';

@Component({
  selector: 'app-forbidden-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen items-center justify-center p-6">
      <section class="max-w-md text-center">
        <h1 class="text-2xl font-semibold">Access denied</h1>
        <p class="mt-3 text-gray-600">
          @if (auth.user(); as user) {
            {{ user.username }} doesn't have a role that allows this page.
          } @else {
            You don't have a role that allows this page.
          }
          Ask an administrator for access, or sign in with another account.
        </p>
        <button
          type="button"
          class="mt-6 rounded-md border border-gray-300 px-4 py-2 hover:bg-gray-100"
          (click)="auth.logout()"
        >
          Sign out
        </button>
      </section>
    </main>
  `,
})
export class ForbiddenPage {
  protected readonly auth = inject(AuthApi);
}
