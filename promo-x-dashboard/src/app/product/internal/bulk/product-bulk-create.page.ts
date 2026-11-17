import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  DOCUMENT,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { problemDetailOf } from '../../../infrastructure/http';
import { ProductClient } from '../product.client';
import { PRODUCT_BULK_MAX_ITEMS } from '../product.model';
import { BulkField, BulkRow, parseProductCsv } from './product-csv';

type RowErrors = Partial<Record<BulkField, string>>;

/** promo-x-core reports a bulk item's problems at `items[<index>].<field>` (or `items.[<index>].<field>`). */
const ITEM_PATH = /^items\.?\[(\d+)]\.?(externalId|sku|name|categoryIds)/;

const FIELD_LABELS: Record<BulkField, string> = {
  externalId: 'External ID',
  sku: 'SKU',
  name: 'Name',
  categoryIds: 'Category IDs',
};

/** Shown on the page, downloadable and usable as a starting point. Each row shows one case of the format. */
const EXAMPLE = `externalId,sku,name,categoryIds
PROD-KB-01,SKU-KB-01,Mechanical keyboard,2;5
PROD-MS-01,SKU-MS-01,"Mouse, wireless",2
PROD-PAD-01,SKU-PAD-01,Desk pad,
`;
const EXAMPLE_FILE_NAME = 'products-import-example.csv';

/**
 * Imports many products at once from CSV: paste it or upload a file, check the rows, then send them in one
 * `POST /api/product/bulk-insert`, which adds all of them or none.
 */
@Component({
  selector: 'app-product-bulk-create-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-bulk-create.page.html',
})
export class ProductBulkCreatePage {
  /** The parent route's `:storefrontId` parameter. */
  readonly storefrontId = input.required<number, unknown>({ transform: numberAttribute });

  private readonly client = inject(ProductClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  protected readonly example = EXAMPLE;
  protected readonly exampleFileName = EXAMPLE_FILE_NAME;
  protected readonly maxItems = PRODUCT_BULK_MAX_ITEMS;
  protected readonly text = signal('');
  protected readonly fileName = signal<string | null>(null);
  protected readonly rows = signal<readonly BulkRow[] | null>(null);
  protected readonly parseError = signal<string | null>(null);
  /** The backend's problems, by row index. */
  protected readonly serverErrors = signal<ReadonlyMap<number, RowErrors>>(new Map());
  protected readonly formError = signal<string | null>(null);
  protected readonly onlyProblems = signal(false);
  protected readonly importing = signal(false);
  /** How many products the last import added. */
  protected readonly imported = signal<number | null>(null);

  /** Each row with its own problems and the backend's, by index. */
  protected readonly checkedRows = computed(() =>
    (this.rows() ?? []).map((row, index) => ({
      index,
      row,
      errors: { ...row.errors, ...this.serverErrors().get(index) } as RowErrors,
    })),
  );
  protected readonly problemCount = computed(
    () => this.checkedRows().filter((item) => Object.keys(item.errors).length > 0).length,
  );
  protected readonly visibleRows = computed(() =>
    this.onlyProblems()
      ? this.checkedRows().filter((item) => Object.keys(item.errors).length > 0)
      : this.checkedRows(),
  );
  /** The browser's own checks must pass; the backend's problems clear once the CSV is checked again. */
  protected readonly canImport = computed(() => {
    const rows = this.rows();
    return (
      rows !== null &&
      rows.length > 0 &&
      rows.every((row) => Object.keys(row.errors).length === 0) &&
      this.serverErrors().size === 0 &&
      !this.importing()
    );
  });

  protected onText(text: string): void {
    this.text.set(text);
    this.fileName.set(null);
    this.clearResults();
  }

  protected async onFile(input: HTMLInputElement): Promise<void> {
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.text.set(await file.text());
    this.fileName.set(file.name);
    this.clearResults();
    this.check();
  }

  /** Puts the example in the editor and checks it, to see how the rows are read. */
  protected useExample(): void {
    this.text.set(EXAMPLE);
    this.fileName.set(null);
    this.check();
  }

  /** Saves the example as a CSV file, to fill in with real products. */
  protected downloadExample(): void {
    const url = URL.createObjectURL(new Blob([EXAMPLE], { type: 'text/csv;charset=utf-8' }));
    const link = this.document.createElement('a');
    link.href = url;
    link.download = EXAMPLE_FILE_NAME;
    link.click();
    URL.revokeObjectURL(url);
  }

  protected check(): void {
    this.clearResults();
    const parsed = parseProductCsv(this.text());
    if (parsed.ok) {
      this.rows.set(parsed.rows);
    } else {
      this.parseError.set(parsed.error);
    }
  }

  protected submit(): void {
    const rows = this.rows();
    if (!rows || !this.canImport()) {
      return;
    }

    this.importing.set(true);
    this.formError.set(null);
    this.client
      .createMany(
        this.storefrontId(),
        rows.map((row) => row.input),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.importing.set(false);
          this.imported.set(rows.length);
          this.text.set('');
          this.fileName.set(null);
          this.clearResults();
        },
        error: (error: unknown) => {
          this.importing.set(false);
          this.showError(error);
        },
      });
  }

  protected startOver(): void {
    this.imported.set(null);
  }

  /** A row's problems, each prefixed with its column. */
  protected errorList(errors: RowErrors): string[] {
    return (Object.keys(FIELD_LABELS) as BulkField[]).flatMap((field) => {
      const message = errors[field];
      return message ? [`${FIELD_LABELS[field]}: ${message}`] : [];
    });
  }

  private clearResults(): void {
    this.rows.set(null);
    this.parseError.set(null);
    this.serverErrors.set(new Map());
    this.formError.set(null);
    this.onlyProblems.set(false);
  }

  private showError(error: unknown): void {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 403) {
      this.formError.set("You're not allowed to create products.");
      return;
    }

    const problem = problemDetailOf(error);
    if (!problem) {
      this.formError.set("Couldn't import the products. Nothing was added; try again.");
      return;
    }

    const byRow = new Map<number, RowErrors>();
    const unmatched: string[] = [];
    for (const item of problem.errors) {
      const match = item.path ? ITEM_PATH.exec(item.path) : null;
      if (match) {
        const index = Number(match[1]);
        const field = match[2] as BulkField;
        byRow.set(index, { ...byRow.get(index), [field]: item.message });
      } else {
        unmatched.push(item.message);
      }
    }
    this.serverErrors.set(byRow);
    this.onlyProblems.set(byRow.size > 0);
    this.formError.set(
      [
        'Nothing was imported.',
        byRow.size > 0 ? `Fix the ${byRow.size} rows with problems and check the CSV again.` : '',
        ...unmatched,
      ]
        .filter(Boolean)
        .join(' '),
    );
  }
}
