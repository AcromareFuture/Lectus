import React, { useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronRight, Clock3, CreditCard, MapPin, Package, ShieldCheck, Store, Truck, X } from 'lucide-react';
import { memberRequest } from './member-api';
import { webHref } from './web-route';
import './mall-pages.css';
import './order-history.css';
import { OrderSubmitting, PaymentResult } from './TradeFeedback';
import { eventDetail, transactionReference, businessNote } from '../../../packages/business-records';

export const money = value => Number(value).toLocaleString('zh-CN', { style: 'currency', currency: 'CNY' });
export const mallImage = name => typeof name === 'string' && /^\/api\/catalog\/assets\/[a-zA-Z0-9_./-]+$/.test(name) && !name.includes('..') ? name : import.meta.env.BASE_URL + 'images/' + name;
const date = value => value ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(value)) : '—';
const statusCopy = {
 '待付款': ['等待付款', '请在付款时限内完成支付，超时订单将自动关闭。'],
 '待发货': ['付款成功，等待发货', '商家正在处理订单，发货后可在这里查看配送进度。'],
 '运输中': ['商品已发出', '请留意配送进度，收到商品并确认无误后再确认收货。'],
 '已完成': ['交易已完成', '感谢你的购买，订单和交易凭证已保留。'],
 '退款中': ['退款申请处理中', '平台正在审核申请，处理结果会同步到订单。'],
 '已退款': ['退款已完成', '退款已按原支付方式处理，可在售后记录中查看凭证。'],
 '已取消': ['订单已关闭', '你可以返回商城重新选购。'],
};
export function Dialog({ title, onClose, busy, children, wide = false }) {
 const ref = useRef(null); const titleId = useId();
 useEffect(() => { const dialog = ref.current; dialog.showModal(); return () => dialog.close(); }, []);
 return <dialog ref={ref} className={`mall-dialog ${wide ? 'mall-dialog-wide' : ''}`} aria-labelledby={titleId} onCancel={e => { e.preventDefault(); if (!busy) onClose(); }} onClick={e => { if (e.target === ref.current && !busy) onClose(); }}>
  <header className="mall-dialog-heading"><h2 id={titleId}>{title}</h2><button type="button" className="mall-icon-button" aria-label="关闭" disabled={busy} onClick={onClose}><X size={21} /></button></header>{children}
 </dialog>;
}
export function ProductLine({ order }) {
 return <div className="mall-order-product"><img src={mallImage(order.image)} alt="" /><div><h3>{order.productName || order.name}</h3><p>{order.variant || order.spec}</p><span>数量 × {order.quantity || 1}</span></div><strong>{money(order.amount ?? order.price)}</strong></div>;
}
function Timeline({ order }) {
 const events = order.events?.length ? order.events : [{ key: 'created', title: '订单已提交', detail: '历史订单', time: order.createdAt }];
 return <ol className="mall-timeline">{events.map((event, i) => <li key={event.key} className={i === 0 ? 'current' : ''}><span className="mall-timeline-dot" /><div><strong>{event.title}</strong><time>{date(event.time)}</time><p>{eventDetail(event)}</p></div></li>)}</ol>;
}
function OrderProgress({ order }) {
 const current = ({ 待付款: 0, 待发货: 1, 运输中: 2, 已完成: 3 })[order.status];
 if (current === undefined) return null;
 return <ol className="mall-progress" aria-label="订单进度">{['提交订单', '付款成功', '商家发货', '确认收货'].map((step, i) => <li key={step} className={i <= current ? 'done' : ''}><span>{i <= current ? <Check size={15} /> : i + 1}</span><small>{step}</small></li>)}</ol>;
}
function Receipt({ order }) {
 return <dl className="mall-facts"><div><dt>订单编号</dt><dd>{order.id}</dd></div><div><dt>下单时间</dt><dd>{date(order.createdAt)}</dd></div>{order.paidAt && <><div><dt>支付方式</dt><dd>{order.paymentMethod || '支付记录'}</dd></div><div><dt>支付时间</dt><dd>{date(order.paidAt)}</dd></div><div><dt>交易流水号</dt><dd>{transactionReference(order.paymentRef) || '历史订单未记录'}</dd></div></>}{order.closedAt && <div><dt>关闭原因</dt><dd>{order.closeReason}</dd></div>}<div><dt>商品金额</dt><dd>{money(order.amount)}</dd></div><div><dt>配送费用</dt><dd>¥0.00</dd></div><div className="total"><dt>{order.paidAt ? '实付金额' : '应付金额'}</dt><dd>{money(order.amount)}</dd></div></dl>;
}
function Aftersale({ value }) {
 if (!value) return null;
 return <section className="mall-detail-section"><h3>售后记录<span className="mall-inline-tag">{value.status === '已处理' ? '退款完成' : value.status}</span></h3><dl className="mall-facts"><div><dt>服务单号</dt><dd>{value.id}</dd></div><div><dt>申请时间</dt><dd>{date(value.createdAt)}</dd></div><div><dt>申请原因</dt><dd>{businessNote(value.reason)}</dd></div><div><dt>退款金额</dt><dd>{money(value.amount)}</dd></div><div><dt>退回方式</dt><dd>{value.paymentMethod || '原支付方式'} · 原路退回</dd></div>{value.result && <div><dt>处理说明</dt><dd>{businessNote(value.result)}</dd></div>}{value.resolvedAt && <div><dt>处理时间</dt><dd>{date(value.resolvedAt)}</dd></div>}{value.refundRef && <div><dt>退款凭证</dt><dd>{transactionReference(value.refundRef)}</dd></div>}</dl></section>;
}
export function MallOrders({ member, compact = false }) {
 const [orders, setOrders] = useState([]); const [loadError, setLoadError] = useState(''); const [error, setError] = useState(''); const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false);
 const [local, setLocal] = useState(false); const [selectedId, setSelectedId] = useState(() => new URLSearchParams(location.hash.split('?')[1]).get('pay') || new URLSearchParams(location.hash.split('?')[1]).get('order'));
 const [mode, setMode] = useState(() => new URLSearchParams(location.hash.split('?')[1]).has('pay') ? 'pay' : 'detail');
 const [query, setQuery] = useState(''); const [page, setPage] = useState(0);
 const [filter, setFilter] = useState('全部'); const [reason, setReason] = useState(''); const [method, setMethod] = useState('微信支付'); const [authorize, setAuthorize] = useState(false); const [now, setNow] = useState(Date.now());
 const refresh = useRef(null); const lock = useRef(false);
 useEffect(() => { const c = new AbortController(); let fetching = false;
  async function load() { if (fetching || !member.session) return; fetching = true; try { setOrders(await memberRequest('mall/orders', { signal: c.signal })); setLoadError(''); } catch(e) { if(e.name !== 'AbortError') setLoadError(e.message); } finally { fetching = false; if(!c.signal.aborted) setLoading(false); } }
  refresh.current = load; memberRequest('mall/public/config', { anonymous: true, signal: c.signal }).then(v => setLocal(v.localSimulation)).catch(e => { if(e.name !== 'AbortError') setLoadError(e.message); }); load(); const t = setInterval(load, 5000); const clock = setInterval(() => setNow(Date.now()), 1000); return () => { clearInterval(t); clearInterval(clock); c.abort(); };
 }, [member.session?.token]);
 const order = orders.find(o => o.id === selectedId);
 const remaining = order?.expiresAt ? Math.max(0, Math.ceil((new Date(order.expiresAt).getTime() - now) / 1000)) : 0;
 const countdown = `${String(Math.floor(remaining / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`;
 function close() { setSelectedId(null); setError(''); setAuthorize(false); if (location.hash.includes('?pay=')) history.replaceState(null, '', webHref('mall-orders')); }
 function open(o, nextMode) { setSelectedId(o.id); setMode(nextMode); setError(''); setReason(''); setAuthorize(false); }
 async function act(action, detail) {
  if (action === 'pay' && !local) { setError('支付服务暂不可用，请稍后再试。'); return; }
  if (lock.current || !order) return; lock.current = true; setBusy(true); setError('');
  try {
   const result = await memberRequest('mall/orders/' + order.id + '/action', { method: 'POST', body: { action, reason: detail } });
   setOrders(list => list.map(o => o.id === result.id ? result : o)); setAuthorize(false);
   if (action === 'pay' && result.status === '待发货') setMode('success');
   else { setMode('detail'); if (action === 'pay' && result.status === '已取消') setError('付款时限已过，订单已关闭，请重新下单。'); }
  } catch(e) { setError(e.message); } finally { lock.current = false; setBusy(false); }
 }
 if (!member.session) return <div className="empty-state"><h1>我的订单</h1><p>登录后查看订单、配送和售后状态。</p><a className="button primary" href={webHref('login', { next: webHref('mall-orders') })}>登录 / 注册</a></div>;
 const filtered = orders.filter(o => (filter === '全部' || (filter === '退款 / 售后' ? o.aftersale : o.status === filter)) && (!query.trim() || [o.productName, o.id, o.merchantName, o.variant].some(v => v?.toLowerCase().includes(query.trim().toLowerCase()))));
 const lastPage = Math.max(0, Math.ceil(filtered.length / 6) - 1); const currentPage = Math.min(page, lastPage);
 const visible = compact ? filtered.slice(0, 3) : filtered.slice(currentPage * 6, currentPage * 6 + 6);
 function actions(o) { return <div className="mall-actions"><button className="button secondary" onClick={() => open(o, 'detail')}>订单详情</button>{o.status === '待付款' && <><button className="mall-text-button" onClick={() => open(o, 'cancel')}>取消订单</button><button className="button primary" onClick={() => open(o, 'pay')}>立即付款</button></>}{o.status === '待发货' && !o.aftersale && <button className="button secondary" onClick={() => open(o, 'refund')}>申请退款</button>}{o.status === '运输中' && <button className="button primary" onClick={() => open(o, 'receive')}>确认收货</button>}</div>; }
 return <section className={`mall-page order-history route-view ${compact ? 'order-history-compact' : ''}`}><div className="section-heading"><div>{compact ? <h2>最近订单</h2> : <h1>我的订单<span>.</span></h1>}<p>查看购买记录、配送进度和售后处理结果。</p></div><a className="button secondary" href={webHref(compact ? 'mall-orders' : 'mall')}>{compact ? '全部订单' : '继续购物'}</a></div><div className="mall-orders-toolbar"><div className="mall-tabs" role="group" aria-label="筛选订单">{['全部', '待付款', '待发货', '运输中', '已完成', '退款 / 售后', '已取消'].map(tab => <button key={tab} aria-pressed={filter === tab} onClick={() => { setFilter(tab); setPage(0); }}>{tab}<span>{orders.filter(o => tab === '全部' || (tab === '退款 / 售后' ? o.aftersale : o.status === tab)).length}</span></button>)}</div></div>{loadError && <p role="alert" className="member-alert">{loadError}</p>}{!compact && <div className="order-search"><label htmlFor="order-search">搜索订单</label><input id="order-search" type="search" value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} placeholder="商品名称、店铺或订单号" maxLength={100} /><span>{filtered.length} 笔订单</span></div>}{loading ? <p className="order-loading" role="status">正在读取订单…</p> : !visible.length ? <div className="empty-state"><Package size={34} /><h2>{query ? '没有找到相关订单' : orders.length ? '暂无此类订单' : '还没有订单'}</h2><p>{query ? '换个关键词，或清除搜索条件。' : '购买记录、配送和售后进度会保存在这里。'}</p>{query ? <button className="button secondary" onClick={() => { setQuery(''); setPage(0); }}>清除搜索</button> : <a className="button secondary" href={webHref('home')}>浏览商品</a>}</div> : <div className="order-history-list">{visible.map(o => <article className="order-history-row" key={o.id} aria-label={`${o.productName}，${o.status}`}>
 <div className="order-row-meta"><time>{o.time}</time><span>{o.merchantName}</span><span className="order-row-number" title={o.id}>订单尾号 {o.id.slice(-12)}</span></div>
 <div className="order-row-body"><div className="order-row-product"><div className="order-row-image"><img src={mallImage(o.image)} alt={o.productName} loading="lazy" /></div><div><h3>{o.productName}</h3><p>{o.variant}</p><span>数量 {o.quantity} 件</span><p className="order-row-hint">{o.status === '运输中' ? `${o.carrier} · ${['等待揽收', '已揽收', '运输中', '派送中'][o.deliveryStage] || '配送中'}` : o.closeReason || statusCopy[o.status]?.[1]}</p></div></div>
 <div className="order-row-amount"><span>{o.status === '待付款' ? '应付金额' : o.status === '已取消' ? '订单金额' : '实付金额'}</span><strong>{money(o.amount)}</strong>{o.status === '已退款' && <small>已原路退回</small>}</div><div className="order-row-controls"><strong className="order-row-status" data-state={o.status}>{o.status}</strong>{actions(o)}</div></div>
 </article>)}</div>}{!compact && filtered.length > 6 && <div className="pagination order-pagination"><span>第 {currentPage + 1} / {lastPage + 1} 页</span><div><button className="button secondary" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>上一页</button><button className="button secondary" disabled={currentPage === lastPage} onClick={() => setPage(currentPage + 1)}>下一页</button></div></div>}

 {order && <Dialog title={({ pay: '收银台', success: '支付结果', refund: '申请退款', cancel: '取消订单', receive: '确认收货', detail: '订单详情' })[mode]} onClose={close} busy={busy} wide={['detail', 'pay'].includes(mode)}>
 {error && <p role="alert" className="member-alert">{error}</p>}
 {mode === 'pay' ? <div className="mall-cashier"><div className="mall-cashier-main"><div className="mall-cashier-status"><Clock3 size={20} /><div><strong>{order.status === '待付款' ? remaining ? '订单已提交，请完成付款' : '付款时限已过' : statusCopy[order.status]?.[0]}</strong><p>{order.status === '待付款' && remaining ? <>剩余付款时间 <b>{countdown}</b></> : '请返回订单详情查看最新状态。'}</p></div></div><h3>选择支付方式</h3><div className="mall-payment-options">{['微信支付', '支付宝'].map(m => <button className={method === m ? 'selected' : ''} key={m} aria-pressed={method === m} disabled={busy || authorize} onClick={() => setMethod(m)}><span className={`mall-provider ${m === '支付宝' ? 'alipay' : ''}`}><CreditCard size={24} /></span><span><strong>{m}</strong><small>在线支付</small></span><span className="mall-radio">{method === m && <Check size={14} />}</span></button>)}</div>{authorize ? <div className="mall-payment-authorize"><ShieldCheck size={26} /><h3>确认本次支付</h3><p>使用{method}支付 {money(order.amount)}。</p><button className="button primary" disabled={busy || !local || !remaining || order.status !== '待付款'} onClick={() => act('pay', method)}>{busy ? '正在确认支付结果…' : '确认支付'}</button><button className="mall-text-button" disabled={busy} onClick={() => setAuthorize(false)}>更换支付方式</button></div> : <button className="button primary mall-pay-button" disabled={!local || !remaining || order.status !== '待付款' || busy} onClick={() => setAuthorize(true)}>前往付款 {money(order.amount)}<ChevronRight size={17} /></button>}{!local && <p className="mall-muted" role="status">支付服务暂不可用，请稍后再试。</p>}<p className="mall-environment"><ShieldCheck size={15} />请通过支付页面完成付款，结果以订单状态为准。</p>{busy && <OrderSubmitting title="正在确认支付" copy="正在核对付款结果，请勿重复提交。" />}</div><aside className="mall-checkout-summary"><h3>订单摘要</h3><ProductLine order={order} /><p className="mall-store-name"><Store size={15} />{order.merchantName}</p><dl className="mall-facts"><div><dt>商品金额</dt><dd>{money(order.amount)}</dd></div><div><dt>运费</dt><dd>¥0.00</dd></div><div className="total"><dt>应付金额</dt><dd>{money(order.amount)}</dd></div></dl><p className="mall-small-id">订单号 {order.id}</p></aside></div>
 : mode === 'success' ? <div className="mall-payment-result"><PaymentResult /><h3>支付成功</h3><p>订单已通知商家，正在等待发货。</p><strong className="mall-paid-amount">{money(order.amount)}</strong><Receipt order={order} /><p className="mall-environment">支付信息已更新，可查看订单记录。</p><div className="mall-actions"><button className="button secondary" onClick={close}>返回订单列表</button><button className="button primary" onClick={() => setMode('detail')}>查看订单</button></div></div>
 : mode === 'refund' ? <form className="mall-refund-form" onSubmit={e => { e.preventDefault(); act('refund', reason); }}><ProductLine order={order} /><dl className="mall-facts"><div className="total"><dt>退款金额</dt><dd>{money(order.amount)}</dd></div><div><dt>退回方式</dt><dd>{order.paymentMethod || '原支付方式'} · 原路退回</dd></div></dl><label>退款原因<textarea autoFocus required minLength="4" maxLength="200" rows="4" placeholder="请描述退款原因，至少 4 个字" value={reason} onChange={e => setReason(e.target.value)} /></label><p className="mall-muted">提交后订单将暂停发货，由平台审核。处理结果可在订单详情中查看。</p><div className="mall-actions"><button className="button secondary" type="button" disabled={busy} onClick={() => setMode('detail')}>暂不申请</button><button className="button primary" disabled={busy}>{busy ? '正在提交…' : '提交退款申请'}</button></div></form>
 : ['cancel', 'receive'].includes(mode) ? <div className="mall-confirm"><ProductLine order={order} /><h3>{mode === 'cancel' ? '确定取消这笔订单？' : '确认已经收到商品？'}</h3><p>{mode === 'cancel' ? '取消后订单将关闭，已预留的商品库存会释放。' : '请确认商品已送达，且数量、规格和外观无误。确认后订单将完成。'}</p><div className="mall-actions"><button className="button secondary" disabled={busy} onClick={() => setMode('detail')}>返回</button><button className="button primary" disabled={busy} onClick={() => act(mode)}>{busy ? '处理中…' : mode === 'cancel' ? '确定取消' : '确认收货'}</button></div></div>
 : <><div className="mall-status-banner"><Package size={28} /><div><h3>{statusCopy[order.status]?.[0]}</h3><p>{order.closeReason || statusCopy[order.status]?.[1]}</p></div><span className="mall-inline-tag">{order.status}</span></div><OrderProgress order={order} /><div className="mall-detail-columns"><div><section className="mall-detail-section"><h3><Store size={18} />{order.merchantName}</h3><ProductLine order={order} /></section><section className="mall-detail-section"><h3><MapPin size={18} />收货信息</h3><p>{order.recipient} · {order.phone}</p><p>{order.address}</p></section>{order.tracking && <section className="mall-detail-section"><h3><Truck size={18} />配送信息</h3><p>{order.carrier} · {order.tracking}</p><p className="mall-muted">{['等待快递揽收', '快递已揽收', '包裹运输中', '正在派送', '已签收'][order.deliveryStage]}</p></section>}<Aftersale value={order.aftersale} /><section className="mall-detail-section"><h3>交易信息</h3><Receipt order={order} /></section></div><aside className="mall-history"><h3>订单进度</h3><Timeline order={order} /></aside></div><div className="mall-detail-actions">{order.status === '待付款' && <><button className="button secondary" onClick={() => setMode('cancel')}>取消订单</button><button className="button primary" onClick={() => setMode('pay')}>立即付款</button></>}{order.status === '待发货' && !order.aftersale && <button className="button secondary" onClick={() => setMode('refund')}>申请退款</button>}{order.status === '运输中' && <button className="button primary" onClick={() => setMode('receive')}>确认收货</button>}<button className="button secondary" onClick={close}>关闭详情</button></div></>}
 </Dialog>}
 </section>;
}
