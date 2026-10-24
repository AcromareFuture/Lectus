import test from 'node:test';
import assert from 'node:assert/strict';
import { provinces, regionOptions, validRegion, deliveryAddress } from './address-regions.js';

test('covers municipalities, autonomous regions and directly administered cities', () => {
  assert.equal(provinces.length, 34);
  for (const value of [
    { province: '北京市', city: '北京市', district: '朝阳区' },
    { province: '广东省', city: '东莞市', district: '中堂镇' },
    { province: '新疆维吾尔自治区', city: '乌鲁木齐市', district: '天山区' },
    { province: '香港特别行政区', city: '香港岛', district: '中西区' },
  ]) assert.ok(validRegion(value), JSON.stringify(value));
});
test('rejects stale cities and districts from a different parent', () => {
  assert.equal(validRegion({ province: '浙江省', city: '北京市', district: '朝阳区' }), false);
  assert.equal(validRegion({ province: '广东省', city: '广州市', district: '南山区' }), false);
  assert.equal(validRegion({ province: '浙江省', city: '杭州市', district: '' }), false);
  assert.deepEqual(regionOptions({ province: '' }), { cities: [], districts: [] });
});
test('sends the complete region and trimmed detail without changing the order API', () => {
  const address = { province: '浙江省', city: '杭州市', district: '西湖区', detail: '  文一路 8 号 2 幢 301 室  ' };
  assert.equal(deliveryAddress(address), '浙江省 杭州市 西湖区 文一路 8 号 2 幢 301 室');
  assert.throws(() => deliveryAddress({ ...address, detail: '   ' }));
  assert.throws(() => deliveryAddress({ ...address, city: '广州市' }));
});
