// User-supplied assets published for device testing. Keep the explicit preview
// entry separate from reviewed catalog bindings and never use a fallback shoe.
const kobe = Object.freeze({
  src: 'models/previews/d4d8e683d2eae93946ea1ee7576ba62d77fcec99e4564bfebed91b13791f9ecc.glb',
  experimental: true,
  alt: 'Nike Kobe 道奇蓝 AI 外观参考模型',
});

const samba = Object.freeze({
  src: 'models/previews/af0571800e113200a2873400a788dc17a7441092ec9dbf7d7698d2db5e4455de.glb',
  experimental: true,
  alt: 'adidas Samba OG 白黑 AI 外观参考模型',
});

const models = new Map([['wp-kobe', kobe], ['wp-samba', samba]]);

export function getMeshyPreviewModel(productId, { enabled = false } = {}) {
  return enabled ? models.get(productId) || null : null;
}
