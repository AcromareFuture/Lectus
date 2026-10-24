import test from 'node:test';
import assert from 'node:assert/strict';
import { getFeaturedEdition, featuredEditions } from './web-featured.js';
import { getMeshyPreviewModel } from './meshy-preview.js';
import { readWebRoute } from './web-route.js';

test('display-edition models never bind to similarly named imported catalog products', () => {
  for (const id of ['SPU-F6F1C6C33124', 'Samba OG', 'adidas', '', null]) {
    assert.equal(getFeaturedEdition(id), null);
    assert.equal(getMeshyPreviewModel(id, { enabled: true }), null);
  }
  for (const product of featuredEditions) assert.ok(getMeshyPreviewModel(product.id, { enabled: true }));
  assert.notEqual(getMeshyPreviewModel('wp-kobe', { enabled: true }).src, getMeshyPreviewModel('wp-samba', { enabled: true }).src);
});

test('old studio bookmarks open the corresponding product gallery in 3D mode', () => {
  for (const id of ['wp-kobe', 'wp-samba']) {
    const route = readWebRoute(`#/studio?id=${id}`);
    assert.equal(route.path, 'product');
    assert.equal(route.id, id);
    assert.equal(route.params.get('view'), '3d');
  }
});
