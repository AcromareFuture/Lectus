import test from 'node:test';
import assert from 'node:assert/strict';
import { communityReturn, readCommunityDraft, saveCommunityDraft } from './community-state.js';

const memory = () => { const data = new Map(); return { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }; };
test('community return links preserve filters and reject external or unrelated destinations', () => {
  assert.equal(communityReturn('#/community?tab=video&topic=球鞋日记'), '#/community?tab=video&topic=%E7%90%83%E9%9E%8B%E6%97%A5%E8%AE%B0');
  for (const value of ['https://example.com', '//example.com', '#/community-post?id=4', '#/community?tab=bad&topic=bad', null]) assert.equal(communityReturn(value), '#/community');
});
test('drafts are isolated by account, expire after one day and never retain media or tokens', () => {
  const storage = memory(), data = { title: '周末穿搭', body: '舒服的日常', kind: 'IMAGE', topic: '日常穿搭', files: ['private'], token: 'secret' };
  saveCommunityDraft(storage, 'a', data, 1000);
  assert.deepEqual(readCommunityDraft(storage, 'a', 2000), { title: data.title, body: data.body, kind: data.kind, topic: data.topic });
  assert.equal(readCommunityDraft(storage, 'b', 2000), null);
  assert.equal(readCommunityDraft(storage, 'a', 86401001), null);
  saveCommunityDraft(storage, 'a', data); saveCommunityDraft(storage, 'a', null);
  assert.equal(readCommunityDraft(storage, 'a'), null);
});
test('blocked browser storage does not prevent editing and future-dated drafts are rejected', () => {
  const blocked = { getItem() { throw new Error('denied'); }, setItem() { throw new Error('full'); } };
  assert.equal(readCommunityDraft(blocked, 'a'), null);
  assert.equal(saveCommunityDraft(blocked, 'a', { title: 'hi', body: '' }), false);
  const storage = memory(); saveCommunityDraft(storage, 'a', { title: 'hi', body: '' }, 5000);
  assert.equal(readCommunityDraft(storage, 'a', 1000), null);
});
