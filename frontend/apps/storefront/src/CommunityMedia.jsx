import React, { useEffect, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, Expand, LoaderCircle, Pause, Play, RefreshCw, Volume2, VolumeX, X } from 'lucide-react';

export function CommunityGallery({ post }) {
  const [active, setActive] = useState(0), [expanded, setExpanded] = useState(false);
  const dialog = useRef(null), touch = useRef(null), opener = useRef(null);
  const move = delta => setActive(n => Math.max(0, Math.min(post.media.length - 1, n + delta)));
  useEffect(() => {
    if (!expanded) return undefined;
    const el = dialog.current, overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden'; el.showModal();
    return () => { el.close(); document.body.style.overflow = overflow; opener.current?.focus({ preventScroll: true }); };
  }, [expanded]);
  function keys(e) { if (e.key === 'ArrowLeft' || e.key === 'ArrowRight') { e.preventDefault(); move(e.key === 'ArrowLeft' ? -1 : 1); } }
  function swipe(e) { const start = touch.current, end = e.changedTouches[0]; touch.current = null; if (start && Math.abs(end.clientX - start.x) > 60 && Math.abs(end.clientY - start.y) < 70) move(end.clientX < start.x ? 1 : -1); }
  const gestures = { onKeyDown: keys, onTouchStart: e => { touch.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }; }, onTouchEnd: swipe };
  const controls = <div className="community-gallery-controls"><button type="button" aria-label="上一张图片" disabled={active === 0} onClick={() => move(-1)}><ChevronLeft size={22} /></button><span role="status" aria-live="polite">{active + 1} / {post.media.length}</span><button type="button" aria-label="下一张图片" disabled={active === post.media.length - 1} onClick={() => move(1)}><ChevronRight size={22} /></button></div>;
  return <div className="community-detail-gallery" {...gestures}>
    <button ref={opener} type="button" className="community-open-image" aria-label="放大查看图片" onClick={() => setExpanded(true)}><img className="community-main-image" src={post.media[active].url} alt={`${post.title}，第 ${active + 1} 张`} /><span><Expand size={16} />放大查看</span></button>
    {post.media.length > 1 && <>{!expanded && controls}<div className="community-thumbnails">{post.media.map((m, i) => <button type="button" key={m.id} aria-label={`查看第 ${i + 1} 张图片`} aria-pressed={active === i} onClick={() => setActive(i)}><img src={m.url} alt="" /></button>)}</div></>}
    {expanded && <dialog ref={dialog} className="community-lightbox" aria-label="图片查看器" onCancel={e => { e.preventDefault(); setExpanded(false); }} onClick={e => { if (e.target === e.currentTarget) setExpanded(false); }}><div className="community-lightbox-inner"><header><span>{post.title}</span><button type="button" autoFocus aria-label="关闭图片查看器" onClick={() => setExpanded(false)}><X size={24} /></button></header><img src={post.media[active].url} alt={`${post.title}，第 ${active + 1} 张`} />{controls}<p>左右滑动或使用方向键切换 · Esc 关闭</p></div></dialog>}
  </div>;
}

export function CommunityVideo({ media, poster, auto = false }) {
  const ref = useRef(null), manualPause = useRef(false), manualPlay = useRef(false);
  const [muted, setMuted] = useState(true), [playing, setPlaying] = useState(false), [failed, setFailed] = useState(false), [buffering, setBuffering] = useState(false), [progress, setProgress] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!auto || !el) return undefined;
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting || entry.intersectionRatio < .1) { el.pause(); manualPause.current = false; manualPlay.current = false; }
      else if (entry.intersectionRatio >= .65 && !document.hidden && !manualPause.current && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) el.play().catch(() => {});
      else if (entry.intersectionRatio < .65 && !manualPlay.current) el.pause();
    }, { threshold: [0, .1, .65] });
    const hide = () => { if (document.hidden) el.pause(); };
    observer.observe(el); document.addEventListener('visibilitychange', hide);
    return () => { observer.disconnect(); document.removeEventListener('visibilitychange', hide); el.pause(); };
  }, [auto]);
  function toggle() { const el = ref.current; if (!el.paused) { manualPause.current = true; manualPlay.current = false; el.pause(); } else { manualPause.current = false; manualPlay.current = true; el.play().catch(() => setFailed(true)); } }
  function retry() { setFailed(false); setBuffering(true); ref.current.load(); ref.current.play().catch(() => { setBuffering(false); }); }
  if (!media?.url) return <div className="community-video"><p className="video-error" role="alert">视频暂时无法读取，请刷新内容后重试。</p></div>;
  return <div className={`community-video ${auto ? 'video-auto' : ''}`}>
    <video ref={ref} src={media.url} poster={poster?.url} controls={!auto} loop={auto} muted={muted} playsInline preload="metadata" onPlay={() => setPlaying(true)} onPlaying={() => setBuffering(false)} onWaiting={() => setBuffering(true)} onPause={() => { setPlaying(false); setBuffering(false); }} onTimeUpdate={e => setProgress(e.currentTarget.duration ? e.currentTarget.currentTime / e.currentTarget.duration : 0)} onError={() => { setFailed(true); setBuffering(false); }} />
    {failed ? <div className="video-error" role="alert"><p>视频暂时无法播放</p><button type="button" onClick={retry}><RefreshCw size={16} />重新播放</button></div> : auto && <><button type="button" className="video-play-toggle" aria-label={playing ? '暂停视频' : '播放视频'} onClick={toggle}>{buffering ? <LoaderCircle size={32} className="community-spin" /> : !playing ? <Play size={38} fill="currentColor" /> : <Pause className="video-pause-hint" size={30} />}</button><button type="button" className="video-sound" aria-label={muted ? '开启声音' : '关闭声音'} onClick={() => setMuted(v => !v)}>{muted ? <VolumeX size={20} /> : <Volume2 size={20} />}</button><progress className="community-video-progress" max={1} value={progress} aria-label="视频播放进度" /></>}
    {buffering && !auto && !failed && <span className="video-buffering" role="status"><LoaderCircle className="community-spin" size={22} />视频加载中…</span>}
  </div>;
}
