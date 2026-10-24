import test from 'node:test';
import assert from 'node:assert/strict';
import { matchingSkus, exactCatalogModel } from './catalog-api.js';
import { getMeshyPreviewModel } from './meshy-preview.js';

test('published previews require opt-in, an exact shoe, and base-relative hashed assets', () => {
  assert.equal(getMeshyPreviewModel('wp-kobe'), null);
  assert.equal(getMeshyPreviewModel('wp-samba'), null);
  assert.equal(getMeshyPreviewModel('wp-asics', { enabled: true }), null);
  assert.equal(getMeshyPreviewModel('SPU-234E824E28E2', { enabled: true }), null);
  assert.equal(getMeshyPreviewModel('toString', { enabled: true }), null);
  const kobe = getMeshyPreviewModel('wp-kobe', { enabled: true });
  const samba = getMeshyPreviewModel('wp-samba', { enabled: true });
  assert.equal(kobe.experimental, true);
  assert.notEqual(kobe.src, samba.src);
  for (const model of [kobe, samba]) {
    assert.match(model.src, /^models\/previews\/[a-f0-9]{64}\.glb$/);
    assert.equal(new URL(model.src, 'https://example.test/lectus/').pathname, `/lectus/${model.src}`);
  }
});

test('only real variant combinations match multi-dimensional selections', () => {
  const skus = [
    { id: 'one', specs: { Size: '7', Version: 'Wide' } },
    { id: 'two', specs: { Size: '8', Version: 'Regular' } },
    { id: 'missing', specs: {} },
  ];
  assert.deepEqual(matchingSkus(skus, { Size: '7', Version: 'Regular' }), []);
  assert.deepEqual(matchingSkus(skus, { Size: '8' }).map(s => s.id), ['two']);
  assert.equal(matchingSkus(skus, {}).length, 3);
});

test('models are rejected when they belong to a different product or colorway', () => {
  const product = { id: 'shoe-a' };
  const model = { productId: 'shoe-a', colorwayId: 'pink', asset: `models/${'a'.repeat(64)}.glb`, sample: false };
  assert.equal(exactCatalogModel(product, { id: 'white', model }), null);
  assert.equal(exactCatalogModel({ id: 'shoe-b' }, { id: 'pink', model }), null);
  assert.equal(exactCatalogModel(product, { id: 'pink', model: { ...model, sample: true } }), null);
  assert.equal(exactCatalogModel(product, { id: 'pink', model: { ...model, asset: 'https://other.test/a.glb' } }), null);
});

test('different colorways retain different model assets and their explicit material variants', () => {
  const previousDocument = globalThis.document;
  globalThis.document = { baseURI: 'http://localhost:4205/' };
  try {
    const product = { id: 'shoe-a' };
    const colorway = (id, hash, variantName) => ({ id, model: { productId: product.id, colorwayId: id, asset: `models/${hash.repeat(64)}.glb`, variantName } });
    const pink = exactCatalogModel(product, colorway('pink', 'a', 'Pink'));
    const white = exactCatalogModel(product, colorway('white', 'b', 'White'));
    assert.notEqual(pink.src, white.src);
    assert.equal(pink.src, `/api/catalog/assets/models/${'a'.repeat(64)}.glb`);
    assert.equal(white.variantName, 'White');
  } finally {
    if (previousDocument === undefined) delete globalThis.document;
    else globalThis.document = previousDocument;
  }
});
