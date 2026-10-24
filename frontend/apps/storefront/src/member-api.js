import { serviceMessage } from '../../../packages/business-records';
const SESSION_KEY = 'lectus-c-member';
export function storedSession() {
  try { const value = JSON.parse(localStorage.getItem(SESSION_KEY)); return value?.token ? value : null; } catch { return null; }
}
export function saveSession(value) {
  if (value) localStorage.setItem(SESSION_KEY, JSON.stringify(value)); else localStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new Event('lectus-session'));
}
export async function memberRequest(path, { method = 'GET', body, signal, anonymous = false } = {}) {
  const token = anonymous ? null : storedSession()?.token;
  const multipart = body instanceof FormData;
  let response;
  try {
    response = await fetch(`${import.meta.env.BASE_URL}api/${path}`, { method, signal, headers: { ...(body && !multipart ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: multipart ? body : body ? JSON.stringify(body) : undefined });
  } catch (error) { if (error.name === 'AbortError') throw error; throw new Error('暂时连接不上账户服务，请稍后重试。'); }
  if (response.status === 401) { if (token) saveSession(null); throw new Error('登录已失效，请重新登录。'); }
  const result = await response.json().catch(() => null);
  if (!response.ok || !result?.success) throw new Error(serviceMessage(result?.message) || '服务暂时不可用，请稍后重试。');
  return result.data;
}
export function paymentScanUrl(token, scanOrigin) {
  const origin = scanOrigin || window.location.origin;
  return `${origin}${import.meta.env.BASE_URL}#/scan?token=${encodeURIComponent(token)}`;
}
