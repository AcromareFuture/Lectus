import test from 'node:test';
import assert from 'node:assert/strict';
import { localPreviewPrices } from './web-prices.js';
import { previewPrice } from './web-pricing.js';
import { featuredEditions } from './web-featured.js';

test('current catalog and editorial editions have positive CNY prices in integer cents', () => {
  const ids = Object.keys(localPreviewPrices);
  assert.equal(ids.filter(id => id.startsWith('SPU-')).length, 250);
  assert.equal(ids.length, 253);
  for (const id of ids) {
    const price = previewPrice(id);
    assert.ok(Number.isSafeInteger(price.cents) && price.cents > 0, id);
    assert.equal(price.cents % 100, 0, id);
    assert.equal(price.currency, 'CNY');
    assert.match(price.formatted, /^[¥￥][\d,]+$/);
  }
  for (const product of featuredEditions) assert.ok(previewPrice(product.id));
});

test('prices use stable IDs across summary, detail, and saved records', () => {
  const summary = { id: 'SPU-F6F1C6C33124', colorwayCount: 2, priceCents: null };
  const detail = { id: summary.id, skus: [{ priceCents: null }] };
  assert.deepEqual(previewPrice(summary.id), previewPrice(detail.id));
  assert.equal(previewPrice(summary.id).cents, 79900);
  assert.equal(previewPrice('wp-kobe').formatted, '¥1,499');
  assert.equal(previewPrice('edit-asics').cents, previewPrice('SPU-234E824E28E2').cents);
});

test('unknown products cannot inherit prices by name or object prototype', () => {
  for (const id of ['Samba OG', 'new-product', '__proto__', 'constructor', '', null, undefined]) {
    assert.equal(previewPrice(id), null);
  }
  assert.ok(Object.isFrozen(localPreviewPrices));
});
