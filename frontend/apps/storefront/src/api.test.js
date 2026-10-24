import test from "node:test";
import assert from "node:assert/strict";
import { createApi, ApiError } from "./api.js";

test("checkout sends only SKU quantities, bearer auth and a reusable idempotency key", async () => {
  let received;
  const api = createApi({ base: "/lectus/api/", getToken: () => "session", fetcher: async (url, options) => {
    received = { url, options };
    return new Response(JSON.stringify({ id: "order-1" }), { status: 201 });
  } });
  const body = { addressId: "address", items: [{ skuId: "sku", quantity: 2 }], fromCart: true };
  assert.deepEqual(await api.createOrder(body, "retry-key-1"), { id: "order-1" });
  assert.equal(received.url, "/lectus/api/orders");
  assert.equal(received.options.headers.Authorization, "Bearer session");
  assert.equal(received.options.headers["Idempotency-Key"], "retry-key-1");
  assert.deepEqual(JSON.parse(received.options.body), body);
});
test("revoked session is a structured error instead of a successful empty result", async () => {
  const api = createApi({ base: "/api", fetcher: async () => new Response(JSON.stringify({ code: "UNAUTHORIZED", message: "请先登录" }), { status: 401 }) });
  await assert.rejects(api.me(), (error) => error instanceof ApiError && error.status === 401 && error.code === "UNAUTHORIZED");
});
test("network errors and non-JSON proxy responses expose actionable errors", async () => {
  const offline = createApi({ base: "/api", fetcher: async () => { throw new TypeError("offline"); } });
  await assert.rejects(offline.health(), { code: "NETWORK_ERROR" });
  const proxy = createApi({ base: "/api", fetcher: async () => new Response("<html>proxy failure</html>", { status: 502 }) });
  await assert.rejects(proxy.health(), { code: "INVALID_RESPONSE", status: 502 });
});
test("logout handles no-content and product IDs are URL encoded", async () => {
  const urls = [];
  const api = createApi({ base: "/api", fetcher: async (url) => { urls.push(url); return new Response(null, { status: 204 }); } });
  assert.equal(await api.logout(), null);
  await api.product("one/two");
  assert.deepEqual(urls, ["/api/auth/logout", "/api/products/one%2Ftwo"]);
});
