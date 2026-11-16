import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthApi, AuthUser } from '../../infrastructure/auth';
import { Header } from './header';

describe('Header', () => {
  const user = signal<AuthUser | null>(null);
  const auth = {
    user,
    login: vi.fn(() => Promise.resolve()),
    logout: vi.fn(() => Promise.resolve()),
  };

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(element: HTMLElement, text: string): HTMLButtonElement | undefined {
    return [...element.querySelectorAll('button')].find((b) => b.textContent?.trim() === text);
  }

  beforeEach(() => {
    user.set(null);
    auth.login.mockClear();
    auth.logout.mockClear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthApi, useValue: auth }],
    });
  });

  it("shows the signed-in user's name and email, and signs out", async () => {
    user.set({ id: '1', username: 'jdoe', name: 'John Doe', email: 'jdoe@example.com' });
    const element = await render();

    expect(element.textContent).toContain('John Doe');
    expect(element.textContent).toContain('jdoe@example.com');

    button(element, 'Sign out')!.click();
    expect(auth.logout).toHaveBeenCalled();
  });

  it('falls back to the username when the token has no name', async () => {
    user.set({ id: '1', username: 'jdoe' });
    const element = await render();

    expect(element.textContent).toContain('jdoe');
  });

  it('offers to sign in when nobody is signed in', async () => {
    const element = await render();

    expect(button(element, 'Sign out')).toBeUndefined();
    button(element, 'Sign in')!.click();
    expect(auth.login).toHaveBeenCalled();
  });
});
