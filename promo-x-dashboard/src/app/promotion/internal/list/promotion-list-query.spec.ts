import {
  hasFilters,
  hasInvalidRange,
  PromotionListQuery,
  readQuery,
  toApiParams,
  toUrlParams,
} from './promotion-list-query';

describe('promotion list query', () => {
  const defaults: PromotionListQuery = {
    page: 1,
    sort: 'createdAt,desc',
    isActive: null,
    isShortCircuit: null,
    createdFrom: null,
    createdTo: null,
  };

  describe('readQuery', () => {
    it('defaults to the first page, newest first, without filters', () => {
      expect(readQuery({})).toEqual(defaults);
    });

    it('reads the filters, sort and page from the URL', () => {
      expect(
        readQuery({
          page: '3',
          sort: 'priority,desc',
          active: 'true',
          shortCircuit: 'false',
          createdFrom: '2026-11-01',
          createdTo: '2026-11-30',
        }),
      ).toEqual({
        page: 3,
        sort: 'priority,desc',
        isActive: true,
        isShortCircuit: false,
        createdFrom: '2026-11-01',
        createdTo: '2026-11-30',
      });
    });

    it('falls back to the defaults for values that are not valid', () => {
      expect(
        readQuery({
          page: '0',
          sort: 'name,asc',
          active: 'yes',
          shortCircuit: '1',
          createdFrom: '2026-02-30',
          createdTo: '30.11.2026',
        }),
      ).toEqual(defaults);
    });
  });

  describe('toUrlParams', () => {
    it('removes the parameters that are at their defaults', () => {
      expect(toUrlParams(defaults)).toEqual({
        page: null,
        sort: null,
        active: null,
        shortCircuit: null,
        createdFrom: null,
        createdTo: null,
      });
    });

    it('keeps the rest', () => {
      expect(
        toUrlParams({
          ...defaults,
          page: 2,
          sort: 'updatedAt,desc',
          isActive: false,
          isShortCircuit: true,
          createdFrom: '2026-11-01',
        }),
      ).toEqual({
        page: 2,
        sort: 'updatedAt,desc',
        active: 'false',
        shortCircuit: 'true',
        createdFrom: '2026-11-01',
        createdTo: null,
      });
    });
  });

  describe('toApiParams', () => {
    it('always sends the page, size and sort', () => {
      expect(toApiParams(defaults, 20)).toEqual({ page: 1, size: 20, sort: 'createdAt,desc' });
    });

    it('sends the filters, with the days as timestamps that include both whole days', () => {
      expect(
        toApiParams(
          {
            ...defaults,
            isActive: false,
            isShortCircuit: true,
            createdFrom: '2026-11-01',
            createdTo: '2026-11-30',
          },
          20,
        ),
      ).toEqual({
        page: 1,
        size: 20,
        sort: 'createdAt,desc',
        isActive: false,
        isShortCircuit: true,
        createdFrom: '2026-11-01T00:00:00Z',
        createdTo: '2026-11-30T23:59:59.999999Z',
      });
    });
  });

  it('knows when filters are set and when the range is backwards', () => {
    expect(hasFilters(defaults)).toBe(false);
    expect(hasFilters({ ...defaults, sort: 'priority,asc', page: 4 })).toBe(false);
    expect(hasFilters({ ...defaults, isShortCircuit: false })).toBe(true);

    expect(
      hasInvalidRange({ ...defaults, createdFrom: '2026-11-02', createdTo: '2026-11-01' }),
    ).toBe(true);
    expect(
      hasInvalidRange({ ...defaults, createdFrom: '2026-11-01', createdTo: '2026-11-01' }),
    ).toBe(false);
    expect(hasInvalidRange({ ...defaults, createdFrom: '2026-11-02' })).toBe(false);
  });
});
