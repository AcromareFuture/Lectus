import React, { useEffect, useRef, useState } from 'react';
import { mallImage } from './MallPages';
import { Check, ChevronRight, Clock3, CreditCard, MapPin, Minus, Package, Plus, ShieldCheck, ShoppingBag, Store, Trash2 } from 'lucide-react';
import { memberRequest } from './member-api';
import { webHref } from './web-route';
import { cartChanged } from './mall-cart-state';
import { Dialog, ProductLine, money } from './MallPages';
import './mall-cart.css';
import AddressFields from './AddressFields';
import { deliveryAddress } from './address-regions';
import { transactionReference } from '../../../packages/business-records';
import { OrderSubmitting, PaymentResult } from './TradeFeedback';

const requestKey = () => Array.from(crypto.getRandomValues(new Uint8Array(16)), n => n.toString(16).padStart(2, '0')).join('');
const total = items => items.reduce((n, item) => n + Math.round(Number(item.price) * 100) * item.quantity, 0) / 100;
function LoginRequired({ next }) { return <div className="empty-state"><ShoppingBag size={34} /><h1>购物车</h1><p>登录后保存商品，在不同设备上继续选购。</p><a className="button primary" href={webHref('login', { next })}>登录 / 注册</a></div>; }
function CartQuantity({ item, busy, change }) {
 return <div className="mall-cart-quantity" aria-label={`${item.name}购买数量`}><button aria-label={`减少${item.name}数量`} disabled={busy || item.quantity <= 1} onClick={() => change(item, item.quantity - 1)}><Minus size={14} /></button><output aria-live="polite">{item.quantity}</output><button aria-label={`增加${item.name}数量`} disabled={busy || item.quantity >= Math.min(99, item.stock)} onClick={() => change(item, item.quantity + 1)}><Plus size={14} /></button></div>;
}
export function MallCart({ member, go }) {
 const [items, setItems] = useState([]); const [selected, setSelected] = useState([]); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
 const [busy, setBusy] = useState(false); const [confirm, setConfirm] = useState(null); const [removed, setRemoved] = useState(null);
 const [form, setForm] = useState({ recipient: '', phone: '', province: '', city: '', district: '', detail: '' }); const [formError, setFormError] = useState('');
 const locked = useRef(false); const sequence = useRef(0); const initial = useRef(true); const submitKey = useRef(''); const refresh = useRef(null);
 function apply(next, selectAll = false) {
  setItems(next); cartChanged(next);
  setSelected(current => selectAll ? next.filter(i => i.available && (!new URLSearchParams(location.hash.split('?')[1]).has('select') || i.id === new URLSearchParams(location.hash.split('?')[1]).get('select'))).map(i => i.id) : current.filter(id => next.some(i => i.id === id && i.available)));
 }
 useEffect(() => {
  const controller = new AbortController(); let fetching = false; initial.current = true; setItems([]); setSelected([]); setConfirm(null); setLoading(true); setError('');
  async function load() {
   if (!member.session || locked.current || fetching) return;
   fetching = true; const version = sequence.current;
   try { const next = await memberRequest('mall/cart', { signal: controller.signal }); if (version === sequence.current && !locked.current) { apply(next, initial.current); initial.current = false; setError(''); } }
   catch (e) { if (e.name !== 'AbortError') setError(e.message); } finally { fetching = false; if (!controller.signal.aborted) setLoading(false); }
  }
  refresh.current = load; load(); const timer = setInterval(load, 5000);
  return () => { controller.abort(); clearInterval(timer); };
 }, [member.session?.token]);
 async function mutate(path, method, body, done) {
  if (locked.current) return; locked.current = true; sequence.current++; setBusy(true); setError('');
  try { apply(await memberRequest(path, { method, body })); done?.(); }
  catch (e) { setError(e.message); }
  finally { locked.current = false; setBusy(false); }
 }
 function change(item, quantity) { mutate('mall/cart/items/' + item.id, 'PUT', { quantity }); }
 function remove(item) { mutate('mall/cart/items/' + item.id, 'DELETE', null, () => setRemoved(item)); }
 function undo() { mutate('mall/cart/items', 'POST', { productId: removed.id, quantity: removed.quantity }, () => setRemoved(null)); }
 const chosen = items.filter(i => selected.includes(i.id) && i.available); const amount = total(chosen); const eligible = items.filter(i => i.available);
 const groups = [...new Map(items.map(i => [i.merchantId, i.merchantName])).entries()];
 function toggle(id) { setSelected(ids => ids.includes(id) ? ids.filter(i => i !== id) : [...ids, id]); }
 function checkout() { if (!chosen.length) return; setConfirm(chosen.map(i => ({ ...i }))); submitKey.current = requestKey(); setFormError(''); }
 async function submit(e) {
  e.preventDefault(); if (locked.current) return; locked.current = true; sequence.current++; setBusy(true); setFormError('');
  try {
   const result = await memberRequest('mall/checkouts', { method: 'POST', body: { recipient: form.recipient.trim(), phone: form.phone.trim(), address: deliveryAddress(form), requestKey: submitKey.current, items: confirm.map(i => ({ productId: i.id, quantity: i.quantity, expectedPrice: i.price })) } });
   cartChanged(await memberRequest('mall/cart').catch(() => items.filter(i => !confirm.some(c => c.id === i.id))));
   go(webHref('mall-cashier', { id: result.id }));
  } catch (e) { setFormError(e.message); } finally { locked.current = false; setBusy(false); }
 }
 if (!member.session) return <LoginRequired next={webHref('mall-cart')} />;
 return <section className="mall-page route-view"><div className="section-heading"><div><h1>购物车<span>.</span></h1><p>按店铺整理，选好后一起结算。</p></div><a className="button secondary" href={webHref('mall')}>继续选购</a></div>
  {error && <p className="member-alert" role="alert">{error} <button className="mall-text-button" disabled={busy} onClick={() => refresh.current?.()}>刷新购物车</button></p>}
  {removed && <p className="mall-added-notice" role="status">已移除 {removed.name}<button className="mall-text-button" onClick={undo} disabled={busy}>撤销移除</button></p>}
  {loading ? <p role="status">正在读取购物车…</p> : !items.length ? <div className="empty-state"><ShoppingBag size={38} /><h2>购物车还是空的</h2><p>把喜欢的商品放进来，选好后再结算。</p><a className="button primary" href={webHref('mall')}>去商城选购</a></div> : <>
   <div className="mall-cart-select"><label><input type="checkbox" checked={eligible.length > 0 && chosen.length === eligible.length} disabled={busy || !eligible.length} onChange={e => setSelected(e.target.checked ? eligible.map(i => i.id) : [])} />全选可购买商品</label><span>{items.length} 种商品 · 购物车不预留库存</span></div>
   <div className="mall-cart-layout"><div className="mall-cart-groups">{groups.map(([merchantId, name]) => {
    const group = items.filter(i => i.merchantId === merchantId); const available = group.filter(i => i.available); const all = available.length > 0 && available.every(i => selected.includes(i.id));
    return <section className="mall-cart-group" key={merchantId}><header><label><input type="checkbox" aria-label={`选择${name}全部商品`} checked={all} disabled={busy || !available.length} onChange={e => setSelected(ids => e.target.checked ? [...new Set([...ids, ...available.map(i => i.id)])] : ids.filter(id => !group.some(i => i.id === id)))} /><Store size={17} /><strong>{name}</strong></label><span>店铺发货</span></header>
     {group.map(item => <article className={`mall-cart-item ${!item.available ? 'unavailable' : ''}`} key={item.id}><input type="checkbox" aria-label={`选择${item.name}`} checked={selected.includes(item.id) && item.available} disabled={busy || !item.available} onChange={() => toggle(item.id)} /><img src={mallImage(item.image)} alt="" /><div className="mall-cart-item-main"><h2>{item.name}</h2><p>{item.spec}</p><span className={item.issue ? 'mall-cart-issue' : 'mall-cart-stock'}>{item.issue || `现货 · 剩余 ${item.stock} 件`}</span><div className="mall-cart-item-controls"><CartQuantity item={item} busy={busy || item.stock === 0} change={change} /><button className="mall-text-button" disabled={busy} onClick={() => remove(item)}><Trash2 size={14} />移除</button></div></div><div className="mall-cart-line-amount"><strong>{money(Math.round(Number(item.price) * 100) * item.quantity / 100)}</strong><small>单价 {money(item.price)}</small></div></article>)}
    </section>;
   })}</div><aside className="mall-cart-summary"><h2>结算明细</h2><dl className="mall-facts"><div><dt>已选商品</dt><dd>{chosen.length} 种 / {chosen.reduce((n, i) => n + i.quantity, 0)} 件</dd></div><div><dt>商品金额</dt><dd>{money(amount)}</dd></div><div><dt>运费</dt><dd>¥0.00</dd></div><div className="total"><dt>合计</dt><dd>{money(amount)}</dd></div></dl><button className="button primary" disabled={busy || !chosen.length} onClick={checkout}>去结算<ChevronRight size={17} /></button><p>提交时会重新核对价格与库存。不同商品分别生成订单，由所属店铺处理。</p><span className="mall-environment"><ShieldCheck size={15} />订单信息加密传输</span></aside></div>
  </>}
  {confirm && <Dialog title="确认订单" onClose={() => setConfirm(null)} busy={busy} wide><form className="mall-checkout" onSubmit={submit}><div><h3 className="mall-block-heading"><MapPin size={19} />收货信息</h3><div className="mall-form-pair"><label>收货人<input autoFocus required maxLength={40} autoComplete="name" value={form.recipient} onChange={e => setForm({ ...form, recipient: e.target.value })} placeholder="填写收货人姓名" disabled={busy} /></label><label>手机号码<input type="tel" inputMode="numeric" autoComplete="tel" required pattern="1[3-9][0-9]{9}" maxLength={11} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="填写 11 位手机号" disabled={busy} /></label></div><AddressFields value={form} onChange={setForm} disabled={busy} /><h3 className="mall-block-heading"><Package size={19} />商品清单</h3>{confirm.map(item => <section key={item.id} className="mall-checkout-line"><p className="mall-store-name"><Store size={14} />{item.merchantName}</p><ProductLine order={{ ...item, amount: Math.round(Number(item.price) * 100) * item.quantity / 100 }} /></section>)}</div><aside className="mall-checkout-summary"><h3>订单金额</h3><dl className="mall-facts"><div><dt>商品总额</dt><dd>{money(total(confirm))}</dd></div><div><dt>运费</dt><dd>¥0.00</dd></div><div className="total"><dt>应付金额</dt><dd>{money(total(confirm))}</dd></div></dl><p className="mall-muted">共 {confirm.length} 笔订单，一起付款，分别发货。</p>{formError && <div className="member-alert" role="alert">{formError}<button className="mall-text-button" type="button" disabled={busy} onClick={() => { setConfirm(null); refresh.current?.(); }}>返回购物车核对</button></div>}<button className="button primary" disabled={busy}>{busy ? '正在核对库存并提交…' : '提交订单，去付款'}<ChevronRight size={17} /></button><p className="mall-environment"><Clock3 size={15} />请在 30 分钟内完成付款<br />请核对收货信息后提交</p></aside>{busy && <OrderSubmitting />}</form></Dialog>}
 </section>;
}

