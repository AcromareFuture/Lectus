export function skuKey(row) {
  return [row.id, row.size, row.color].join("|");
}
export function normalizeCart(rows, products) {
  if (!Array.isArray(rows)) return [];
  const out = [];
  for (const row of rows) {
    const p = products.find((x) => x.id === row?.id);
    if (
      !p ||
      !p.sizes.includes(row.size) ||
      !p.colors.includes(row.color) ||
      !Number.isFinite(Number(row.qty)) ||
      Number(row.qty) < 1
    )
      continue;
    const clean = {
      id: p.id,
      size: row.size,
      color: row.color,
      qty: Math.min(99, Math.floor(Number(row.qty))),
      checked: row.checked !== false,
    };
    const same = out.find((x) => skuKey(x) === skuKey(clean));
    if (same) same.qty = Math.min(99, same.qty + clean.qty);
    else out.push(clean);
  }
  return out;
}
export function addCart(cart, row, products) {
  const rows = normalizeCart(cart, products);
  const p = products.find((x) => x.id === row.id);
  if (!p || !p.sizes.includes(row.size) || !p.colors.includes(row.color))
    return rows;
  const n = Math.max(1, Math.min(99, Math.floor(Number(row.qty) || 1)));
  const same = rows.find((x) => skuKey(x) === skuKey(row));
  if (same) {
    same.qty = Math.min(99, same.qty + n);
    same.checked = true;
  } else
    rows.push({
      id: row.id,
      size: row.size,
      color: row.color,
      qty: n,
      checked: true,
    });
  return rows;
}
export function totals(rows, products, coupon) {
  const selected = normalizeCart(rows, products).filter((x) => x.checked);
  const subtotal = selected.reduce(
    (n, x) => n + products.find((p) => p.id === x.id).price * x.qty,
    0,
  );
  const discount =
    coupon && subtotal >= coupon.minimum
      ? Math.min(coupon.amount, subtotal)
      : 0;
  return {
    selected,
    count: selected.reduce((n, x) => n + x.qty, 0),
    subtotal,
    discount,
    total: subtotal - discount,
  };
}
export function validateAddress(values) {
  const errors = {};
  if (!values.name.trim()) errors.name = "请填写收件人姓名";
  if (!/^1\d{10}$/.test(values.phone)) errors.phone = "请填写 11 位手机号";
  if (values.region.trim().length < 2) errors.region = "请填写省、市、区";
  if (values.detail.trim().length < 4) errors.detail = "请填写完整街道与门牌号";
  return errors;
}
export function filterProducts(
  products,
  category,
  query,
  sort,
  priceRange = {},
) {
  const words = String(query || "")
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean);
  const min =
    priceRange.min === "" || priceRange.min == null
      ? 0
      : Number(priceRange.min);
  const max =
    priceRange.max === "" || priceRange.max == null
      ? Infinity
      : Number(priceRange.max);
  let result = products.filter(
    (p) =>
      (category === "全部" || p.category === category) &&
      p.price >= min &&
      p.price <= max &&
      words.every((word) =>
        `${p.name} ${p.brand} ${p.en} ${p.category}`
          .toLowerCase()
          .includes(word),
      ),
  );
  if (sort === "价格升序") result.sort((a, b) => a.price - b.price);
  if (sort === "价格降序") result.sort((a, b) => b.price - a.price);
  if (sort === "人气优先") result.sort((a, b) => b.sold - a.sold);
  return result;
}
export function rememberSearch(history, term) {
  const clean = String(term || "")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, 40);
  const valid = Array.isArray(history)
    ? history.filter((x) => typeof x === "string" && x.trim())
    : [];
  return clean
    ? [
        clean,
        ...valid.filter((x) => x.toLowerCase() !== clean.toLowerCase()),
      ].slice(0, 8)
    : valid.slice(0, 8);
}
export function validatePriceRange(min, max) {
  if (
    [min, max].some(
      (x) => x !== "" && (!Number.isFinite(Number(x)) || Number(x) < 0),
    )
  )
    return "请输入有效的非负价格";
  if (min !== "" && max !== "" && Number(min) > Number(max))
    return "最高价不能低于最低价";
  return "";
}
export function initialState() {
  return {
    version: 1,
    cart: [],
    favorites: [],
    likes: [],
    followed: [],
    addresses: [],
    orders: [],
    listings: [],
    appraisals: [],
    localPosts: [],
    claimed: [],
    profile: { name: "莱择新朋友", bio: "风格由你定义。" },
    messages: [],
    readMessages: [],
    history: [],
    searchHistory: [],
    variantPreferences: {},
  };
}
export function hydrate(raw, products) {
  const base = initialState();
  if (!raw || raw.version !== 1) return base;
  const arrays = [
    "favorites",
    "likes",
    "followed",
    "addresses",
    "orders",
    "listings",
    "appraisals",
    "localPosts",
    "claimed",
    "messages",
    "readMessages",
    "history",
  ];
  for (const key of arrays) if (Array.isArray(raw[key])) base[key] = raw[key];
  base.favorites = base.favorites.filter((id) =>
    products.some((p) => p.id === id),
  );
  base.cart = normalizeCart(raw.cart, products);
  base.searchHistory = rememberSearch(raw.searchHistory, "");
  for (const [id, row] of Object.entries(raw.variantPreferences || {})) {
    const p = products.find((x) => x.id === id);
    if (p?.sizes.includes(row?.size) && p.colors.includes(row?.color))
      base.variantPreferences[id] = { size: row.size, color: row.color };
  }
  if (raw.profile && typeof raw.profile.name === "string")
    base.profile = {
      name: raw.profile.name.slice(0, 20),
      bio: String(raw.profile.bio || "").slice(0, 60),
    };
  return base;
}
