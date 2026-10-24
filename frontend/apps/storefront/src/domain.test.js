import { test } from "node:test";
import assert from "node:assert/strict";
import {
  addCart,
  totals,
  normalizeCart,
  validateAddress,
  hydrate,
  filterProducts,
  rememberSearch,
  validatePriceRange,
} from "./domain.js";
const products = [
  {
    id: "a",
    name: "运动鞋",
    brand: "A",
    en: "SHOE",
    price: 699,
    category: "球鞋",
    sizes: ["38", "39"],
    colors: ["红"],
    sold: 50,
  },
  {
    id: "b",
    name: "背包",
    brand: "B",
    en: "PACK",
    price: 429,
    category: "箱包",
    sizes: ["均码"],
    colors: ["蓝"],
    sold: 100,
  },
];
test("cart separates variants and caps quantities", () => {
  let rows = addCart(
    [],
    { id: "a", size: "38", color: "红", qty: 2 },
    products,
  );
  rows = addCart(rows, { id: "a", size: "39", color: "红", qty: 1 }, products);
  assert.equal(rows.length, 2);
  rows = addCart(
    rows,
    { id: "a", size: "38", color: "红", qty: 1000 },
    products,
  );
  assert.equal(rows[0].qty, 99);
  assert.equal(
    addCart(rows, { id: "a", size: "x", color: "红", qty: 2 }, products).length,
    2,
  );
});
test("only checked rows count; coupons obey thresholds", () => {
  const rows = [
    { id: "a", size: "38", color: "红", qty: 1, checked: true },
    { id: "b", size: "均码", color: "蓝", qty: 2, checked: false },
  ];
  assert.equal(totals(rows, products, { minimum: 500, amount: 50 }).total, 649);
  assert.equal(
    totals(rows, products, { minimum: 1000, amount: 100 }).total,
    699,
  );
});
test("corrupt storage and fractional quantities are rejected", () => {
  assert.equal(
    normalizeCart(
      [
        { id: "a", size: "38", color: "红", qty: Infinity },
        { id: "b", size: "均码", color: "蓝", qty: 0.2 },
      ],
      products,
    ).length,
    0,
  );
  assert.equal(hydrate({ version: 99, cart: [{}] }, products).cart.length, 0);
});
test("address requires a valid phone and detail", () => {
  assert.deepEqual(
    validateAddress({
      name: "测试",
      phone: "13800000000",
      region: "上海市徐汇区",
      detail: "示例路100号",
    }),
    {},
  );
  assert.equal(
    Object.keys(
      validateAddress({ name: "", phone: "123", region: "", detail: "" }),
    ).length,
    4,
  );
});
test("search and price sorting work within categories", () => {
  assert.equal(filterProducts(products, "全部", "PACK", "综合")[0].id, "b");
  assert.equal(filterProducts(products, "球鞋", "", "综合").length, 1);
  assert.equal(filterProducts(products, "全部", "", "价格升序")[0].id, "b");
});
test("discovery combines words, category and inclusive price bounds", () => {
  assert.equal(
    filterProducts(products, "全部", "  球鞋  SHOE  ", "综合")[0].id,
    "a",
  );
  assert.equal(
    filterProducts(products, "全部", "", "综合", { min: "429", max: "699" })
      .length,
    2,
  );
  assert.equal(
    filterProducts(products, "全部", "", "综合", { min: "", max: "500" })[0].id,
    "b",
  );
  assert.equal(
    filterProducts(products, "球鞋", "", "综合", { min: "", max: "500" })
      .length,
    0,
  );
  assert.equal(filterProducts(products, "全部", "not here", "综合").length, 0);
});
test("price validation rejects reversed, negative and invalid ranges", () => {
  assert.equal(validatePriceRange("", ""), "");
  assert.equal(validatePriceRange("0", "0"), "");
  for (const [min, max] of [
    ["900", "500"],
    ["-1", ""],
    ["", "NaN"],
    ["Infinity", ""],
  ])
    assert.ok(validatePriceRange(min, max));
});
test("recent searches normalize whitespace, deduplicate and cap local history", () => {
  assert.deepEqual(rememberSearch(["pace", "背包"], "  PACE   "), [
    "PACE",
    "背包",
  ]);
  assert.deepEqual(rememberSearch([null, " ", "耳机"], ""), ["耳机"]);
  assert.equal(
    rememberSearch(
      Array.from({ length: 10 }, (_, i) => String(i)),
      "新词",
    ).length,
    8,
  );
});
test("v1 storage migrates without losing cart; remembered variants are validated", () => {
  const legacy = {
    version: 1,
    cart: [{ id: "a", size: "38", color: "红", qty: 2 }],
    favorites: ["b"],
  };
  assert.equal(hydrate(legacy, products).cart[0].qty, 2);
  assert.deepEqual(hydrate(legacy, products).searchHistory, []);
  const upgraded = hydrate(
    {
      ...legacy,
      searchHistory: [null, "背包"],
      variantPreferences: {
        a: { size: "39", color: "红" },
        b: { size: "wrong", color: "蓝" },
      },
    },
    products,
  );
  assert.deepEqual(upgraded.variantPreferences, {
    a: { size: "39", color: "红" },
  });
  assert.deepEqual(upgraded.searchHistory, ["背包"]);
});
