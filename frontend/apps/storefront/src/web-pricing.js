import { localPreviewPrices } from './web-prices.js';

const cny = new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', maximumFractionDigits: 0 });

export function previewPrice(id) {
  const cents = Object.hasOwn(localPreviewPrices, id) ? localPreviewPrices[id] : null;
  return Number.isSafeInteger(cents) && cents > 0
    ? { cents, currency: 'CNY', formatted: cny.format(cents / 100) }
    : null;
}
