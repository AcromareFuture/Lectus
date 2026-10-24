import test from 'node:test';
import assert from 'node:assert/strict';
import { readWebRoute, webHref, patchWebFilters } from './web-route.js';
test('deep links round-trip Chinese filters and reserved characters without changing identity', () => {
  const href = webHref('discover', { category: '鞋类', q: 'A & B #1 / 2?', brand: 'BR-1', page: 2 });
  const route = readWebRoute(href);
  assert.equal(route.category, '鞋类'); assert.equal(route.q, 'A & B #1 / 2?'); assert.equal(route.page, 2); assert.equal(route.brand, 'BR-1');
});
test('brand changes preserve search and category while resetting pagination', () => {
  const route = readWebRoute('#/discover?category=鞋类&q=Nike&page=5');
  const next = readWebRoute(patchWebFilters(route, { brand: 'BR-NIKE' }));
  assert.equal(next.page, 0); assert.equal(next.q, 'Nike'); assert.equal(next.category, '鞋类'); assert.equal(next.brand, 'BR-NIKE');
});
test('unrecognized legacy anchors arrive at home and page input is bounded', () => {
  assert.equal(readWebRoute('#home').path, 'home'); assert.equal(readWebRoute('#unknown').path, 'home');
  for (const value of ['-4', 'NaN', '-Infinity']) assert.equal(readWebRoute(`#/discover?page=${value}`).page, 0);
  assert.equal(readWebRoute('#/discover?page=999999').page, 999);
});
test('exact product identifiers survive links and empty filters are omitted', () => {
  const href = webHref('product', { id: 'SPU-A&B', category: '', brand: null });
  assert.equal(readWebRoute(href).id, 'SPU-A&B'); assert.ok(!href.includes('category')); assert.ok(!href.includes('brand'));
});


test('legacy scan and checkout routes lead into the unified product flow', () => {
  assert.equal(readWebRoute('#/checkout?product=SPU-000FE86E738A').path, 'product');
  assert.equal(readWebRoute('#/checkout?product=SPU-000FE86E738A').id, 'SPU-000FE86E738A');
  assert.equal(readWebRoute('#/scan?token=expired').path, 'home');
});

test('mall bookmarks retain their filters on the unified product homepage', () => {
 const route = readWebRoute('#/mall?category=鞋类&page=2');
 assert.equal(route.path, 'home'); assert.equal(route.category, '鞋类'); assert.equal(route.page, 2);
 assert.equal(webHref('mall'), '#/home');
});
