import React, { useEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowRight, Bookmark, Check, ChevronLeft, ChevronRight, Heart, ImagePlus, LoaderCircle, MessageCircle, Play, Plus, RefreshCw, Send, Trash2, X } from 'lucide-react';
import { memberRequest } from './member-api';
import { webHref } from './web-route';
import './community.css';
import './community-interactions.css';
import { CommunityGallery, CommunityVideo as Video } from './CommunityMedia';
import { useCommunityFeedback } from './community-feedback';
import { communityReturn, communityTopics, readCommunityDraft, saveCommunityDraft } from './community-state';
import { Dialog } from './MallPages';

const topics = communityTopics;
let feedReturnPoint = null;
const tabs = [['recommend', '推荐'], ['following', '关注'], ['video', '视频'], ['saved', '我的收藏'], ['mine', '我的发布']];
const requestKey = () => crypto.randomUUID();
const date = value => new Date(value).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' });
const coverOf = post => post.media.find(m => m.kind === 'IMAGE');
const href = post => webHref('community-post', { id: post.id });
const draftStorage = () => { try { return window.sessionStorage; } catch { return null; } };
function Avatar({ author }) { return <span className="community-avatar">{author.avatarUrl ? <img src={author.avatarUrl} alt="" loading="lazy" /> : author.name.slice(0, 1)}</span>; }
function LoginPrompt({ next, title = '登录后，接着分享你的日常。' }) { return <div className="community-empty"><h2>{title}</h2><p>发布内容、关注创作者，也把喜欢的内容留在这里。</p><a className="button primary" href={webHref('login', { next })}>登录 / 注册<ArrowRight size={16} /></a></div>; }

function Actions({ post, member, onUpdate, compact = false, onError, onNotice }) {
  const lock = useRef(false);
  const [busy, setBusy] = useState('');
  async function react(kind) {
    if (!member.session) { window.location.hash = webHref('login', { next: href(post) }); return; }
    if (lock.current) return; lock.current = true; setBusy(kind);
    try { const enabled = !post[kind === 'LIKE' ? 'liked' : 'saved']; onUpdate(await memberRequest(`community/posts/${post.id}/reactions/${kind}`, { method: 'PUT', body: { enabled } })); onNotice?.(kind === 'LIKE' ? enabled ? '已点赞' : '已取消点赞' : enabled ? '已收藏，可在「我的收藏」查看' : '已取消收藏'); }
    catch (e) { onError(e.message); } finally { lock.current = false; setBusy(''); }
  }
  return <div className={`community-actions ${compact ? 'compact' : ''}`}>
    <button className={post.liked ? 'selected' : ''} aria-pressed={post.liked} aria-label={post.liked ? '取消点赞' : '点赞'} disabled={Boolean(busy)} onClick={() => react('LIKE')}>{busy === 'LIKE' ? <LoaderCircle size={compact ? 17 : 21} className="community-spin" /> : <Heart size={compact ? 17 : 21} fill={post.liked ? 'currentColor' : 'none'} />}<span>{post.likes || (compact ? '赞' : '点赞')}</span></button>
    {!compact && <><button className={post.saved ? 'selected' : ''} aria-pressed={post.saved} aria-label={post.saved ? '取消收藏内容' : '收藏内容'} disabled={Boolean(busy)} onClick={() => react('SAVE')}>{busy === 'SAVE' ? <LoaderCircle size={21} className="community-spin" /> : <Bookmark size={21} fill={post.saved ? 'currentColor' : 'none'} />}<span>{post.saves || '收藏'}</span></button><a href={`${href(post)}&comments=1`} aria-label="查看评论"><MessageCircle size={21} /><span>{post.comments || '评论'}</span></a></>}
  </div>;
}

