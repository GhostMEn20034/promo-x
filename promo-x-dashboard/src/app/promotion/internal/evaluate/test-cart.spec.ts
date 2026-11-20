import { cartErrors, curlCommand, EMPTY_CART, TestCart, toRequest } from './test-cart';

describe('test cart', () => {
  const keyboard = { id: 1, externalId: 'PROD-KB-01', sku: 'SKU-KB-01', name: 'Keyboard' };
  const mouse = { id: 2, externalId: 'PROD-MS-01', sku: 'SKU-MS-01', name: 'Mouse' };
  const cart: TestCart = {
    lines: [
      { product: keyboard, quantity: '2', unitPrice: '100.00' },
      { product: mouse, quantity: '1', unitPrice: '25' },
    ],
    promoCodes: ['SUMMER'],
    customerId: ' customer-42 ',
    sessionId: '',
    shippingCost: '10',
  };

  it('builds the request, counting uses right away', () => {
    expect(toRequest(cart)).toEqual({
      customerId: 'customer-42',
      promoCodes: ['SUMMER'],
      items: [
        { productExternalId: 'PROD-KB-01', quantity: 2, unitPrice: 100 },
        { productExternalId: 'PROD-MS-01', quantity: 1, unitPrice: 25 },
      ],
      shippingCost: 10,
      reserveUsage: false,
    });
  });

  it('leaves out a blank customer, for an anonymous guest', () => {
    expect('customerId' in toRequest({ ...cart, customerId: '  ' })).toBe(false);
  });

  it('finds what promo-x-core would reject', () => {
    expect(cartErrors(cart, 128)).toBeNull();
    expect(cartErrors(EMPTY_CART, 128)!.cart).toBe('Add at least one product.');
    expect(cartErrors(cart, 1)!.cart).toBe('At most 1 products.');

    const errors = cartErrors(
      {
        ...cart,
        lines: [{ product: keyboard, quantity: '0', unitPrice: '' }],
        shippingCost: '-1',
      },
      128,
    )!;
    expect(errors.lines.get(1)).toEqual({
      quantity: 'A whole number, 1 or more.',
      unitPrice: 'Unit price is required.',
    });
    expect(errors.shippingCost).toBe('A number, 0 or more.');
  });

  it('writes a curl command for the request, quoting it for the shell', () => {
    const command = curlCommand('http://localhost:8000/', 3, {
      ...toRequest(cart),
      promoCodes: ["IT'S-ON"],
    });

    expect(command.split('\n').slice(0, 4)).toEqual([
      "curl -X POST 'http://localhost:8000/api/promotion/evaluate' \\",
      '  -H "Authorization: Bearer $TOKEN" \\',
      "  -H 'X-Storefront-Id: 3' \\",
      "  -H 'Content-Type: application/json' \\",
    ]);
    expect(command).toContain(`"IT'\\''S-ON"`);
  });
});
