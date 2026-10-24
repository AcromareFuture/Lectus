import { readWebRoute, webHref } from './web-route.js';

const DRAFT_PREFIX = 'lectus-community-draft:';
const DRAFT_TTL = 24 * 60 * 60 * 1000;
export const communityTopics = ['日常穿搭', '球鞋日记', '户外生活', '数码好物', '生活方式'];
export function communityReturn(value) {
  if (typeof value !== 'string' || !/^#\/community(?:\?|$)/.test(value)) return webHref('community');
  const route = readWebRoute(value);
  return webHref('community', { tab: ['recommend', 'following', 'video', 'saved', 'mine'].includes(route.params.get('tab')) ? route.params.get('tab') : undefined, topic: communityTopics.includes(route.params.get('topic')) ? route.params.get('topic') : undefined });
}
// Only writing content is stored, in this tab; files and credentials never enter storage.
export function readCommunityDraft(storage, account, now = Date.now()) {
  if (!account) return null;
  try {
    const data = JSON.parse(storage.getItem(DRAFT_PREFIX + account));
    if (!data || !Number.isFinite(data.at) || now < data.at || now - data.at > DRAFT_TTL) { storage.removeItem(DRAFT_PREFIX + account); return null; }
    return { title: String(data.title || '').slice(0, 80), body: String(data.body || '').slice(0, 2000), topic: communityTopics.includes(data.topic) ? data.topic : communityTopics[0], kind: data.kind === 'VIDEO' ? 'VIDEO' : 'IMAGE' };
  } catch { return null; }
}
export function saveCommunityDraft(storage, account, data, now = Date.now()) {
  if (!account) return false;
  try {
    if (!data || (!data.title && !data.body)) storage.removeItem(DRAFT_PREFIX + account);
    else storage.setItem(DRAFT_PREFIX + account, JSON.stringify({ title: data.title.slice(0, 80), body: data.body.slice(0, 2000), topic: data.topic, kind: data.kind, at: now }));
    return true;
  } catch { return false; }
}