export function CommunityFeed({ route, member }) {
  const { notify, feedback } = useCommunityFeedback();
  const restore = useRef(feedReturnPoint?.key === route.key && feedReturnPoint?.token === member.session?.token && Date.now() - feedReturnPoint.at < 300000 ? feedReturnPoint : null);
  const tab = tabs.some(([id]) => id === route.params.get('tab')) ? route.params.get('tab') : 'recommend';
  const topic = topics.includes(route.params.get('topic')) ? route.params.get('topic') : '';
  const viewKey = `${tab}:${topic}:${member.session?.token || ''}`;
  const [loadedView, setLoadedView] = useState('');
  const [items, setItems] = useState([]), [loading, setLoading] = useState(true), [error, setError] = useState(''), [more, setMore] = useState(false), [cursor, setCursor] = useState(0), [attempt, setAttempt] = useState(0);
  const gate = useRef(false), controller = useRef(null), sentinel = useRef(null);
  const restricted = ['following', 'mine', 'saved'].includes(tab) && !member.session;
  const endpoint = before => `community/public/feed?${new URLSearchParams({ tab: tab === 'video' ? 'recommend' : tab, topic, kind: tab === 'video' ? 'VIDEO' : '', before: String(before) })}`;
  useEffect(() => {
    const c = new AbortController(); controller.current = c; setLoadedView(viewKey);
    if (restore.current && attempt === 0 && restore.current.key === route.key && restore.current.token === member.session?.token) { const point = restore.current; restore.current = null; feedReturnPoint = null; setItems(point.items); setMore(point.more); setCursor(point.cursor); setLoading(false); gate.current = false; const frame = requestAnimationFrame(() => window.scrollTo({ top: point.scroll, behavior: 'instant' })); return () => { cancelAnimationFrame(frame); c.abort(); }; }
    gate.current = true; setItems([]); setError(''); setMore(false); setLoading(true);
    if (restricted) { gate.current = false; setLoading(false); return () => c.abort(); }
    memberRequest(endpoint(0), { signal: c.signal }).then(data => { if (!c.signal.aborted) { setItems(data.items); setMore(data.hasMore); setCursor(data.nextCursor); } }).catch(e => { if (!c.signal.aborted) setError(e.message); }).finally(() => { if (!c.signal.aborted) { gate.current = false; setLoading(false); } });
    return () => c.abort();
  }, [tab, topic, member.session?.token, attempt]);
  async function loadMore() {
    if (gate.current || !more) return; gate.current = true; setLoading(true); setError(''); const c = controller.current;
    try { const data = await memberRequest(endpoint(cursor), { signal: c.signal }); if (!c.signal.aborted) { setItems(old => [...old, ...data.items.filter(next => !old.some(item => item.id === next.id))]); setMore(data.hasMore); setCursor(data.nextCursor); } }
    catch (e) { if (!c.signal.aborted) setError(e.message); } finally { if (!c.signal.aborted) { gate.current = false; setLoading(false); } }
  }
  useEffect(() => {
    if (!more || loading || error || !sentinel.current) return undefined;
    const observer = new IntersectionObserver(([entry]) => { if (entry.isIntersecting) loadMore(); }, { rootMargin: '400px' }); observer.observe(sentinel.current); return () => observer.disconnect();
  }, [more, loading, error, cursor]);
  const update = next => setItems(old => old.map(item => item.id === next.id ? next : item));
  function remember(e) { const link = e.target.closest('a'); if (!link?.getAttribute('href')?.startsWith('#/community-post')) return; feedReturnPoint = { key: route.key, token: member.session?.token, items, cursor, more, scroll: window.scrollY, at: Date.now() }; }
  const postHref = post => webHref('community-post', { id: post.id, return: route.key });
  const visibleItems = loadedView === viewKey ? items : [];
  const waiting = loading || loadedView !== viewKey;
  return <section className="community-page route-view" data-i18n-skip onClickCapture={remember}>{feedback}
    <header className="community-heading"><div><h1>生活，自有<span>主场。</span></h1><p>穿搭、球鞋、好物，还有你的每一天。</p></div><a className="button primary community-publish" href={webHref('community-publish')}><Plus size={19} />发布内容</a></header>
    <div className="community-toolbar"><nav aria-label="社区栏目">{tabs.map(([id, label]) => <a key={id} href={webHref('community', { tab: id, topic })} aria-current={tab === id ? 'page' : undefined}>{label}</a>)}</nav><button className="community-refresh" aria-label="刷新社区" onClick={() => setAttempt(v => v + 1)} disabled={waiting}><RefreshCw size={17} /></button></div>
    <div className="community-topics" aria-label="社区话题"><a aria-current={!topic ? 'true' : undefined} href={webHref('community', { tab })}>全部话题</a>{topics.map(name => <a key={name} aria-current={topic === name ? 'true' : undefined} href={webHref('community', { tab, topic: name })}># {name}</a>)}</div>
    {error && loadedView === viewKey && <div className="community-error" role="alert"><p>{error}</p><button onClick={() => items.length ? loadMore() : setAttempt(v => v + 1)}>重新加载</button></div>}
    {restricted ? <LoginPrompt next={route.key} /> : <>
      {!waiting && !error && !visibleItems.length && <div className="community-empty"><ImagePlus size={32} /><h2>{tab === 'following' ? '关注的人，还没有新动态' : tab === 'saved' ? '喜欢的内容，留在这里' : tab === 'mine' ? '记录你的第一份日常' : tab === 'video' ? '第一段视频，等你来分享' : '这个话题，等你来开启'}</h2><p>{tab === 'saved' ? '打开内容，点击收藏，下次接着看。' : '分享穿搭、好物或生活片段，让同样喜欢的人看见。'}</p><a className="button secondary" href={webHref('community-publish')}>发布内容<ArrowRight size={16} /></a></div>}
      <div className={tab === 'video' ? 'community-reels' : 'community-masonry'} aria-busy={waiting}>{visibleItems.map(post => tab === 'video' ? <article className="community-reel" key={post.id}><Video media={post.media.find(m => m.kind === 'VIDEO')} poster={coverOf(post)} auto /><div className="reel-copy"><span>{post.author.name}{post.author.official && ' · 官方'}</span><a href={postHref(post)}><h2>{post.title}</h2></a><p>{post.body}</p><a className="reel-topic" href={webHref('community', { topic: post.topic })}># {post.topic}</a><Actions post={post} member={member} onUpdate={update} onError={message => notify(message, true)} onNotice={notify} /></div></article> : <article className="community-card" key={post.id}>
        <a href={postHref(post)} className="community-card-cover" style={{ aspectRatio: `${coverOf(post)?.width || 4}/${coverOf(post)?.height || 5}` }} aria-label={`查看${post.kind === 'VIDEO' ? '视频' : '图文'}：${post.title}`}><img src={coverOf(post)?.url} alt={post.title} loading="lazy" />{post.kind === 'VIDEO' && <span className="community-media-count"><Play size={14} fill="currentColor" />{Math.floor(post.media[0].duration / 60)}:{String(post.media[0].duration % 60).padStart(2, '0')}</span>}{post.kind === 'IMAGE' && post.media.length > 1 && <span className="community-media-count">{post.media.length} 图</span>}</a>
        <div className="community-card-content"><a className="community-card-topic" href={webHref('community', { tab, topic: post.topic })}># {post.topic}</a><a href={postHref(post)}><h2>{post.title}</h2></a><div className="community-card-bottom"><span className="community-card-author"><Avatar author={post.author} /><span>{post.author.name}</span>{post.author.official && <Check size={12} aria-label="官方" />}</span><Actions post={post} member={member} compact onUpdate={update} onError={message => notify(message, true)} onNotice={notify} /></div></div>
      </article>)}</div>
      {waiting && !visibleItems.length && tab !== 'video' && <div className="community-masonry community-skeletons" aria-hidden="true">{Array.from({ length: 8 }, (_, i) => <div className="community-skeleton-card" key={i}><div style={{ aspectRatio: i % 3 === 0 ? '4/5' : '1/1' }} /><i /><i /></div>)}</div>}
      {waiting && <div className="community-loading" role="status"><LoaderCircle size={20} className="community-spin" />正在加载内容…</div>}
      <div ref={sentinel} className="community-feed-end">{more && !waiting && <button className="button secondary" onClick={loadMore}>继续浏览<ArrowRight size={16} /></button>}{!more && visibleItems.length > 0 && !waiting && <span>已看到这里，新的日常等你分享。</span>}</div>
    </>}
  </section>;
}

export function CommunityPost({ route, member }) {
  const { notify, feedback } = useCommunityFeedback();
  const back = communityReturn(route.params.get('return'));
  const operation = useRef(false);
  const [post, setPost] = useState(null), [comments, setComments] = useState([]), [more, setMore] = useState(false), [loading, setLoading] = useState(true), [loadError, setLoadError] = useState(''), [error, setError] = useState(''), [attempt, setAttempt] = useState(0), [body, setBody] = useState(''), [busy, setBusy] = useState(false), [confirmDelete, setConfirmDelete] = useState(false);
  const commentKey = useRef(requestKey()), commentInput = useRef(null);
  useEffect(() => { const c = new AbortController(); setLoading(true); setLoadError(''); Promise.all([memberRequest(`community/public/posts/${route.id}`, { signal: c.signal }), memberRequest(`community/public/posts/${route.id}/comments`, { signal: c.signal })]).then(([p, list]) => { setPost(p); setComments(list.items); setMore(list.hasMore); }).catch(e => { if (!c.signal.aborted) setLoadError(e.message); }).finally(() => { if (!c.signal.aborted) setLoading(false); }); return () => c.abort(); }, [route.id, member.session?.token, attempt]);
  useEffect(() => { if (!loading && route.params.get('comments')) commentInput.current?.focus(); }, [loading]);
  async function follow() { if (operation.current) return; if (!member.session) { window.location.hash = webHref('login', { next: route.key }); return; } operation.current = true; setBusy(true); setError(''); try { await memberRequest(`community/authors/${post.author.id}/follow`, { method: 'PUT', body: { enabled: !post.following } }); setPost(old => ({ ...old, following: !old.following })); notify(post.following ? '已取消关注' : '已关注，可在「关注」查看动态'); } catch (e) { setError(e.message); } finally { operation.current = false; setBusy(false); } }
  async function send(e) { e.preventDefault(); if (operation.current || !body.trim()) return; operation.current = true; setBusy(true); setError(''); try { await memberRequest(`community/posts/${post.id}/comments`, { method: 'POST', body: { body: body.trim(), requestKey: commentKey.current } }); setBody(''); commentKey.current = requestKey(); const list = await memberRequest(`community/public/posts/${post.id}/comments`); setComments(list.items); setMore(list.hasMore); setPost(await memberRequest(`community/public/posts/${post.id}`)); notify('评论已发送'); } catch (e) { setError(e.message); } finally { operation.current = false; setBusy(false); } }
  async function moreComments() { if (operation.current) return; operation.current = true; setBusy(true); setError(''); try { const list = await memberRequest(`community/public/posts/${post.id}/comments?after=${comments.at(-1)?.id || 0}`); setComments(old => [...old, ...list.items]); setMore(list.hasMore); } catch (e) { setError(e.message); } finally { operation.current = false; setBusy(false); } }
  async function remove() { if (operation.current) return; operation.current = true; setBusy(true); setError(''); try { await memberRequest(`community/posts/${post.id}`, { method: 'DELETE' }); feedReturnPoint = null; window.location.hash = webHref('community', { tab: 'mine' }); } catch (e) { setError(e.message); } finally { operation.current = false; setBusy(false); } }
  if (loading) return <div className="community-loading" role="status"><LoaderCircle className="community-spin" size={20} />正在打开内容…</div>;
  if (loadError || !post) return <div className="community-empty"><h1>内容暂时无法打开</h1><p role="alert">{loadError}</p><button className="button secondary" onClick={() => setAttempt(v => v + 1)}>重新加载</button><a href={webHref('community')}>返回社区</a></div>;
  return <article className="community-detail route-view" data-i18n-skip>{feedback}<a className="back-link" href={back}><ArrowLeft size={16} />返回社区</a><div className="community-detail-layout">{post.kind === 'VIDEO' ? <div className="community-detail-gallery"><Video media={post.media.find(m => m.kind === 'VIDEO')} poster={coverOf(post)} /></div> : <CommunityGallery post={post} />}
    <div className="community-detail-content"><div className="community-author-line"><Avatar author={post.author} /><div><strong>{post.author.name}</strong><span>{post.author.official ? '官方内容' : date(post.createdAt)}</span></div>{!post.author.official && !post.owned && <button className={`community-follow ${post.following ? 'is-following' : ''}`} disabled={busy} onClick={follow}>{post.following ? <Check size={15} /> : <Plus size={15} />}{post.following ? '已关注' : '关注'}</button>}</div><h1>{post.title}</h1><p className="community-post-body">{post.body}</p><a className="community-card-topic" href={webHref('community', { topic: post.topic })}># {post.topic}</a><Actions post={post} member={member} onUpdate={next => { setPost(next); if (feedReturnPoint) feedReturnPoint.items = feedReturnPoint.items.map(item => item.id === next.id ? next : item); }} onError={message => notify(message, true)} onNotice={notify} />
      {error && <p className="community-inline-error" role="alert">{error}</p>}
      <section className="community-comments" id="community-comments"><h2>评论<span>{post.comments}</span></h2>{member.session ? <form onSubmit={send}><span className="community-comment-hint">Ctrl / ⌘ + Enter 发送</span><textarea ref={commentInput} aria-label="写评论" placeholder="聊聊你的想法…" onKeyDown={e => { if ((e.ctrlKey || e.metaKey) && e.key === 'Enter' && !e.nativeEvent.isComposing) { e.preventDefault(); e.currentTarget.form.requestSubmit(); } }} required maxLength={500} value={body} onChange={e => { setBody(e.target.value); commentKey.current = requestKey(); }} disabled={busy} /><button className="button primary" disabled={busy || !body.trim()}>{busy ? <LoaderCircle size={16} className="community-spin" /> : <Send size={16} />}发送</button></form> : <p className="community-comment-login"><a href={webHref('login', { next: route.key })}>登录</a>后参与讨论。</p>}
        {!comments.length && <p className="community-no-comments">还没有评论，来聊聊你的想法。</p>}{comments.map(comment => <article key={comment.id} className="community-comment"><Avatar author={comment.author} /><div><strong>{comment.author.name}</strong><p>{comment.body}</p><time>{date(comment.createdAt)}</time></div></article>)}{more && <button className="community-more-comments" disabled={busy} onClick={moreComments}>查看更多评论</button>}
      </section>{post.owned && <div className="community-delete">{confirmDelete ? <><p>删除后，这篇内容将不再公开展示。</p><button disabled={busy} onClick={remove}>确认删除</button><button disabled={busy} onClick={() => setConfirmDelete(false)}>保留内容</button></> : <button onClick={() => setConfirmDelete(true)}><Trash2 size={15} />删除这篇内容</button>}</div>}
    </div></div></article>;
}

async function readImage(file) {
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  try { const scale = Math.min(1, 1920 / Math.max(bitmap.width, bitmap.height)); const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale)); const ctx = canvas.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height); const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .9)); if (!blob) throw new Error('图片无法读取'); return { file: new File([blob], 'photo.jpg', { type: 'image/jpeg' }), width: canvas.width, height: canvas.height }; } finally { bitmap.close(); }
}
async function readVideo(file) {
  const video = document.createElement('video'); const url = URL.createObjectURL(file); video.muted = true; video.preload = 'auto'; video.playsInline = true;
  try {
    await new Promise((resolve, reject) => { const timeout = setTimeout(() => reject(new Error('视频读取超时，请重新选择')), 20000); video.onloadeddata = () => { clearTimeout(timeout); resolve(); }; video.onerror = () => { clearTimeout(timeout); reject(new Error('视频无法读取，请选择 H.264 MP4 视频')); }; video.src = url; });
    if (!Number.isFinite(video.duration) || video.duration <= 0 || video.duration > 120) throw new Error('请选择 2 分钟以内的视频');
    if (video.videoWidth > 4096 || video.videoHeight > 4096) throw new Error('视频分辨率不能超过 4K');
    const canvas = document.createElement('canvas'); const ratio = Math.min(1, 1080 / Math.max(video.videoWidth, video.videoHeight)); canvas.width = Math.round(video.videoWidth * ratio); canvas.height = Math.round(video.videoHeight * ratio); canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height); const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', .9)); if (!blob) throw new Error('无法生成视频封面，请重新选择'); return { file, cover: new File([blob], 'cover.jpg', { type: 'image/jpeg' }), width: video.videoWidth, height: video.videoHeight, duration: Math.ceil(video.duration) };
  } finally { video.removeAttribute('src'); video.load(); URL.revokeObjectURL(url); }
}

