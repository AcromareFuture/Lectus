const base = () => (import.meta.env?.VITE_API_BASE_URL || new URL('api/', document.baseURI).pathname).replace(/\/$/, '');
export const catalogUrl = (path) => `${base()}/catalog/${path}`;
export const assetUrl = (key) => key ? catalogUrl(`assets/${key}`) : null;

export async function catalogRequest(path, signal) {
  let response;
  try { response = await fetch(catalogUrl(path), { signal: AbortSignal.any([signal, AbortSignal.timeout(15000)].filter(Boolean)), headers: { Accept: 'application/json' } }); }
  catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('商品服务暂时无法连接，请稍后重试');
  }
  if (!response.ok) throw new Error(response.status === 404 ? '这件商品暂时无法查看' : '商品加载失败，请重试');
  return response.json();
}

export function matchingSkus(skus, selection) {
  return skus.filter((sku) => Object.entries(selection).every(([key, value]) => sku.specs[key] === value));
}

export function exactCatalogModel(product, colorway) {
  const model = colorway?.model;
  return model && !model.sample && model.productId === product.id && model.colorwayId === colorway.id && /^models\/[a-f0-9]{64}\.glb$/.test(model.asset)
    ? { ...model, src: assetUrl(model.asset) } : null;
}
