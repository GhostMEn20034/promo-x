import { parseProductCsv } from './product-csv';

describe('parseProductCsv', () => {
  function rows(csv: string) {
    const parsed = parseProductCsv(csv);
    if (!parsed.ok) {
      throw new Error(parsed.error);
    }
    return parsed.rows;
  }

  function error(csv: string): string {
    const parsed = parseProductCsv(csv);
    if (parsed.ok) {
      throw new Error('Expected the CSV to be rejected');
    }
    return parsed.error;
  }

  it('reads the products with their category IDs and line numbers', () => {
    const parsed = rows(
      'externalId,sku,name,categoryIds\nKB-1,SKU-KB,Keyboard,2;5\nMS-1,SKU-MS,Mouse,\n',
    );

    expect(parsed).toEqual([
      {
        line: 2,
        input: { externalId: 'KB-1', sku: 'SKU-KB', name: 'Keyboard', categoryIds: [2, 5] },
        errors: {},
      },
      {
        line: 3,
        input: { externalId: 'MS-1', sku: 'SKU-MS', name: 'Mouse', categoryIds: [] },
        errors: {},
      },
    ]);
  });

  it('takes the columns in any order and letter case, without categoryIds', () => {
    const [row] = rows('NAME,SkU,EXTERNALID\nKeyboard,SKU-KB,KB-1');

    expect(row.input).toEqual({
      externalId: 'KB-1',
      sku: 'SKU-KB',
      name: 'Keyboard',
      categoryIds: [],
    });
  });

  it('reads quoted fields with commas, quotes and line breaks, and CRLF line endings', () => {
    const parsed = rows(
      '﻿externalId,sku,name\r\nKB-1,SKU-KB,"Keyboard, ""mechanical"""\r\nMS-1,SKU-MS,"Mouse\nwireless"\r\nPD-1,SKU-PD,Pad',
    );

    expect(parsed.map((row) => row.input.name)).toEqual([
      'Keyboard, "mechanical"',
      'Mouse\nwireless',
      'Pad',
    ]);
    expect(parsed.map((row) => row.line)).toEqual([2, 3, 5]);
  });

  it('accepts category IDs separated by semicolons or spaces, without repeats', () => {
    const [row] = rows('externalId,sku,name,categoryIds\nKB-1,SKU-KB,Keyboard,"2; 5 2"');

    expect(row.input.categoryIds).toEqual([2, 5]);
  });

  it('flags missing, too long and invalid values on their fields', () => {
    const [row] = rows(`externalId,sku,name,categoryIds\n,${'S'.repeat(256)},Keyboard,2;abc;0`);

    expect(row.errors).toEqual({
      externalId: 'Required.',
      sku: 'At most 255 characters.',
      categoryIds: 'Category IDs must be positive whole numbers.',
    });
  });

  it('flags every repeat of an external ID after the first', () => {
    const parsed = rows('externalId,sku,name\nKB-1,A,One\nKB-1,B,Two\nMS-1,C,Three\nKB-1,D,Four');

    expect(parsed.map((row) => row.errors.externalId)).toEqual([
      undefined,
      'Duplicates line 2.',
      undefined,
      'Duplicates line 2.',
    ]);
  });

  it('skips blank lines', () => {
    expect(rows('externalId,sku,name\n\nKB-1,A,One\n  \n')).toHaveLength(1);
  });

  it('rejects a CSV without products or without the required columns', () => {
    expect(error('')).toBe('The CSV is empty.');
    expect(error('externalId,sku,name')).toBe('The CSV has a header row but no products.');
    expect(error('externalId,title\nKB-1,Keyboard')).toBe('The header row is missing: sku, name.');
  });

  it('rejects more than 1000 products', () => {
    const body = Array.from({ length: 1001 }, (_, i) => `P-${i},S-${i},Product ${i}`).join('\n');

    expect(error(`externalId,sku,name\n${body}`)).toBe(
      'At most 1000 products can be imported at once; this CSV has 1001.',
    );
  });
});
