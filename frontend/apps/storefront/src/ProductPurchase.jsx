import React, { useEffect, useRef, useState } from 'react';
import { ShoppingBag, ArrowRight, Minus, Plus, Check, LoaderCircle } from 'lucide-react';
import { memberRequest } from './member-api';
import { cartChanged } from './mall-cart-state';
import { webHref } from './web-route';
import './product-purchase.css';

export default function ProductPurchase({ product, member, colorway, onVariant }) {
  const [variants, setVariants] = useState([]);
  const [selected, setSelected] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [pendingAction, setPendingAction] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [attempt, setAttempt] = useState(0);
  const lock = useRef(false);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('');
    memberRequest(`mall/public/products/${encodeURIComponent(product.id)}/variants`, { anonymous: true, signal: controller.signal })
      .then(items => setVariants(items))
      .catch(e => { if (e.name !== 'AbortError') setError(e.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [product.id, attempt]);
  const visible = variants.filter(v => !colorway || !product.skus?.length || product.skus.some(s => s.id === v.id && s.colorwayId === colorway));
  const current = visible.find(v => v.id === selected);
  function choose(v) { setSelected(v.id); setQuantity(1); setNotice(''); onVariant?.(v.id); }
  async function add(checkout) {
    if (!current || !current.stock || lock.current) return;
    if (!member.session) { window.location.hash = webHref('login', { next: webHref('product', { id: product.id }) }); return; }
    lock.current = true; setBusy(true); setPendingAction(checkout ? 'checkout' : 'cart'); setError(''); setNotice('');
    try {
      const items = await memberRequest('mall/cart/items', { method: 'POST', body: { productId: current.id, quantity } });
      cartChanged(items);
      if (checkout) window.location.hash = webHref('mall-cart', { select: current.id });
      else setNotice(`已加入购物车，${quantity} 件`);
    } catch (e) { setError(e.message); }
    finally { lock.current = false; setBusy(false); setPendingAction(''); }
  }
  const lowest = variants.length ? Math.min(...variants.map(v => Number(v.price))) : null;
  const money = n => new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(n);
  return <section className="product-purchase" aria-label="选择规格与购买">
    <div className="detail-price"><strong>{current ? money(current.price) : lowest !== null ? money(lowest) : '暂未上架'}{!current && lowest !== null ? ' 起' : ''}</strong><span>售价 · 人民币</span></div>
    {loading ? <p role="status">正在读取规格与库存…</p> : variants.length > 0 ? <>
      <fieldset disabled={busy}><legend>选择规格{current ? ` · ${current.spec}` : ''}</legend><div className="purchase-variants">{visible.map(v => <button type="button" key={v.id} aria-pressed={selected === v.id} disabled={v.stock === 0} onClick={() => choose(v)}>{v.spec}{v.stock === 0 && ' · 缺货'}</button>)}</div>{!visible.length && <p>此配色暂未上架，请选择其他配色。</p>}</fieldset>
      <div className="purchase-quantity"><label htmlFor="purchase-count">数量</label><div className="purchase-stepper"><button type="button" aria-label="减少购买数量" disabled={!current || busy || quantity <= 1} onClick={() => { setQuantity(n => n - 1); setNotice(''); }}><Minus size={16} /></button><input id="purchase-count" type="number" inputMode="numeric" min="1" max={Math.min(current?.stock || 1, 99)} disabled={!current || busy} value={quantity} onChange={e => { setQuantity(Math.max(1, Math.min(Math.min(current?.stock || 1, 99), Math.trunc(Number(e.target.value)) || 1))); setNotice(''); }} /><button type="button" aria-label="增加购买数量" disabled={!current || busy || quantity >= Math.min(current?.stock || 1, 99)} onClick={() => { setQuantity(n => n + 1); setNotice(''); }}><Plus size={16} /></button></div><span>{current ? `可选库存 ${current.stock} 件` : '请先选择规格'}</span></div>
      <p className="purchase-store">{current?.merchantName || variants[0]?.merchantName}</p>
      <div className="product-actions" aria-busy={busy}><button className="button secondary" disabled={!current || !current.stock || busy} onClick={() => add(false)}>{pendingAction === 'cart' ? <LoaderCircle size={17} className="purchase-spin" /> : <ShoppingBag size={17} />}{pendingAction === 'cart' ? '正在加入…' : '加入购物车'}</button><button className="button primary" disabled={!current || !current.stock || busy} onClick={() => add(true)}>{pendingAction === 'checkout' ? '正在准备订单…' : '立即购买'}{pendingAction === 'checkout' ? <LoaderCircle size={17} className="purchase-spin" /> : <ArrowRight size={17} />}</button></div>
    </> : !error && <p>这件商品尚未上架，暂时无法购买。</p>}
    {error && <p className="member-alert" role="alert">{error} <button className="mall-text-button" onClick={() => setAttempt(n => n + 1)}>刷新库存</button></p>}
    {notice && <p className="purchase-notice" role="status"><Check size={18} />{notice} <a href={webHref('mall-cart')}>查看购物车<ArrowRight size={14} /></a></p>}
    <p className="detail-note">请确认商品规格及收货信息，订单提交时将核对价格与库存。</p>
  </section>;
}
