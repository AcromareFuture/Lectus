const paths = new Set(['home', 'discover', 'product', 'saved', 'shops', 'studio', 'account', 'login', 'checkout', 'scan', 'settings', 'mall', 'mall-orders', 'mall-cart', 'mall-cashier', 'community', 'community-post', 'community-publish']);
export function readWebRoute(hash = '') {
  const [rawPath, query = ''] = hash.replace(/^#\/?/, '').split('?');
  let path = rawPath === 'studio' ? 'product' : paths.has(rawPath) ? rawPath : 'home';
  if (rawPath === 'mall') path = 'home';
  const params = new URLSearchParams(query);
  if (rawPath === 'studio') {
    params.set('id', params.get('id') === 'wp-samba' ? 'wp-samba' : 'wp-kobe');
    params.set('view', '3d');
  }
  if (rawPath === 'checkout' || rawPath === 'scan') { const id = params.get('product') || params.get('id'); path = id ? 'product' : 'home'; if (id) params.set('id', id); params.delete('product'); }
  const page = Math.max(0, Math.min(999, Math.trunc(Number(params.get('page')) || 0)));
  return { path, params, category: params.get('category') || '', brand: params.get('brand') || '', q: (params.get('q') || '').slice(0, 100), page, id: params.get('id') || '', key: webHref(path, Object.fromEntries(params)) };
}
export function webHref(path, values = {}) {
  const params = new URLSearchParams(Object.entries(values).filter(([, value]) => value !== '' && value != null && value !== false));
  if (path === 'mall') path = 'home';
  return `#/${paths.has(path) ? path : 'home'}${params.size ? `?${params}` : ''}`;
}
export function patchWebFilters(route, patch) {
  return webHref('discover', { category: route.category, brand: route.brand, q: route.q, page: 0, ...patch });
}
