// These two user-supplied display editions are distinct from imported SPUs.
// Never match a model by brand/name or reuse it for another catalog colorway.
export const featuredEditions = Object.freeze([
  Object.freeze({ id: 'wp-kobe', name: 'Kobe 5 Protro Dodgers', brand: 'Nike', color: '道奇蓝', category: '鞋类', photo: 'product-kobe', colorwayCount: 1, displayEdition: true, description: '把热爱，穿上场。' }),
  Object.freeze({ id: 'wp-samba', name: 'Samba OG', brand: 'adidas', color: '白黑', category: '鞋类', photo: 'product-samba', colorwayCount: 1, displayEdition: true, description: '经典，自有新意。' }),
]);

export function getFeaturedEdition(id) {
  return featuredEditions.find(product => product.id === id) || null;
}
