import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Pager, pagerItems } from './pager';

describe('pagerItems', () => {
  const labels = (page: number, total: number) =>
    pagerItems(page, total).map((item) => (item.kind === 'gap' ? '…' : item.page));

  it('shows every page when there are up to 7', () => {
    expect(labels(1, 2)).toEqual([1, 2]);
    expect(labels(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('shows the first, last and neighbouring pages with gaps between them', () => {
    expect(labels(1, 12)).toEqual([1, 2, '…', 12]);
    expect(labels(6, 12)).toEqual([1, '…', 5, 6, 7, '…', 12]);
    expect(labels(12, 12)).toEqual([1, '…', 11, 12]);
  });

  it('shows a single hidden page instead of a gap', () => {
    expect(labels(4, 12)).toEqual([1, 2, 3, 4, 5, '…', 12]);
  });
});

describe('Pager', () => {
  async function render(page: number, totalPages: number): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Pager);
    fixture.componentRef.setInput('page', page);
    fixture.componentRef.setInput('totalPages', totalPages);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders nothing for a single page', async () => {
    const element = await render(1, 1);

    expect(element.querySelector('nav')).toBeNull();
  });

  it('marks the current page and links the others', async () => {
    const element = await render(2, 3);

    expect(element.querySelector('[aria-current="page"]')!.textContent!.trim()).toBe('2');
    expect(element.querySelector('a[aria-label="Page 1"]')!.getAttribute('href')).toBe('/');
    expect(element.querySelector('a[aria-label="Page 3"]')!.getAttribute('href')).toBe('/?page=3');
    expect(element.querySelector('a[aria-label="Previous page"]')).toBeTruthy();
    expect(element.querySelector('a[aria-label="Next page"]')).toBeTruthy();
  });

  it('emits the picked page instead of linking when links are off', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Pager);
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('totalPages', 3);
    fixture.componentRef.setInput('links', false);
    const pageChange = vi.fn<(page: number) => void>();
    fixture.componentInstance.pageChange.subscribe(pageChange);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('a')).toBeNull();
    element.querySelector<HTMLButtonElement>('button[aria-label="Page 3"]')!.click();
    element.querySelector<HTMLButtonElement>('button[aria-label="Next page"]')!.click();

    expect(pageChange.mock.calls).toEqual([[3], [2]]);
  });

  it('has no previous link on the first page and no next link on the last', async () => {
    const element = await render(1, 2);

    expect(element.querySelector('a[aria-label="Previous page"]')).toBeNull();
    expect(element.querySelector('a[aria-label="Next page"]')).toBeTruthy();
  });
});
