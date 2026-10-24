import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowRight, LogOut, MapPin, Minus, Package, Plus, ShoppingBag, Trash2, UserRound, X } from "lucide-react";
import { createApi } from "./api";
import { useI18n } from "./i18n";
import "./account.css";

const tokenKey = "lectus-api-session";
const storedToken = () => { try { return sessionStorage.getItem(tokenKey); } catch { return null; } };
const storeToken = (value) => { try { value ? sessionStorage.setItem(tokenKey, value) : sessionStorage.removeItem(tokenKey); } catch { /* Current tab remains usable without session storage. */ } };
const yuan = (cents) => `¥${(cents / 100).toLocaleString("zh-CN", { minimumFractionDigits: 2 })}`;
const image = (name) => `${import.meta.env.BASE_URL}images/${name}.webp`;

export default function AccountPage() {
  const { t } = useI18n();
  const token = useRef(storedToken());
  const alive = useRef(true);
  const lock = useRef(false);
  const orderKey = useRef(null);
  const [user, setUser] = useState(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [register, setRegister] = useState(false);
  const [tab, setTab] = useState("products");
  const [products, setProducts] = useState([]);
  const [cart, setCart] = useState([]);
  const [addresses, setAddresses] = useState([]);
  const [orders, setOrders] = useState([]);
  const [orderPage, setOrderPage] = useState(0);
  const [moreOrders, setMoreOrders] = useState(false);
  const [pick, setPick] = useState(null);
  const [skuId, setSkuId] = useState("");
  const [addAddress, setAddAddress] = useState(false);
  const [addressId, setAddressId] = useState("");
  const api = useMemo(() => createApi({ base: import.meta.env.VITE_API_BASE_URL || new URL("api/", document.baseURI).pathname, getToken: () => token.current }), []);
  const reload = useCallback(async () => {
    const [current, catalog, bag, saved, history] = await Promise.all([
      api.me(), api.products({ size: 100 }), api.cart(), api.addresses(), api.orders(),
    ]);
    if (!alive.current) return;
    setUser(current); setProducts(catalog.items); setCart(bag); setAddresses(saved); setOrders(history);
    setOrderPage(0); setMoreOrders(history.length === 20);
    setAddressId((id) => saved.some((item) => item.id === id) ? id : saved.find((item) => item.isDefault)?.id || saved[0]?.id || "");
  }, [api]);
  const handleError = useCallback((failure) => {
    if (!alive.current) return;
    if (failure.status === 401) { token.current = null; storeToken(null); setUser(null); setCart([]); setOrders([]); setAddresses([]); }
    setError(failure.message);
  }, []);
  useEffect(() => {
    alive.current = true;
    (async () => {
      try { if (token.current) await reload(); else await api.health(); }
      catch (failure) { handleError(failure); }
      finally { if (alive.current) setBusy(false); }
    })();
    return () => { alive.current = false; };
  }, [api, reload, handleError]);
  useEffect(() => {
    if (pick) document.querySelector(".account-picker")?.scrollIntoView({ block: "nearest", behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth" });
  }, [pick]);
  async function run(action) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError(""); setNotice("");
    try { await action(); }
    catch (failure) { handleError(failure); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  async function authenticate(event) {
    event.preventDefault();
    const fields = new FormData(event.currentTarget);
    const form = event.currentTarget;
    await run(async () => {
      const body = { email: fields.get("email"), password: fields.get("password") };
      if (register) body.displayName = fields.get("displayName");
      const session = await (register ? api.register(body) : api.login(body));
      token.current = session.token; storeToken(session.token);
      form.reset(); await reload();
    });
  }
  async function quantity(row, value) {
    orderKey.current = null;
    await run(async () => {
      if (value === 0) await api.deleteCart(row.skuId);
      else await api.putCart({ skuId: row.skuId, quantity: value, checked: row.checked });
      setCart(await api.cart());
    });
  }
  const selected = cart.filter((row) => row.checked);
  const total = selected.reduce((sum, row) => sum + row.unitPriceCents * row.quantity, 0);
  return <div className="account-page page-pad" aria-busy={busy}>
    {error && <div className="account-message account-error" role="alert"><p>{t(error)}</p><button disabled={busy} onClick={() => run(async () => { if (token.current) await reload(); else await api.health(); })}>{t("重新连接")}</button></div>}
    {notice && <p className="account-message" role="status">{t(notice)}</p>}
    {!user ? <>
      <div className="account-welcome"><UserRound size={27} strokeWidth={1.4}/><h1>{t(register ? "创建莱择账号" : "欢迎回到莱择")}<span className="pink">.</span></h1><p>{t("登录后，在账号中保存购物袋、地址和订单。")}</p></div>
      <form className="form-stack account-auth" onSubmit={authenticate}>
        <fieldset disabled={busy}>
          {register && <label>{t("昵称")}<input name="displayName" autoComplete="nickname" required maxLength={40}/></label>}
          <label>{t("邮箱")}<input name="email" type="email" autoComplete="email" required maxLength={254} placeholder="you@example.com"/></label>
          <label>{t("密码")}<input name="password" type="password" autoComplete={register ? "new-password" : "current-password"} required minLength={register ? 10 : 1} maxLength={64}/></label>
          {register && <small>{t("密码至少10个字符，建议使用英文、数字与符号。")}</small>}
          <button type="submit" className="black-button">{t(busy ? "连接中…" : register ? "注册并登录" : "登录")}<ArrowRight size={18}/></button>
        </fieldset>
      </form>
      <button className="account-switch" disabled={busy} onClick={() => { setRegister(!register); setError(""); }}>{t(register ? "已有账号，去登录" : "还没有账号？立即注册")}</button>
      <p className="account-footnote">{t("登录后可管理购物袋、收货地址和订单。")}</p>
    </> : <>
      <div className="account-heading"><div><h1 data-i18n-skip>{user.displayName}</h1><p data-i18n-skip>{user.email}</p></div><button className="icon-button" aria-label={t("退出登录")} disabled={busy} onClick={() => run(async () => { await api.logout(); token.current = null; storeToken(null); setUser(null); setProducts([]); setCart([]); setOrders([]); setAddresses([]); orderKey.current = null; })}><LogOut size={20}/></button></div>
      <nav className="account-tabs" aria-label={t("账号商城导航")}>
        {[["products", "选购"], ["cart", "购物袋"], ["orders", "订单"]].map(([id, text]) => <button key={id} disabled={busy} aria-current={tab === id ? "page" : undefined} onClick={() => { setTab(id); setPick(null); setError(""); }}>{t(text)}{id === "cart" && cart.length > 0 && <small>{cart.reduce((sum, row) => sum + row.quantity, 0)}</small>}</button>)}
      </nav>
      <p className="account-footnote">{t("购物袋和订单将保存在当前账号中。")}</p>
      {tab === "products" && <>
        <div className="account-product-grid">{products.map((product) => <button key={product.id} className="account-product" disabled={busy} onClick={() => { setPick(product); setSkuId(""); setNotice(""); }}><img src={image(product.image)} alt={product.name} loading="lazy"/><small>{product.brand}</small><b data-i18n-skip>{product.name}</b><strong>{yuan(product.priceCents)}<Plus size={18}/></strong></button>)}</div>
        {pick && <section className="account-picker" aria-label={t("选择规格")}><div className="account-picker-title"><h2 data-i18n-skip>{pick.name}</h2><button className="icon-button" aria-label={t("关闭规格选择")} onClick={() => setPick(null)}><X size={18}/></button></div><label>{t("尺码 / 颜色")}<select value={skuId} onChange={(event) => setSkuId(event.target.value)} disabled={busy}><option value="">{t("请选择规格")}</option>{pick.skus.map((sku) => <option key={sku.id} value={sku.id} disabled={sku.stock < 1}>{sku.size} / {sku.color} · {t("库存")} {sku.stock}</option>)}</select></label><button className="black-button" disabled={busy || !skuId} onClick={() => run(async () => { const existing = cart.find((row) => row.skuId === skuId); await api.putCart({ skuId, quantity: (existing?.quantity || 0) + 1, checked: true }); await reload(); setPick(null); setNotice("已加入账号购物袋"); })}>{t("加入购物袋")}<ShoppingBag size={17}/></button></section>}
      </>}
      {tab === "cart" && <>
        {cart.length === 0 ? <div className="account-empty"><ShoppingBag size={30}/><h2>{t("还没把喜欢装进来")}</h2><button className="outline-button" onClick={() => setTab("products")}>{t("去逛逛")}</button></div> : <>
          {cart.map((row) => <article className="account-cart-row" key={row.skuId}><input type="checkbox" aria-label={`${t("选择")} ${row.name}`} checked={row.checked} disabled={busy} onChange={(event) => { orderKey.current = null; const checked = event.target.checked; run(async () => setCart(await api.putCart({ skuId: row.skuId, quantity: row.quantity, checked }))); }}/><img src={image(row.image)} alt=""/><div><b data-i18n-skip>{row.name}</b><small>{row.size} / {row.color}</small><strong>{yuan(row.unitPriceCents)}</strong><div className="account-quantity"><button disabled={busy} aria-label={t("减少数量")} onClick={() => quantity(row, row.quantity - 1)}><Minus size={15}/></button><span>{row.quantity}</span><button disabled={busy || row.quantity >= Math.min(99, row.stock)} aria-label={t("增加数量")} onClick={() => quantity(row, row.quantity + 1)}><Plus size={15}/></button><button disabled={busy} aria-label={t("删除商品")} onClick={() => quantity(row, 0)}><Trash2 size={15}/></button></div></div></article>)}
          <section className="account-delivery"><h2><MapPin size={18}/>{t("收货地址")}</h2>{addresses.length > 0 && <select aria-label={t("选择收货地址")} value={addressId} disabled={busy} onChange={(event) => { setAddressId(event.target.value); orderKey.current = null; }}>{addresses.map((item) => <option key={item.id} value={item.id}>{item.recipient} · {item.region} {item.detail}</option>)}</select>}<button className="account-text-button" onClick={() => setAddAddress(!addAddress)} disabled={busy}>{t(addAddress ? "取消" : "添加收货地址")}<Plus size={15}/></button>
            {addAddress && <form className="form-stack" onSubmit={(event) => { event.preventDefault(); const fields = new FormData(event.currentTarget); run(async () => { const saved = await api.saveAddress({ recipient: fields.get("recipient"), phone: fields.get("phone"), region: fields.get("region"), detail: fields.get("detail"), isDefault: true }); orderKey.current = null; await reload(); setAddressId(saved.id); setAddAddress(false); }); }}><fieldset disabled={busy}>{[["recipient", "收件人", "text", 80], ["phone", "联系电话", "tel", 30], ["region", "所在地区", "text", 200], ["detail", "详细地址", "text", 300]].map(([name, label, type, max]) => <label key={name}>{t(label)}<input name={name} type={type} required maxLength={max} minLength={name === "region" ? 2 : name === "detail" ? 4 : 1}/></label>)}<button className="outline-button" type="submit">{t("保存地址")}</button></fieldset></form>}
          </section>
          <div className="account-checkout"><div><small>{t("商品合计")}</small><strong>{yuan(total)}</strong></div><button className="black-button" disabled={busy || !addressId || selected.length === 0} onClick={() => run(async () => { orderKey.current ||= crypto.randomUUID(); const order = await api.createOrder({ addressId, items: selected.map((row) => ({ skuId: row.skuId, quantity: row.quantity })), fromCart: true }, orderKey.current); orderKey.current = null; await reload(); setTab("orders"); setNotice("订单已保存，请前往订单列表查看。"); })}>{t(busy ? "提交中…" : "提交订单")}<ArrowRight size={17}/></button></div>
          {!addressId && <p className="account-footnote">{t("请先添加收货地址。")}</p>}
        </>}
      </>}
      {tab === "orders" && <>{orders.length === 0 ? <div className="account-empty"><Package size={30}/><h2>{t("还没有订单")}</h2></div> : orders.map((order) => <article className="account-order" key={order.id}><div className="account-order-top"><small data-i18n-skip>{order.id.slice(0, 8).toUpperCase()}</small><b>{t(order.status === "CANCELLED" ? "已取消" : "待付款")}</b></div>{order.items.map((row) => <div className="account-order-item" key={row.skuId}><img src={image(row.image)} alt=""/><div><b data-i18n-skip>{row.name}</b><small>{row.size} / {row.color} × {row.quantity}</small></div></div>)}<p className="account-order-address" data-i18n-skip>{order.address.recipient} · {order.address.region} {order.address.detail}</p><div className="account-order-bottom"><strong>{yuan(order.totalCents)}</strong>{order.status === "PENDING_PAYMENT" && <button className="outline-button" disabled={busy} onClick={() => run(async () => { await api.cancelOrder(order.id); await reload(); setNotice("订单已取消，库存已退回。"); })}>{t("取消订单")}</button>}</div></article>)}{moreOrders && <button className="outline-button" disabled={busy} onClick={() => run(async () => { const next = await api.orders(orderPage + 1); setOrders((previous) => [...previous, ...next]); setOrderPage(orderPage + 1); setMoreOrders(next.length === 20); })}>{t("加载更多订单")}</button>}</>}
    </>}
  </div>;
}
