import { bearerTokenCondition } from './bearer-token-condition';

describe('bearerTokenCondition', () => {
  const { urlPattern } = bearerTokenCondition('http://localhost:8000/');

  it('matches the base URL and anything under it', () => {
    expect(urlPattern.test('http://localhost:8000')).toBe(true);
    expect(urlPattern.test('http://localhost:8000/promotion/1')).toBe(true);
    expect(urlPattern.test('http://localhost:8000?page=1')).toBe(true);
  });

  it("doesn't match other hosts or ports that start with the base URL", () => {
    expect(urlPattern.test('http://localhost:80001/promotion')).toBe(false);
    expect(urlPattern.test('http://localhost:8000.example.com/promotion')).toBe(false);
    expect(urlPattern.test('http://localhost:8080/realms/promo-x')).toBe(false);
  });

  it('only matches under the base path when the base URL has one', () => {
    const { urlPattern: api } = bearerTokenCondition('http://localhost:8000/api');

    expect(api.test('http://localhost:8000/api/storefront/')).toBe(true);
    expect(api.test('http://localhost:8000/api?page=1')).toBe(true);
    expect(api.test('http://localhost:8000/apiary')).toBe(false);
    expect(api.test('http://localhost:8000/actuator/health')).toBe(false);
  });
});