export function CommunityPublish({ member }) {
  if (!member.session) return <LoginPrompt next={webHref('community-publish')} title="把你的日常，分享给同样喜欢的人。" />;
  return <Composer key={member.session.token} member={member} />;
}
function Composer({ member }) {
  const account = String(member.session.memberId || member.session.username || '');
  const [draft] = useState(() => readCommunityDraft(draftStorage(), account));
  const [leaveTo, setLeaveTo] = useState('');
  const [draftStatus, setDraftStatus] = useState(draft ? '已恢复文字草稿，请重新选择图片或视频' : ''), [discard, setDiscard] = useState(false);
  const mediaSlots = useRef({ IMAGE: [], VIDEO: [] }), finished = useRef(false);
  const [kind, setKind] = useState(draft?.kind || 'IMAGE'), [files, setFiles] = useState([]), [title, setTitle] = useState(draft?.title || ''), [body, setBody] = useState(draft?.body || ''), [topic, setTopic] = useState(draft?.topic || topics[0]), [error, setError] = useState(''), [busy, setBusy] = useState(false), [processing, setProcessing] = useState(false), [progress, setProgress] = useState('');
  const input = useRef(null), urls = useRef(new Set()), publishKey = useRef(requestKey()), lock = useRef(false), abort = useRef(new AbortController());
  useEffect(() => () => { abort.current.abort(); urls.current.forEach(url => URL.revokeObjectURL(url)); }, []);
  useEffect(() => { const warn = e => { const textSaved = saveCommunityDraft(draftStorage(), account, { title, body, topic, kind }); if (!finished.current && (files.length || mediaSlots.current.IMAGE.length || mediaSlots.current.VIDEO.length || ((title || body) && !textSaved))) { e.preventDefault(); e.returnValue = ''; } }; window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn); }, [title, body, files.length, topic, kind, account]);
  useEffect(() => {
    if (finished.current) return;
    const ok = saveCommunityDraft(draftStorage(), account, { title, body, topic, kind });
    setDraftStatus(title || body ? ok ? '文字草稿已保存在当前标签页，图片与视频请在发布前选好' : '浏览器无法保存草稿，请保持页面打开' : '文字草稿仅保留在当前标签页，24 小时后过期');
  }, [title, body, topic, kind, account]);
  useEffect(() => {
    function guard(e) {
      const target = e.target.closest('a[href^="#/"]');
      if (!target || target.getAttribute('href') === '#/community-publish' || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey || finished.current) return;
      if (busy || processing) { e.preventDefault(); e.stopPropagation(); setError('正在处理内容，请等待完成后再离开。'); return; }
      if (files.length || mediaSlots.current.IMAGE.length || mediaSlots.current.VIDEO.length) { e.preventDefault(); e.stopPropagation(); setLeaveTo(target.getAttribute('href')); }
    }
    document.addEventListener('click', guard, true); return () => document.removeEventListener('click', guard, true);
  }, [busy, processing, files.length]);
  const dirty = () => { publishKey.current = requestKey(); setError(''); };
  function switchKind(value) { if (value === kind) return; mediaSlots.current[kind] = files; setKind(value); setFiles(mediaSlots.current[value]); dirty(); }
  function reorder(index, delta) { setFiles(old => { const next = [...old], target = index + delta; if (target < 0 || target >= next.length) return old; [next[index], next[target]] = [next[target], next[index]]; return next; }); dirty(); }
  function clearDraft() { urls.current.forEach(url => URL.revokeObjectURL(url)); urls.current.clear(); mediaSlots.current = { IMAGE: [], VIDEO: [] }; setFiles([]); setTitle(''); setBody(''); setDiscard(false); saveCommunityDraft(draftStorage(), account, null); dirty(); }

  async function select(e) {
    const incoming = Array.from(e.target.files || []); e.target.value = ''; if (!incoming.length) return;
    if (kind === 'IMAGE' && incoming.length + files.length > 9) { setError('一篇图文最多上传 9 张图片'); return; }
    setProcessing(true); setError('');
    try {
      const next = [];
      for (const file of incoming) {
        if (file.size > (kind === 'VIDEO' ? 50 : 10) * 1024 * 1024) throw new Error(kind === 'VIDEO' ? '视频不能超过 50 MB' : '单张图片不能超过 10 MB');
        if (kind === 'VIDEO' && !/\.mp4$/i.test(file.name)) throw new Error('请选择 MP4 视频');
        if (kind === 'IMAGE' && !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('请选择 JPG、PNG 或 WebP 图片');
        const data = kind === 'VIDEO' ? await readVideo(file) : await readImage(file);
        const preview = URL.createObjectURL(data.cover || data.file); urls.current.add(preview); next.push({ ...data, preview, key: requestKey(), mediaId: null, coverId: null });
      }
      setFiles(old => kind === 'VIDEO' ? next : [...old, ...next]); dirty();
    } catch (e) { setError(e.message || '文件读取失败，请重新选择'); } finally { setProcessing(false); }
  }
  async function upload(file, mediaKind) { const form = new FormData(); form.append('file', file); form.append('kind', mediaKind); return memberRequest('community/media', { method: 'POST', body: form, signal: abort.current.signal }); }
  async function publish(e) {
    e.preventDefault(); if (lock.current || processing) return; if (!files.length) { setError(kind === 'VIDEO' ? '请先选择视频' : '请先选择图片'); return; } lock.current = true; setBusy(true); setError('');
    try {
      const ids = [];
      for (let i = 0; i < files.length; i++) { const item = files[i]; setProgress(kind === 'VIDEO' ? '正在上传视频…' : `正在上传图片 ${i + 1} / ${files.length}…`); if (!item.mediaId) item.mediaId = (await upload(item.file, kind)).id; ids.push(item.mediaId); if (item.cover) { setProgress('正在保存视频封面…'); if (!item.coverId) item.coverId = (await upload(item.cover, 'IMAGE')).id; ids.push(item.coverId); } }
      setProgress('正在发布…'); const post = await memberRequest('community/posts', { method: 'POST', body: { title: title.trim(), body: body.trim(), topic, kind, mediaIds: ids, requestKey: publishKey.current }, signal: abort.current.signal }); finished.current = true; saveCommunityDraft(draftStorage(), account, null); feedReturnPoint = null; window.location.hash = href(post);
    } catch (e) { if (e.name !== 'AbortError') setError(e.message); } finally { lock.current = false; setBusy(false); setProgress(''); }
  }
  return <section className="community-composer route-view" data-i18n-skip><a href={webHref('community')} className="back-link"><ArrowLeft size={16} />返回社区</a><header><h1>分享你的日常<span>。</span></h1><p>一套穿搭，一件好物，或一个值得记录的片刻。</p></header><form onSubmit={publish} aria-busy={busy || processing}><div className="community-draft-bar"><p role="status">{draftStatus}</p>{(title || body || files.length > 0) && <button type="button" disabled={busy || processing} onClick={() => setDiscard(v => !v)}>清空草稿</button>}{discard && <div><span>清空文字和已选素材？</span><button type="button" disabled={busy || processing} onClick={clearDraft}>确认清空</button><button type="button" onClick={() => setDiscard(false)}>继续编辑</button></div>}</div><div className="community-editor-layout"><div className="community-media-editor"><div className="community-kind-tabs" role="group" aria-label="发布类型">{[['IMAGE', '图文'], ['VIDEO', '视频']].map(([value, label]) => <button key={value} type="button" aria-pressed={kind === value} disabled={busy || processing} onClick={() => switchKind(value)}>{label}</button>)}</div><input ref={input} type="file" accept={kind === 'VIDEO' ? 'video/mp4,.mp4' : 'image/jpeg,image/png,image/webp'} multiple={kind === 'IMAGE'} aria-label={kind === 'VIDEO' ? '选择视频文件' : '选择图片文件'} onChange={select} disabled={busy || processing} className="community-file-input" />
      {files.length > 0 && <div className={`community-upload-grid ${kind === 'VIDEO' ? 'upload-video' : ''}`}>{files.map((item, index) => <div key={item.key} className="community-upload-tile"><div className="community-upload-item"><img src={item.preview} alt={kind === 'VIDEO' ? '视频封面' : `待发布图片 ${index + 1}`} />{kind === 'VIDEO' && <span><Play size={18} />{Math.floor(item.duration / 60)}:{String(item.duration % 60).padStart(2, '0')}</span>}<button type="button" disabled={busy || processing} aria-label={`移除${kind === 'VIDEO' ? '视频' : `第 ${index + 1} 张图片`}`} onClick={() => { URL.revokeObjectURL(item.preview); urls.current.delete(item.preview); setFiles(old => old.filter(f => f.key !== item.key)); mediaSlots.current[kind] = mediaSlots.current[kind].filter(f => f.key !== item.key); dirty(); }}><X size={17} /></button></div>{kind === 'IMAGE' && <div className="community-upload-order"><span>{index === 0 ? '封面' : `第 ${index + 1} 张`}</span><button type="button" aria-label={`前移第 ${index + 1} 张图片`} disabled={busy || processing || index === 0} onClick={() => reorder(index, -1)}><ChevronLeft size={17} /></button><button type="button" aria-label={`后移第 ${index + 1} 张图片`} disabled={busy || processing || index === files.length - 1} onClick={() => reorder(index, 1)}><ChevronRight size={17} /></button></div>}</div>)}</div>}
      {(kind === 'IMAGE' && files.length < 9 || !files.length) && <button type="button" className={`community-upload-drop ${files.length ? 'has-files' : ''}`} disabled={busy || processing} onClick={() => input.current.click()}>{processing ? <LoaderCircle className="community-spin" size={30} /> : <ImagePlus size={32} strokeWidth={1.5} />}<strong>{processing ? '正在读取文件…' : kind === 'VIDEO' ? '选择一段视频' : files.length ? '继续添加图片' : '选择你想分享的图片'}</strong><span>{kind === 'VIDEO' ? 'MP4 · 最多 2 分钟 · 50 MB 以内' : 'JPG、PNG、WebP · 最多 9 张 · 每张 10 MB 以内'}</span></button>}
      <p className="community-upload-note">{kind === 'VIDEO' ? '视频将自动生成封面。建议使用 H.264 编码的 MP4，播放更流畅。' : `${files.length} / 9 张图片 · 第一张将作为封面`}</p></div>
      <div className="community-text-editor"><label>标题<input maxLength={80} required placeholder="给这份日常起个标题" value={title} disabled={busy} onChange={e => { setTitle(e.target.value); dirty(); }} /><small>{title.length} / 80</small></label><label>正文<textarea maxLength={2000} rows={8} placeholder="分享搭配思路、使用感受，或者这个片刻的故事…" value={body} disabled={busy} onChange={e => { setBody(e.target.value); dirty(); }} /><small>{body.length} / 2000</small></label><fieldset><legend>选择话题</legend><div className="community-composer-topics">{topics.map(name => <button key={name} type="button" disabled={busy} aria-pressed={topic === name} onClick={() => { setTopic(name); dirty(); }}># {name}</button>)}</div></fieldset><p className="community-publish-note">发布后，所有人都可以浏览这篇内容。请分享原创或已获授权的图片与视频。</p>{error && <p className="community-inline-error" role="alert">{error}</p>}<div className="community-publish-footer"><span role="status">{progress || (!title.trim() ? '请填写标题' : !files.length ? kind === 'VIDEO' ? '请选择视频' : '请选择图片' : '已准备好，可以发布')}</span><button className="button primary" disabled={busy || processing || !title.trim() || !files.length}>{busy ? <LoaderCircle className="community-spin" size={17} /> : <Send size={17} />}{busy ? '发布中…' : '发布内容'}</button></div></div></div></form>{leaveTo && <Dialog title="离开发布页？" onClose={() => setLeaveTo('')}><p className="community-leave-copy">文字草稿会保留在当前标签页，已选图片和视频需要重新选择。</p><div className="mall-actions"><button type="button" className="button secondary" onClick={() => setLeaveTo('')}>继续编辑</button><button type="button" className="button primary" onClick={() => { finished.current = true; window.location.hash = leaveTo; }}>保留文字并离开</button></div></Dialog>}</section>;
}
