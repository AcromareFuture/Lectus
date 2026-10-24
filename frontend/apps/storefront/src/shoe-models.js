// Bind a real, licensed model to an exact product id here. Never fall back to
// the independent demo when a product has no matching model.
export const productModels = Object.freeze({});

export const sampleShoeModel = Object.freeze({
  src: "models/MaterialsVariantsShoe.glb",
  sample: true,
  alt: "可旋转的运动鞋模型，支持三种配色",
  materials: [
    { id: "midnight", label: "午夜", color: "#0875a0" },
    { id: "beach", label: "海滩", color: "#cc9ea4" },
    { id: "street", label: "街头", color: "linear-gradient(135deg, #252526 55%, #f64b68 55%)" },
  ],
  attribution: {
    copyright: "© 2021 Shopify",
    source: "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/MaterialsVariantsShoe",
    license: "CC BY 4.0",
    licenseUrl: "https://creativecommons.org/licenses/by/4.0/",
    note: "仅切换展示材质，原始模型未修改。",
  },
});

export function getProductModel(productId) {
  const model = Object.hasOwn(productModels, productId) ? productModels[productId] : null;
  return model?.src && !model.sample ? model : null;
}
