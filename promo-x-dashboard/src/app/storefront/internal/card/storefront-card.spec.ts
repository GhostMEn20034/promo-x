import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Storefront } from '../storefront.model';
import { StorefrontCard } from './storefront-card';

describe('StorefrontCard', () => {
  let fixture: ComponentFixture<StorefrontCard>;

  async function render(storefront: Storefront, canEdit = false): Promise<HTMLElement> {
    fixture = TestBed.createComponent(StorefrontCard);
    fixture.componentRef.setInput('storefront', storefront);
    fixture.componentRef.setInput('canEdit', canEdit);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('links to the storefront and shows its website', async () => {
    const element = await render({ id: 7, name: 'Tech Store', website: 'techstore.com' });

    const links = element.querySelectorAll('a');
    expect(links[0].getAttribute('href')).toBe('/storefronts/7');
    expect(links[0].textContent?.trim()).toBe('Tech Store');
    expect(links[1].getAttribute('href')).toBe('https://techstore.com');
    expect(links[1].getAttribute('target')).toBe('_blank');
    expect(links[1].querySelector('svg')).toBeTruthy();
  });

  it('says when there is no website', async () => {
    const element = await render({ id: 7, name: 'Tech Store', website: null });

    expect(element.querySelectorAll('a')).toHaveLength(1);
    expect(element.textContent).toContain('No website');
  });

  it('hides the edit button unless editing is allowed', async () => {
    const element = await render({ id: 7, name: 'Tech Store', website: null });

    expect(element.querySelector('button')).toBeNull();
  });

  it('emits edit when the pencil is clicked', async () => {
    const element = await render({ id: 7, name: 'Tech Store', website: null }, true);
    const edit = vi.fn();
    fixture.componentInstance.edit.subscribe(edit);

    const button = element.querySelector('button')!;
    expect(button.getAttribute('aria-label')).toBe('Edit Tech Store');
    button.click();

    expect(edit).toHaveBeenCalledTimes(1);
  });
});
