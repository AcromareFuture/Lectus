export const CONSENT_COOKIE = 'lectus_consent';
export const CONSENT_EVENT = 'lectus-consent';
export const OPTIONAL_KEYS = ['lectus-language', 'lectus-web-saved'];
const MAX_AGE = 180 * 24 * 60 * 60;

export function parseConsent(cookies, now = Date.now()) {
  try {
    const raw = cookies.split(';').map(value => value.trim()).find(value => value.startsWith(`${CONSENT_COOKIE}=`));
    const value = JSON.parse(decodeURIComponent(raw?.slice(CONSENT_COOKIE.length + 1)));
    return value.v === 1 && typeof value.preferences === 'boolean' && Number.isFinite(value.at) && value.at <= now && now - value.at < MAX_AGE * 1000 ? value : null;
  } catch { return null; }
}
export const readConsent = () => parseConsent(document.cookie);
export const preferencesAllowed = () => Boolean(readConsent()?.preferences);
export function saveConsent(preferences) {
  const value = { v: 1, preferences: Boolean(preferences), at: Date.now() };
  document.cookie = `${CONSENT_COOKIE}=${encodeURIComponent(JSON.stringify(value))}; Path=${import.meta.env?.BASE_URL || '/'}; Max-Age=${MAX_AGE}; SameSite=Lax${location.protocol === 'https:' ? '; Secure' : ''}`;
  if (!preferences) OPTIONAL_KEYS.forEach(key => { try { localStorage.removeItem(key); } catch { /* Browser storage may be blocked. */ } });
  window.dispatchEvent(new Event(CONSENT_EVENT));
  const stored = readConsent();
  return stored?.preferences === value.preferences && stored?.at === value.at;
}
export function preferenceValue(key) {
  if (!preferencesAllowed()) return null;
  try { return localStorage.getItem(key); } catch { return null; }
}
export function savePreference(key, value) {
  if (!preferencesAllowed()) return false;
  try { localStorage.setItem(key, value); return true; } catch { return false; }
}
