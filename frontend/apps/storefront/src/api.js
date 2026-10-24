export class ApiError extends Error {
  constructor(message, status = 0, code = "NETWORK_ERROR") {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function createApi({ base, getToken = () => null, fetcher = fetch }) {
  async function request(path, { method = "GET", body, key } = {}) {
    const headers = { Accept: "application/json" };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (key) headers["Idempotency-Key"] = key;
    let response;
    try {
      response = await fetcher(`${base.replace(/\/$/, "")}${path}`, {
        method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(30000), cache: "no-store",
      });
    } catch {
      throw new ApiError("暂时无法连接服务，请稍后重试");
    }
    if (response.status === 204) return null;
    let result;
    try { result = await response.json(); }
    catch { throw new ApiError("服务响应异常，请稍后重试", response.status, "INVALID_RESPONSE"); }
    if (!response.ok) throw new ApiError(result.message || "操作未完成，请重试", response.status, result.code);
    return result;
  }
  return {
    health: () => request("/health"),
    register: (body) => request("/auth/register", { method: "POST", body }),
    login: (body) => request("/auth/login", { method: "POST", body }),
    me: () => request("/auth/me"),
    logout: () => request("/auth/logout", { method: "POST" }),
    products: (params = {}) => request(`/products?${new URLSearchParams(params)}`),
    product: (id) => request(`/products/${encodeURIComponent(id)}`),
    cart: () => request("/cart"),
    putCart: (body) => request("/cart/items", { method: "PUT", body }),
    deleteCart: (id) => request(`/cart/items/${encodeURIComponent(id)}`, { method: "DELETE" }),
    addresses: () => request("/addresses"),
    saveAddress: (body, id) => request(`/addresses${id ? `/${encodeURIComponent(id)}` : ""}`, { method: id ? "PUT" : "POST", body }),
    deleteAddress: (id) => request(`/addresses/${encodeURIComponent(id)}`, { method: "DELETE" }),
    orders: (page = 0) => request(`/orders?page=${page}&size=20`),
    order: (id) => request(`/orders/${encodeURIComponent(id)}`),
    createOrder: (body, key) => request("/orders", { method: "POST", body, key }),
    cancelOrder: (id) => request(`/orders/${encodeURIComponent(id)}/cancel`, { method: "POST" }),
  };
}
