import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, unlink } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';
import { build } from 'esbuild';
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';

// Component regression tests run without a browser or production API access.
const directory = new URL('../node_modules/.cache/', import.meta.url);
await mkdir(directory, { recursive: true });
const output = new URL(`lectus-community-${process.pid}.mjs`, directory);
await build({ entryPoints: [fileURLToPath(new URL('./Community.jsx', import.meta.url))], outfile: fileURLToPath(output), bundle: true, platform: 'node', format: 'esm', packages: 'external', loader: { '.css': 'empty' }, define: { 'import.meta.env.BASE_URL': '"/"' }, logLevel: 'silent' });
const { CommunityFeed, CommunityPublish } = await import(output.href);
const { readWebRoute } = await import('./web-route.js');
const dom = new JSDOM('<div id="root"></div>', { url: 'https://local.invalid/' });
for (const key of ['window', 'document', 'localStorage', 'sessionStorage', 'File', 'Blob']) globalThis[key] = dom.window[key];
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const observers = [];
globalThis.IntersectionObserver = class { constructor(callback) { this.callback = callback; observers.push(this); } observe() {} disconnect() {} };
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0);
globalThis.cancelAnimationFrame = clearTimeout;
window.matchMedia = () => ({ matches: true });
window.scrollTo = () => {};
Object.defineProperty(dom.window.HTMLMediaElement.prototype, 'paused', { get() { return !this.unitPlaying; } });
dom.window.HTMLMediaElement.prototype.play = function () { this.unitPlaying = true; this.dispatchEvent(new window.Event('play')); this.dispatchEvent(new window.Event('playing')); return Promise.resolve(); };
dom.window.HTMLMediaElement.prototype.pause = function () { this.unitPlaying = false; this.dispatchEvent(new window.Event('pause')); };
dom.window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
dom.window.HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
globalThis.createImageBitmap = async () => ({ width: 10, height: 10, close() {} });
dom.window.HTMLCanvasElement.prototype.getContext = () => ({ fillRect() {}, drawImage() {} });
dom.window.HTMLCanvasElement.prototype.toBlob = callback => callback(new Blob(['image'], { type: 'image/jpeg' }));
let sequence = 0;
URL.createObjectURL = () => `blob:local-${++sequence}`;
URL.revokeObjectURL = () => {};
const member = { session: { memberId: 123, token: 'unit-session' } };
const author = { id: 123, name: '会员', official: false };
const image = { id: 1, title: '图文记录', body: '', kind: 'IMAGE', topic: '日常穿搭', author, media: [{ id: 'i1', kind: 'IMAGE', url: '/photo.jpg', width: 10, height: 10 }], liked: false, saved: false };
const video = { ...image, id: 2, title: '视频记录', kind: 'VIDEO', media: [{ id: 'v1', kind: 'VIDEO', url: '/clip.mp4', duration: 6 }, ...image.media] };
let root;
const render = async element => { if (!root) root = createRoot(document.getElementById('root')); await act(async () => root.render(element)); };
const click = async node => { assert.ok(node, 'control exists'); await act(async () => node.click()); };
const button = name => [...document.querySelectorAll('button')].find(node => node.textContent.trim() === name);
const response = data => ({ status: 200, ok: true, json: async () => ({ success: true, data }) });
test.afterEach(async () => { if (root) await act(async () => root.unmount()); root = null; document.getElementById('root').innerHTML = ''; localStorage.clear(); sessionStorage.clear(); });
test.after(async () => { await unlink(output); dom.window.close(); });

test('switching from image feed to video never renders stale image rows as videos while the request is pending', async () => {
  let deliverVideo;
  globalThis.fetch = async url => {
    if (url.includes('kind=VIDEO')) return new Promise(resolve => { deliverVideo = () => resolve(response({ items: [video], hasMore: false, nextCursor: 2 })); });
    return response({ items: [image], hasMore: false, nextCursor: 1 });
  };
  await render(React.createElement(CommunityFeed, { member, route: readWebRoute('#/community') }));
  assert.equal(document.querySelectorAll('.community-card').length, 1);
  await render(React.createElement(CommunityFeed, { member, route: readWebRoute('#/community?tab=video') }));
  assert.equal(document.querySelectorAll('.community-reel').length, 0);
  assert.match(document.body.textContent, /正在加载内容/);
  await act(async () => deliverVideo());
  assert.equal(document.querySelectorAll('.community-reel').length, 1);
  assert.equal(document.querySelector('video').getAttribute('src'), '/clip.mp4');
  await render(React.createElement(CommunityFeed, { member, route: readWebRoute('#/community') }));
  assert.equal(document.querySelectorAll('video').length, 0);
  assert.equal(document.querySelectorAll('.community-card').length, 1);
});

test('image order and cover survive type switches; leaving with media offers to continue editing', async () => {
  globalThis.fetch = () => { throw new Error('Editing must not upload files'); };
  await render(React.createElement(CommunityPublish, { member }));
  const input = document.querySelector('input[type=file]');
  Object.defineProperty(input, 'files', { configurable: true, value: [new File(['a'], 'one.jpg', { type: 'image/jpeg' }), new File(['b'], 'two.jpg', { type: 'image/jpeg' })] });
  await act(async () => input.dispatchEvent(new window.Event('change', { bubbles: true })));
  const before = [...document.querySelectorAll('.community-upload-item>img')].map(node => node.src);
  assert.equal(before.length, 2);
  await click(document.querySelector('[aria-label="后移第 1 张图片"]'));
  assert.equal(document.querySelector('.community-upload-item>img').src, before[1]);
  await click(button('视频')); assert.equal(document.querySelectorAll('.community-upload-item').length, 0);
  await click(button('图文')); assert.equal(document.querySelectorAll('.community-upload-item').length, 2);
  assert.equal(document.querySelector('.community-upload-item>img').src, before[1]);
  await click(document.querySelector('a.back-link'));
  assert.ok(document.querySelector('dialog[open]'));
  assert.match(document.querySelector('dialog').textContent, /重新选择/);
  await click(button('继续编辑'));
  assert.equal(document.querySelectorAll('.community-upload-item').length, 2);
});

test('manual video playback stays active while partly visible and pauses when it leaves view', async () => {
  globalThis.fetch = async () => response({ items: [video], hasMore: false, nextCursor: 2 });
  await render(React.createElement(CommunityFeed, { member, route: readWebRoute('#/community?tab=video') }));
  const element = document.querySelector('video'), observer = observers.at(-1);
  await click(document.querySelector('[aria-label="播放视频"]'));
  await act(async () => observer.callback([{ isIntersecting: true, intersectionRatio: .4 }]));
  assert.equal(element.paused, false);
  await act(async () => observer.callback([{ isIntersecting: false, intersectionRatio: 0 }]));
  assert.equal(element.paused, true);
});
