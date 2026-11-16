import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from './layout';

@Component({
  imports: [RouterOutlet, Header],
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block min-h-screen bg-gray-50 text-gray-900' },
  template: `
    <app-header />
    <main>
      <router-outlet />
    </main>
  `,
})
export class App {}
