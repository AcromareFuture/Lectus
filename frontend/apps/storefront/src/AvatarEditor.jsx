import React, { useEffect, useRef, useState } from 'react';
import { Camera, ImagePlus, RotateCcw } from 'lucide-react';
import { useI18n } from './i18n';
import { memberRequest } from './member-api';
import MemberAvatar from './MemberAvatar';
import { cropRect } from './avatar-crop';
import './member-preferences.css';

export default function AvatarEditor({ member }) {
  const { t } = useI18n();
  const fileInput = useRef(null), canvas = useRef(null), drag = useRef(null), selection = useRef(0);
  const [image, setImage] = useState(null), [crop, setCrop] = useState({ zoom: 1, x: 0, y: 0 });
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  useEffect(() => () => { selection.current += 1; }, []);
  useEffect(() => {
    if (!image || !canvas.current) return;
    const context = canvas.current.getContext('2d');
    const { sx, sy, side } = cropRect(image.naturalWidth, image.naturalHeight, crop.zoom, crop.x, crop.y);
    context.fillStyle = '#fff'; context.fillRect(0, 0, 512, 512);
    context.drawImage(image, sx, sy, side, side, 0, 0, 512, 512);
  }, [image, crop]);
  async function select(event) {
    const file = event.target.files?.[0]; event.target.value = '';
    if (!file) return;
    const id = ++selection.current;
    setError(''); setNotice('');
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { setError('请选择 JPG、PNG 或 WebP 图片。'); return; }
    if (file.size > 10 * 1024 * 1024) { setError('请选择小于 10 MB 的图片。'); return; }
    setLoading(true);
    const url = URL.createObjectURL(file);
    try {
      const next = new Image(); next.src = url; await next.decode();
      if (next.naturalWidth * next.naturalHeight > 32_000_000) throw new Error('图片分辨率过高，请选择较小的图片。');
      if (id === selection.current) { setImage(next); setCrop({ zoom: 1, x: 0, y: 0 }); }
    } catch { if (id === selection.current) setError('图片无法读取，请换一张图片。'); }
    finally { URL.revokeObjectURL(url); if (id === selection.current) setLoading(false); }
  }
  async function save() {
    setBusy(true); setError(''); setNotice('');
    try {
      const blob = await new Promise(resolve => canvas.current.toBlob(resolve, 'image/png'));
      if (!blob) throw new Error('图片无法读取，请换一张图片。');
      const body = new FormData(); body.append('file', blob, 'avatar.png');
      const result = await memberRequest('member/avatar', { method: 'POST', body });
      member.setProfile(current => ({ ...current, avatarUrl: result.avatarUrl }));
      setImage(null); setNotice('头像已更新。');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  function move(event) {
    if (!drag.current || busy) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const { side } = cropRect(image.naturalWidth, image.naturalHeight, crop.zoom);
    const rangeX = (image.naturalWidth - side) / side * rect.width / 2;
    const rangeY = (image.naturalHeight - side) / side * rect.height / 2;
    const clamp = value => Math.max(-1, Math.min(1, value));
    setCrop(value => ({ ...value, x: rangeX ? clamp(drag.current.x + (event.clientX - drag.current.startX) / rangeX) : 0, y: rangeY ? clamp(drag.current.y + (event.clientY - drag.current.startY) / rangeY) : 0 }));
  }
  return <section className="avatar-editor" aria-labelledby="avatar-title">
    <div className="avatar-editor-summary"><MemberAvatar profile={member.profile} /><div><h3 id="avatar-title">{t('个人头像')}</h3><p>{t('选择一张照片，让主场更像你。')}</p><button className="text-link" disabled={busy || loading} onClick={() => fileInput.current.click()}><Camera size={16} />{t(loading ? '正在读取图片…' : image ? '重新选图' : '更换头像')}</button></div></div>
    <input ref={fileInput} className="settings-sr-only" type="file" accept="image/jpeg,image/png,image/webp" aria-label={t('选择头像图片')} tabIndex={-1} onChange={select} disabled={busy || loading} />
    {image && <div className="avatar-crop-editor">
      <div className="avatar-crop-preview" onPointerDown={event => { if (busy) return; event.currentTarget.setPointerCapture(event.pointerId); drag.current = { startX: event.clientX, startY: event.clientY, x: crop.x, y: crop.y }; }} onPointerMove={move} onPointerUp={() => { drag.current = null; }} onPointerCancel={() => { drag.current = null; }}>
        <canvas ref={canvas} width="512" height="512" role="img" aria-label={t('头像裁剪预览')} /><span className="avatar-circle-guide" aria-hidden="true" />
      </div>
      <div className="avatar-crop-controls"><h3>{t('裁剪头像')}</h3><p>{t('拖动照片调整位置，缩放后确认圆形区域。')}</p>
        {[['zoom', '缩放', 1, 4, false], ['x', '水平位置', -1, 1, image.naturalWidth === Math.min(image.naturalWidth, image.naturalHeight) / crop.zoom], ['y', '垂直位置', -1, 1, image.naturalHeight === Math.min(image.naturalWidth, image.naturalHeight) / crop.zoom]].map(([key, label, min, max, fixed]) => <label key={key}>{t(label)}<input type="range" min={min} max={max} step="0.01" value={crop[key]} disabled={busy || fixed} onChange={event => setCrop(value => ({ ...value, [key]: Number(event.target.value) }))} /></label>)}
        <button className="text-link" disabled={busy} onClick={() => setCrop({ zoom: 1, x: 0, y: 0 })}><RotateCcw size={15} />{t('重置裁剪')}</button>
        <div className="account-form-actions"><button className="button primary" disabled={busy || loading} onClick={save}>{t(busy ? '正在保存…' : '保存头像')}<ImagePlus size={16} /></button><button className="button secondary" disabled={busy} onClick={() => { setImage(null); setError(''); }}>{t('取消')}</button></div>
      </div>
    </div>}
    <p className="avatar-file-hint">{t('支持 JPG、PNG、WebP，最大 10 MB。确认后上传裁剪结果。')}</p>
    {error && <p className="member-inline-error" role="alert">{t(error)}</p>}{notice && <p className="member-inline-success" role="status">{t(notice)}</p>}
  </section>;
}
