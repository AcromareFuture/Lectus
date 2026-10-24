import test from 'node:test';
import assert from 'node:assert/strict';
import { cropRect, avatarSrc } from './avatar-crop.js';
import { parseConsent, CONSENT_COOKIE, saveConsent, preferenceValue, savePreference } from './cookie-consent.js';

test('portrait and landscape crops remain inside the source at every pan and zoom edge', () => {
  for (const [width, height] of [[1600, 900], [900, 1600], [600, 600]]) {
    for (const zoom of [1, 1.5, 4]) for (const x of [-1, 0, 1]) for (const y of [-1, 0, 1]) {
      const rect = cropRect(width, height, zoom, x, y);
      assert.ok(rect.sx >= 0 && rect.sy >= 0);
      assert.ok(rect.sx + rect.side <= width + 1e-8 && rect.sy + rect.side <= height + 1e-8);
    }
  }
  assert.deepEqual(cropRect(1200, 600, 1, 1, 0), { sx: 0, sy: 0, side: 600 });
  assert.deepEqual(cropRect(1200, 600, 1, -1, 0), { sx: 600, sy: 0, side: 600 });
});
test('avatars accept only same-site uploaded images and honor the application base path', () => {
  const url = '/api/member/avatar/' + 'a'.repeat(32) + '.png';
  assert.equal(avatarSrc(url, '/lectus/'), '/lectus' + url);
  assert.equal(avatarSrc('https://external.example/avatar.png'), null);
  assert.equal(avatarSrc('/api/member/avatar/../private.png'), null);
});
test('cookie choices require explicit booleans, a supported version and an unexpired decision', () => {
  const now = Date.now();
  const cookie = value => `other=x; ${CONSENT_COOKIE}=${encodeURIComponent(JSON.stringify(value))}`;
  for (const preferences of [true, false]) assert.equal(parseConsent(cookie({ v: 1, at: now, preferences }), now).preferences, preferences);
  for (const value of [{ v: 2, at: now, preferences: true }, { v: 1, at: now, preferences: 'true' }, { v: 1, at: now - 181 * 86400000, preferences: true }, { v: 1, at: now + 1000, preferences: true }]) assert.equal(parseConsent(cookie(value), now), null);
  assert.equal(parseConsent('lectus_consent=%broken'), null);
  assert.equal(parseConsent(''), null);
});

test('optional storage waits for consent and revocation preserves the login session', () => {
  const values = new Map([['lectus-c-member', 'session']]);
  const previous = Object.fromEntries(['document', 'window', 'location', 'localStorage'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  try {
    let cookie = '';
    Object.defineProperties(globalThis, {
      document: { configurable: true, value: { get cookie() { return cookie; }, set cookie(value) { cookie = value.split(';')[0]; } } },
      window: { configurable: true, value: new EventTarget() },
      location: { configurable: true, value: { protocol: 'http:' } },
      localStorage: { configurable: true, value: { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) } },
    });
    assert.equal(savePreference('lectus-language', 'ja'), false);
    assert.equal(preferenceValue('lectus-language'), null);
    assert.equal(saveConsent(true), true);
    assert.equal(savePreference('lectus-language', 'ja'), true);
    assert.equal(savePreference('lectus-web-saved', '[1]'), true);
    assert.equal(preferenceValue('lectus-language'), 'ja');
    assert.equal(saveConsent(false), true);
    assert.equal(values.has('lectus-language'), false);
    assert.equal(values.has('lectus-web-saved'), false);
    assert.equal(values.get('lectus-c-member'), 'session');
    assert.equal(savePreference('lectus-language', 'en'), false);
    Object.defineProperty(document, 'cookie', { get: () => cookie, set: () => {} });
    assert.equal(saveConsent(true), false);
  } finally {
    for (const [key, descriptor] of Object.entries(previous)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else delete globalThis[key];
    }
  }
});
