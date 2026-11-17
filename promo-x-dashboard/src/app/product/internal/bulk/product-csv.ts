import { PRODUCT_BULK_MAX_ITEMS, PRODUCT_FIELD_MAX_LENGTH, ProductInput } from '../product.model';

/** A field of a bulk row, as named by promo-x-core's `items[<index>].<field>` error paths. */
export type BulkField = keyof ProductInput;

/** One CSV row, with the problems found in it before sending. */
export interface BulkRow {
  /** The row's line in the CSV, counting the header as line 1. */
  readonly line: number;
  readonly input: ProductInput;
  readonly errors: Partial<Record<BulkField, string>>;
}

export type ParsedCsv =
  | { readonly ok: true; readonly rows: readonly BulkRow[] }
  | { readonly ok: false; readonly error: string };

const COLUMNS: Record<string, BulkField> = {
  externalid: 'externalId',
  sku: 'sku',
  name: 'name',
  categoryids: 'categoryIds',
};
const REQUIRED: readonly BulkField[] = ['externalId', 'sku', 'name'];

/**
 * Parses products from CSV with a header row: `externalId,sku,name,categoryIds`, in any order and any letter case.
 * `categoryIds` is optional, with the IDs separated by `;` or spaces. Fields may be quoted (`"Keyboard, mechanical"`,
 * with `""` for a quote). Blank lines are skipped.
 *
 * Each row is checked the way promo-x-core checks it, so problems show before anything is sent.
 */
export function parseProductCsv(text: string): ParsedCsv {
  const records = parseCsvRecords(text.replace(/^﻿/, ''));
  const [header, ...body] = records.filter((record) => record.cells.some((cell) => cell.trim()));
  if (!header) {
    return { ok: false, error: 'The CSV is empty.' };
  }

  const columns = header.cells.map((cell) => COLUMNS[cell.trim().toLowerCase()]);
  const missing = REQUIRED.filter((field) => !columns.includes(field));
  if (missing.length > 0) {
    return { ok: false, error: `The header row is missing: ${missing.join(', ')}.` };
  }
  if (body.length === 0) {
    return { ok: false, error: 'The CSV has a header row but no products.' };
  }
  if (body.length > PRODUCT_BULK_MAX_ITEMS) {
    return {
      ok: false,
      error: `At most ${PRODUCT_BULK_MAX_ITEMS} products can be imported at once; this CSV has ${body.length}.`,
    };
  }

  const rows = body.map((record) => toRow(record, columns));
  return { ok: true, rows: flagDuplicateExternalIds(rows) };
}

function toRow(record: CsvRecord, columns: readonly (BulkField | undefined)[]): BulkRow {
  const value = (field: BulkField) => {
    const index = columns.indexOf(field);
    return index === -1 ? '' : (record.cells[index] ?? '').trim();
  };
  const errors: Partial<Record<BulkField, string>> = {};

  for (const field of REQUIRED) {
    const text = value(field);
    if (text === '') {
      errors[field] = 'Required.';
    } else if (text.length > PRODUCT_FIELD_MAX_LENGTH) {
      errors[field] = `At most ${PRODUCT_FIELD_MAX_LENGTH} characters.`;
    }
  }

  const rawIds = value('categoryIds')
    .split(/[;\s]+/)
    .filter(Boolean);
  const categoryIds = [...new Set(rawIds.filter(isPositiveInteger).map(Number))];
  if (rawIds.some((id) => !isPositiveInteger(id))) {
    errors.categoryIds = 'Category IDs must be positive whole numbers.';
  }

  return {
    line: record.line,
    input: { externalId: value('externalId'), sku: value('sku'), name: value('name'), categoryIds },
    errors,
  };
}

/** Like promo-x-core's DUPLICATE_EXTERNAL_ID_IN_BATCH: every repeat after the first is flagged. */
function flagDuplicateExternalIds(rows: readonly BulkRow[]): BulkRow[] {
  const firstLine = new Map<string, number>();
  return rows.map((row) => {
    const externalId = row.input.externalId;
    if (externalId === '' || row.errors.externalId) {
      return row;
    }
    const first = firstLine.get(externalId);
    if (first === undefined) {
      firstLine.set(externalId, row.line);
      return row;
    }
    return { ...row, errors: { ...row.errors, externalId: `Duplicates line ${first}.` } };
  });
}

function isPositiveInteger(text: string): boolean {
  return /^[1-9]\d*$/.test(text) && Number.isSafeInteger(Number(text));
}

interface CsvRecord {
  readonly line: number;
  readonly cells: readonly string[];
}

/** RFC 4180 records: commas between cells, quoted cells may hold commas, quotes (`""`) and line breaks. */
function parseCsvRecords(text: string): CsvRecord[] {
  const records: CsvRecord[] = [];
  let cells: string[] = [];
  let cell = '';
  let quoted = false;
  let line = 1;
  let recordLine = 1;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
      } else {
        if (char === '\n') {
          line++;
        }
        cell += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === ',') {
      cells.push(cell);
      cell = '';
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') {
        i++;
      }
      cells.push(cell);
      records.push({ line: recordLine, cells });
      cells = [];
      cell = '';
      line++;
      recordLine = line;
    } else {
      cell += char;
    }
  }
  if (cell !== '' || cells.length > 0) {
    cells.push(cell);
    records.push({ line: recordLine, cells });
  }
  return records;
}