export function MallCashier({ member, route }) {
 const [checkout, setCheckout] = useState(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false);
 const [method, setMethod] = useState('微信支付'); const [authorize, setAuthorize] = useState(false); const [now, setNow] = useState(Date.now()); const [attempt, setAttempt] = useState(0);
 const lock = useRef(false); const revision = useRef(0); const expected = useRef(null);
 useEffect(() => {
  const controller = new AbortController(); let fetching = false; setCheckout(null); setLoading(true); setAuthorize(false); setError('');
  async function load() {
   if (!member.session || !route.id || lock.current || fetching) return;
   fetching = true; const version = revision.current;
   try { const result = await memberRequest('mall/checkouts/' + encodeURIComponent(route.id), { signal: controller.signal }); if (version === revision.current && !lock.current) { setCheckout(result); setError(''); } }
   catch (e) { if (e.name !== 'AbortError') setError(e.message); } finally { fetching = false; if (!controller.signal.aborted) setLoading(false); }
  }
  if (!route.id) { setLoading(false); setError('未找到结算单，请从购物车重新进入。'); }
  load(); const timer = setInterval(load, 5000); const clock = setInterval(() => setNow(Date.now()), 1000);
  return () => { controller.abort(); clearInterval(timer); clearInterval(clock); };
 }, [member.session?.token, route.id, attempt]);
 async function pay() {
  if (!checkout?.localSimulation) { setError('支付服务暂不可用，请稍后再试。'); setAuthorize(false); return; }
  if (lock.current) return; lock.current = true; revision.current++; setBusy(true); setError('');
  try { const result = await memberRequest('mall/checkouts/' + checkout.id + '/pay', { method: 'POST', body: { method, expectedAmount: expected.current } }); setCheckout(result); setAuthorize(false); }
  catch (e) { setError(e.message); setAuthorize(false); } finally { lock.current = false; setBusy(false); }
 }
 if (!member.session) return <LoginRequired next={webHref('mall-cashier', { id: route.id })} />;
 const unpaid = checkout?.orders.filter(o => o.status === '待付款') || [];
 const deadline = unpaid.length ? Math.min(...unpaid.map(o => new Date(o.expiresAt).getTime())) : 0;
 const remaining = Math.max(0, Math.ceil((deadline - now) / 1000)); const countdown = `${String(Math.floor(remaining / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`;
 const paid = checkout?.status === 'paid'; const unavailable = checkout?.status === 'unavailable';
 return <section className="mall-page route-view mall-cashier-page"><div className="section-heading"><div><h1>{paid ? '付款完成' : '收银台'}<span>.</span></h1><p>{paid ? '各笔订单已保存支付凭证，可分别查看配送和售后。' : '核对订单后，选择支付方式完成付款。'}</p></div><a className="button secondary" href={webHref('mall-orders')}>我的订单</a></div>
  {error && <p className="member-alert" role="alert">{error} <button className="mall-text-button" disabled={busy} onClick={() => setAttempt(n => n + 1)}>刷新收银台</button></p>}
  {loading ? <p role="status">正在核对订单状态…</p> : checkout && <>
   <div key={checkout.status} className={`mall-checkout-result trade-stage-enter ${paid ? 'paid' : ''}`}>{paid ? <PaymentResult key="paid" compact /> : remaining && !unavailable ? <PaymentResult key="created" compact label="订单已生成" /> : <Clock3 size={32} />}<div><h2>{paid ? '支付成功' : unavailable ? '部分订单已关闭' : remaining ? '订单已提交，等待付款' : '付款时限已过'}</h2><p>{paid ? `共 ${checkout.orders.length} 笔订单，已分别通知所属店铺。` : unavailable ? '本次合并付款已停止，请前往我的订单查看每笔订单的状态。' : remaining ? <>请在 <strong>{countdown}</strong> 内完成付款，超时未付的订单将关闭。</> : '请前往我的订单核对状态，已关闭订单可重新选购。'}</p></div><strong className="mall-group-total">{money(paid ? checkout.total : checkout.pendingAmount)}</strong></div>
   <div className="mall-cart-layout"><div className="mall-cashier-order-list"><div className="mall-cashier-list-heading"><h2>订单明细</h2><span>{checkout.orders.length} 笔订单 · 分别发货</span></div>{checkout.orders.map(order => <article className="mall-cashier-order" key={order.id}><header><span><Store size={15} />{order.merchantName}</span><strong>{order.status}</strong></header><ProductLine order={order} /><dl className="mall-facts"><div><dt>订单编号</dt><dd>{order.id}</dd></div>{order.paymentRef && <><div><dt>支付方式</dt><dd>{order.paymentMethod || '支付记录'}</dd></div><div><dt>交易流水</dt><dd>{transactionReference(order.paymentRef)}</dd></div></>}</dl><a className="mall-order-link" href={webHref('mall-orders', { order: order.id })}>查看订单<ChevronRight size={15} /></a></article>)}</div>
   <aside className="mall-cart-summary"><h2>{paid ? '支付信息' : '选择支付方式'}</h2>{paid ? <><dl className="mall-facts"><div><dt>本次订单总额</dt><dd>{money(checkout.total)}</dd></div><div><dt>剩余待付</dt><dd>¥0.00</dd></div></dl><a className="button primary" href={webHref('mall-orders')}>查看我的订单</a><a className="mall-order-link" href={webHref('mall')}>继续购物<ChevronRight size={15} /></a></> : unavailable || !remaining ? <a className="button primary" href={webHref('mall-orders')}>前往我的订单</a> : <><div className="mall-payment-options">{['微信支付', '支付宝'].map(value => <button key={value} className={method === value ? 'selected' : ''} aria-pressed={method === value} disabled={busy || authorize} onClick={() => setMethod(value)}><span className={`mall-provider ${value === '支付宝' ? 'alipay' : ''}`}><CreditCard size={23} /></span><span><strong>{value}</strong><small>在线支付</small></span><span className="mall-radio">{method === value && <Check size={14} />}</span></button>)}</div><dl className="mall-facts"><div><dt>待付款订单</dt><dd>{unpaid.length} 笔</dd></div><div className="total"><dt>应付金额</dt><dd>{money(checkout.pendingAmount)}</dd></div></dl><button className="button primary" disabled={busy || !checkout.localSimulation} onClick={() => { expected.current = checkout.pendingAmount; setAuthorize(true); }}>确认金额，去付款<ChevronRight size={17} /></button>{!checkout.localSimulation && <p role="status">支付服务暂不可用，请稍后再试。订单可在“我的订单”中查看。</p>}</>}<p className="mall-environment"><ShieldCheck size={15} />{paid ? '支付信息已更新，可查看订单记录。' : '请通过支付页面完成付款，结果以订单状态为准。'}</p></aside></div>
  </>}
  {authorize && checkout && <Dialog title="确认本次支付" onClose={() => setAuthorize(false)} busy={busy}><div className="mall-payment-result"><ShieldCheck size={44} /><h3>{method}</h3><p>将为 {unpaid.length} 笔待付款订单完成付款。</p><strong className="mall-paid-amount">{money(expected.current)}</strong><p className="mall-environment">请确认支付金额和订单信息。</p>{error && <p className="member-alert" role="alert">{error}</p>}<div className="mall-actions"><button className="button secondary" disabled={busy} onClick={() => setAuthorize(false)}>返回核对</button><button className="button primary" disabled={busy || !checkout.localSimulation || !remaining || checkout.status !== 'pending' || checkout.pendingAmount !== expected.current} onClick={pay}>{busy ? '正在确认支付结果…' : '确认支付'}</button></div>{checkout.pendingAmount !== expected.current && <p role="alert">待付金额已变化，请返回核对。</p>}{busy && <OrderSubmitting title="正在确认支付" copy="正在核对付款结果，请勿重复提交。" />}</div></Dialog>}
 </section>;
}
