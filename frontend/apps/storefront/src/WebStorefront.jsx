import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowRight, ArrowUpRight, Box, Check, ChevronLeft, ChevronRight, Heart, MoveUpRight, Search, SlidersHorizontal, Store, Settings, ShoppingBag, UserRound, X } from 'lucide-react';
import { catalogRequest, assetUrl } from './catalog-api';
import ShoeViewer from './ShoeViewer';
import { I18nProvider, useI18n } from './i18n';
import { getMeshyPreviewModel } from './meshy-preview';
import { featuredEditions, getFeaturedEdition } from './web-featured';
import { readWebRoute, webHref } from './web-route';
import { previewPrice } from './web-pricing';
import { AccountPage, LoginPage, useMember } from './MemberPages';
import MemberAvatar from './MemberAvatar';
import CookieNotice, { openCookiePreferences } from './CookiePreferences';
import { CONSENT_EVENT, preferenceValue, savePreference } from './cookie-consent';
import WebSettings from './WebSettings';
import { MallOrders } from './MallPages';
import { MallCart, MallCashier } from './MallCart';
import ProductPurchase from './ProductPurchase';
import { useCartCount } from './mall-cart-state';
import { CommunityFeed, CommunityPost, CommunityPublish } from './Community';
import './web-storefront.css';
import './web-settings.css';
import './account-dashboard.css';
import './site-header.css';

const asset = name => `${import.meta.env.BASE_URL}images/${name}${['product-asics', 'product-kobe', 'product-samba'].includes(name) ? '-cutout.png' : '.webp'}`;
const heroAsset = name => `${import.meta.env.BASE_URL}images/${name}-hero.png`;

function useCatalog(path) {
  const [state, setState] = useState({ loading: Boolean(path) });
  const [attempt, retry] = useState(0);
  useEffect(() => {
    if (!path) { setState({ loading: false }); return undefined; }
    const controller = new AbortController(); setState({ loading: true });
    catalogRequest(path, controller.signal).then(data => setState({ data, loading: false })).catch(error => {
      if (error.name !== 'AbortError') setState({ error: error.message, loading: false });
    });
    return () => controller.abort();
  }, [path, attempt]);
  return { ...state, retry: () => retry(n => n + 1) };
}

function useWebRoute() {
  const [route, setRoute] = useState(() => readWebRoute(window.location.hash));
  useEffect(() => {
    const update = () => setRoute(readWebRoute(window.location.hash));
    window.addEventListener('hashchange', update); update();
    return () => window.removeEventListener('hashchange', update);
  }, []);
  function go(href) {
    if (window.location.hash === href) { setRoute(readWebRoute(href)); window.scrollTo({ top: 0, behavior: 'smooth' }); return; }
    window.location.hash = href;
  }
  return [route, go];
}

function ProductImage({ product, srcKey, ...props }) {
  const { t } = useI18n();
  const [broken, setBroken] = useState(false); const key = srcKey || product?.image;
  if (product?.photo) return <img {...props} src={asset(product.photo)} alt={`${product.brand} ${product.name} ${product.color || ''}`} />;
  return broken || !key ? <span className="image-fallback" data-i18n-skip>{t('商品图片暂不可用')}</span> : <img {...props} src={assetUrl(key)} alt={product.name} onError={() => setBroken(true)} />;
}

function Logo() { return <span className="wordmark">LECTUS<span className="brand-dot">.</span><small>莱择</small></span>; }

function ProductPrice({ product, detail = false }) {
  const { t } = useI18n();
  const price = previewPrice(product.id);
  return <div className={detail ? 'detail-price' : 'product-status'} data-i18n-skip>{price ? <><strong>{price.formatted}</strong><span>{t('售价')}{detail ? ` · ${t('人民币')}` : ''}</span></> : <>{t('暂无报价')}<span>{t('商品信息')}</span></>}</div>;
}

function Header({ route, go, savedCount, member }) {
  const cartCount = useCartCount(member.session?.token);
  const { t } = useI18n();
  const memberName = member.profile?.nickname || member.profile?.username || member.session?.nickname || member.session?.username || t('个人页');
  const [query, setQuery] = useState(route.q); useEffect(() => setQuery(route.q), [route.q]);
  function submit(event) { event.preventDefault(); go(webHref('discover', { q: query.trim(), page: 0 })); }
  return <><div className="topline"><span>选择你的生活主场。</span><span>好物，自有主场</span></div><header className="site-header"><a href={webHref('home')} aria-label="Lectus 莱择首页"><Logo /></a><nav aria-label="主导航"><a className={route.path === 'home' ? 'active' : ''} href={webHref('home')}>首页</a><a className={route.path === 'discover' ? 'active' : ''} href={webHref('discover')}>发现好物</a><a className={route.path === 'shops' ? 'active' : ''} href={webHref('shops')}>探索品牌</a><a className={route.path.startsWith('community') ? 'active' : ''} href={webHref('community')}>社区</a><a className={route.path === 'saved' ? 'active' : ''} href={webHref('saved')}>心动清单</a><a className={route.path === 'mall-orders' ? 'active' : ''} href={webHref('mall-orders')}>我的订单</a></nav><form className="header-search" role="search" onSubmit={submit}><Search size={17} /><input aria-label="搜索品牌或商品" placeholder="搜索品牌、商品、灵感" value={query} maxLength={100} onChange={event => setQuery(event.target.value)} />{query && <button type="button" aria-label="清空搜索" onClick={() => setQuery('')}><X size={15} /></button>}<button type="submit" aria-label="提交搜索"><ArrowRight size={17} /></button></form><div className="header-tools"><a className={`saved-button ${route.path === 'mall-cart' ? 'current' : ''}`} aria-label={`购物车，${cartCount} 件`} href={webHref('mall-cart')}><ShoppingBag size={20} /><span>购物车</span>{cartCount > 0 && <b>{cartCount}</b>}</a><a className={`header-account ${route.path === 'account' ? 'current' : ''}`} href={webHref('account')} data-i18n-skip aria-label={member.session ? `${t('个人页')} · ${memberName}` : t('登录')} title={member.session ? memberName : t('登录')}>{member.session ? <MemberAvatar profile={member.profile || member.session} className="header-avatar" /> : <UserRound size={20} />}<span className="header-account-name">{member.session ? memberName : t('登录')}</span></a><a className={`header-settings ${route.path === 'settings' ? 'current' : ''}`} href={webHref('settings')} aria-label="个人设置" aria-current={route.path === 'settings' ? 'page' : undefined}><Settings size={20} /><span>设置</span></a></div></header></>;
}

function Hero() {
  const [selected, setSelected] = useState(0);
  const [query, setQuery] = useState('');
  const stage = useRef(null);
  const frame = useRef(0);
  const picks = [
    { id: 'edit-asics', name: 'GEL-KAYANO 14', brand: 'ASICS', photo: 'product-asics', color: '银白色', href: webHref('discover', { q: 'Gel-Kayano 14' }) },
    ...featuredEditions.map(item => ({ ...item, href: webHref('product', { id: item.id }) })),
  ];
  const current = picks[selected];
  const currentPrice = previewPrice(current.id);
  useEffect(() => () => cancelAnimationFrame(frame.current), []);
  function follow(event) {
    if (event.pointerType !== 'mouse' || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - bounds.left) / bounds.width - .5) * 10;
    const y = ((event.clientY - bounds.top) / bounds.height - .5) * 6;
    cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      stage.current?.style.setProperty('--shoe-x', `${x}px`);
      stage.current?.style.setProperty('--shoe-y', `${y}px`);
    });
  }
  function reset() {
    cancelAnimationFrame(frame.current);
    stage.current?.style.setProperty('--shoe-x', '0px');
    stage.current?.style.setProperty('--shoe-y', '0px');
  }
  return <section className="hero" aria-label="本期风格精选">
    <div className="hero-copy"><h1>喜欢的，<br />就有<span>主场。</span></h1><p>不跟随每一种流行。<br />从一双好鞋开始，发现自己的日常。</p>
      <form className="hero-search" role="search" onSubmit={event => { event.preventDefault(); window.location.hash = webHref('discover', { q: query.trim() }); }}><Search size={19} /><input aria-label="探索你喜欢的商品" placeholder="搜索品牌或商品，发现下一份喜欢" maxLength={100} value={query} onChange={event => setQuery(event.target.value)} /><button type="submit" className="button primary">开始探索<ArrowUpRight size={17} /></button></form>
      <div className="hero-quick"><span>从这里开始</span>{['Nike', 'adidas', 'ASICS'].map(brand => <a key={brand} href={webHref('discover', { q: brand })}>{brand}<ArrowUpRight size={12} /></a>)}</div>
    </div>
    <div className="hero-stage" ref={stage}>
      <div className="hero-art" onPointerMove={follow} onPointerLeave={reset}>
        <span className="hero-word" aria-hidden="true">EVERYDAY.</span>
        <a className="hero-product" key={current.photo} href={current.href} aria-label={`查看 ${current.brand} ${current.name}`}><img src={heroAsset(current.photo)} alt={`${current.brand} ${current.name} ${current.color}`} fetchPriority="high" width="1050" height="560" /></a>
      </div>
      <div className="hero-product-caption" aria-live="polite" aria-atomic="true"><span className="hero-product-info"><strong>{current.brand}</strong><span>{current.name}</span><small>{current.color}</small></span><span className="hero-price"><strong>{currentPrice?.formatted}</strong><small>售价</small></span><a className="icon-button" href={current.href} aria-label={`探索 ${current.name}`}><ArrowUpRight size={22} /></a></div>
      <div className="hero-selector" role="group" aria-label="切换精选鞋款">{picks.map((item, index) => <button key={item.photo} aria-label={`展示 ${item.name}`} aria-pressed={selected === index} onClick={() => { reset(); setSelected(index); }}><img src={heroAsset(item.photo)} alt="" width="64" height="40" /><span>{item.brand}</span></button>)}</div>
    </div>
  </section>;
}

function SceneShelf() {
  const scenes = [
    { name: '城市有自己的节奏', detail: '街头鞋履 · 日常穿搭', image: 'city', category: '鞋类', className: 'scene-city' },
    { name: '向户外，多走一步', detail: '探索 Salomon', image: 'hiker', q: 'Salomon', className: 'scene-outdoor' },
    { name: '给日常一点新意', detail: '数码 · 灵感好物', image: 'headphones', category: '数码', className: 'scene-daily' },
  ];
  return <section className="scene-section" aria-labelledby="scene-title"><div className="section-heading"><div><h2 id="scene-title">今天，想去哪里？</h2><p>从生活的一个片刻，找到下一份喜欢。</p></div><a className="text-link" href={webHref('discover')}>随心逛逛<ArrowUpRight size={16} /></a></div><div className="scene-grid">{scenes.map(scene => <a key={scene.name} className={`scene ${scene.className}`} href={webHref('discover', { category: scene.category, q: scene.q })}><img src={asset(scene.image)} alt="" loading="lazy" /><div><h3>{scene.name}</h3><p>{scene.detail}</p></div><span className="scene-arrow"><ArrowUpRight size={21} /></span></a>)}</div></section>;
}

function FeaturedShelf({ saved, toggle }) {
  const asics = { id: 'edit-asics', name: 'GEL-KAYANO 14', brand: 'ASICS', color: '银白色', category: '鞋类', photo: 'product-asics', href: webHref('discover', { q: 'Gel-Kayano 14' }) };
  return <section className="featured-section" aria-labelledby="featured-title"><div className="section-heading"><div><h2 id="featured-title">从一双好鞋开始<span>。</span></h2><p>看轮廓，也看细节。有 3D 标签的款式，可以直接旋转查看。</p></div><a className="text-link" href={webHref('discover', { category: '鞋类' })}>全部鞋履<ArrowUpRight size={16} /></a></div><div className="featured-grid">{[asics, ...featuredEditions].map((product, index) => <ProductCard key={product.id} product={product} index={index} saved={saved} toggle={toggle} />)}</div></section>;
}

function BrowseStrip() {
  const links = [['鞋履', '鞋类'], ['服饰', '潮服'], ['数码', '数码'], ['配饰', '配饰'], ['美妆', '美妆'], ['生活', '家居']];
  return <div className="browse-strip route-view"><span>好物，自有主场</span><div>{links.map(([label, value]) => <a key={label} href={webHref('discover', { category: value })}>{label}<ArrowUpRight size={14} /></a>)}</div><span className="strip-tail">按你的喜好，慢慢发现。</span></div>;
}

function ProductCard({ product, saved, toggle, index }) {
  const { t } = useI18n();
  const isSaved = saved.includes(product.id);
  const [showModel, setShowModel] = useState(false);
  const model = getFeaturedEdition(product.id) ? getMeshyPreviewModel(product.id, { enabled: true }) : null;
  const href = product.href || webHref('product', { id: product.id });
  return <article className={`product-card ${model ? 'has-model' : ''}`} style={{ '--card-index': index }} data-i18n-skip>
    <div className="product-picture">
      {showModel && model ? <div className="card-model"><ShoeViewer model={model} layout="fill" /></div> : <a className="product-open" href={href} aria-label={t('查看 {name}', { name: product.name })}><ProductImage product={product} loading="lazy" width="400" height="400" /></a>}
      {!product.href && <button className={`icon-button heart-button ${isSaved ? 'is-saved' : ''}`} aria-label={t(isSaved ? '取消收藏 {name}' : '收藏 {name}', { name: product.name })} aria-pressed={isSaved} onClick={() => toggle(product.id)}><Heart size={18} fill={isSaved ? 'currentColor' : 'none'} /></button>}
      {model && <button className="model-badge" aria-label={t(showModel ? '返回图片 {name}' : '查看3D {name}', { name: product.name })} aria-pressed={showModel} onClick={() => setShowModel(value => !value)}><Box size={14} />{t(showModel ? '返回图片' : '3D 看细节')}</button>}
      {!showModel && <span className="product-category">{t(product.category)}</span>}
    </div>
    <div className="product-brand">{product.brand}<span>{product.color || t('{count} 款配色', { count: product.colorwayCount || 1 })}</span></div>
    <a className="product-name" href={href} data-i18n-skip>{product.name}<ArrowUpRight size={14} /></a>
    <ProductPrice product={product} />
  </article>;
}

function SavedGrid({ saved, toggle }) {
  const { t } = useI18n();
  const [items, setItems] = useState([]); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(''); const [attempt, setAttempt] = useState(0);
  useEffect(() => { const controller = new AbortController(); setLoading(true); setError(''); Promise.all(saved.map(id => getFeaturedEdition(id) || catalogRequest(`products/${encodeURIComponent(id)}`, controller.signal))).then(data => { if (!controller.signal.aborted) setItems(data); }).catch(error => { if (!controller.signal.aborted) setError(error.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); }); return () => controller.abort(); }, [saved, attempt]);
  if (loading) return <div className="product-grid" aria-label={t('正在加载收藏')} data-i18n-skip><div className="skeleton"><span /><i /><i /></div><div className="skeleton"><span /><i /><i /></div></div>;
  if (error) return <div className="empty-state" data-i18n-skip><h3>{t('收藏暂时无法读取')}</h3><p role="alert">{t(error)}</p><button className="button secondary" onClick={() => setAttempt(value => value + 1)}>{t('重新加载')}</button></div>;
  if (!items.length) return <div className="empty-state" data-i18n-skip><Heart size={29} /><h2>{t('还没有收藏的好物')}</h2><p>{t('点击商品上的爱心，把喜欢留在这里。')}</p><a className="button secondary" href={webHref('discover')}>{t('浏览全部好物')}</a></div>;
  return <div className="product-grid">{items.map((product, index) => <ProductCard key={product.id} product={product} saved={saved} toggle={toggle} index={index} />)}</div>;
}

function CatalogSection({ route, meta, catalog, saved, toggle }) {
  const Heading = 'h2';
  const onlySaved = route.path === 'saved'; const items = catalog.data?.items || []; const title = onlySaved ? '你的心动清单' : route.q ? `关于「${route.q}」` : ['home', 'mall'].includes(route.path) ? '全部商品' : '商品列表';
  return <section id="discover" className="discover route-view" aria-labelledby="discover-heading"><div className="section-heading"><div><Heading id="discover-heading">{title}<span>.</span></Heading><p>{onlySaved ? '为喜欢留个位置，下次再来看。' : '按分类、品牌或关键词，找到想要的商品。'}</p></div><span className="catalog-count">{onlySaved ? `${saved.length} 件收藏` : meta.data ? `${meta.data.total} 件好物 · 持续探索` : '商品信息'}</span></div><div className="filter-bar"><div className="category-tabs" aria-label="商品分类"><a className={!route.category ? 'selected' : ''} href={webHref(route.path === 'mall' ? 'mall' : 'discover', { q: route.q })}>全部好物</a>{(meta.data?.categories || []).map(category => <a className={route.category === category.name ? 'selected' : ''} key={category.name} href={webHref(route.path === 'mall' ? 'mall' : 'discover', { category: category.name, q: route.q })}>{category.name}</a>)}</div><label className="brand-filter"><SlidersHorizontal size={16} /><select aria-label="筛选品牌" value={route.brand} disabled={onlySaved} onChange={event => { window.location.hash = webHref(route.path === 'mall' ? 'mall' : 'discover', { category: route.category, q: route.q, brand: event.target.value }); }}><option value="">全部品牌</option>{meta.data?.brands.map(brand => <option key={brand.id} value={brand.id}>{brand.name}</option>)}</select></label></div><div aria-live="polite">{onlySaved ? <SavedGrid saved={saved} toggle={toggle} /> : catalog.loading ? <div className="product-grid" aria-label="正在加载商品">{Array.from({ length: 8 }, (_, index) => <div className="skeleton" key={index}><span /><i /><i /></div>)}</div> : catalog.error ? <div className="empty-state" role="alert"><h2>好物暂时还在路上</h2><p>{catalog.error}</p><button className="button secondary" onClick={catalog.retry}>重新加载</button></div> : items.length ? <div className="product-grid">{items.map((product, index) => <ProductCard key={product.id} product={product} saved={saved} toggle={toggle} index={index} />)}</div> : <div className="empty-state"><Heart size={29} /><h2>暂时没有找到相关好物</h2><p>试试其他关键词，或浏览全部分类。</p><a className="button secondary" href={webHref('discover')}>浏览全部好物</a></div>}</div>{!onlySaved && catalog.data && <div className="pagination"><span>共 {catalog.data.total} 件 · 第 {route.page + 1} / {Math.max(1, Math.ceil(catalog.data.total / 8))} 页</span><div><a className="icon-button" aria-label="上一页" aria-disabled={route.page === 0} href={route.page === 0 ? webHref(route.path === 'mall' ? 'mall' : 'discover', { category: route.category, brand: route.brand, q: route.q }) : webHref(route.path === 'mall' ? 'mall' : 'discover', { category: route.category, brand: route.brand, q: route.q, page: route.page - 1 })}><ChevronLeft size={18} /></a><a className="icon-button" aria-label="下一页" aria-disabled={!catalog.data.hasMore} href={catalog.data.hasMore ? webHref(route.path === 'mall' ? 'mall' : 'discover', { category: route.category, brand: route.brand, q: route.q, page: route.page + 1 }) : webHref(route.path === 'mall' ? 'mall' : 'discover', { category: route.category, brand: route.brand, q: route.q, page: route.page })}><ChevronRight size={18} /></a></div></div>}</section>;
}

function Shops() {
  return <section className="shops route-view"><div className="section-heading"><div><h2>从品牌，找到你的风格<span>.</span></h2><p>经典的轮廓，也有新的表达。</p></div><a className="text-link" href={webHref('discover')}>浏览全部好物<ArrowUpRight size={16} /></a></div><div className="shop-grid"><a className="own-shop" href={webHref('discover', { q: 'adidas' })}><div><Logo /><h2>adidas</h2><p>从经典出发，<br />走出自己的日常。</p><span className="shop-type">探索 adidas<ArrowUpRight size={16} /></span></div><img src={asset('product-samba')} alt="adidas Samba 鞋履" /></a><a className="merchant-shop" href={webHref('discover', { q: 'Salomon' })}><img src={asset('hiker')} alt="户外徒步生活灵感" loading="lazy" /><div><h2>Salomon</h2><p>城市与山野，<br />都可以是你的主场。</p><span>探索 Salomon<ArrowUpRight size={16} /></span></div></a></div></section>;
}

function ProductPage({ route, saved, toggle, member }) {
  const edition = getFeaturedEdition(route.id);
  const resource = useCatalog(route.id && !edition ? `products/${encodeURIComponent(route.id)}` : null);
  const [activeImage, setActiveImage] = useState(null);
  const [colorway, setColorway] = useState(null);
  const [view, setView] = useState(route.params.get('view') === '3d' ? '3d' : 'photo');
  const product = edition || resource.data;
  const model = edition ? getMeshyPreviewModel(edition.id, { enabled: true }) : null;
  if (!route.id) return <div className="empty-state"><h1>没有找到这件商品</h1><a className="button secondary" href={webHref('discover')}>回到商品列表</a></div>;
  if (resource.loading) return <div className="detail-state" role="status">正在打开这件好物…</div>;
  if (resource.error || !product) return <div className="empty-state" role="alert"><h1>这件商品暂时无法查看</h1><p>{resource.error}</p><button className="button secondary" onClick={resource.retry}>重新加载</button></div>;
  const active = activeImage || product.detailImage;
  const isSaved = saved.includes(product.id);
  return <article className="product-page route-view"><a className="back-link" href={edition ? webHref('home') : webHref('discover', { category: product.category })}><ChevronLeft size={16} />{edition ? '返回首页精选' : '返回商品列表'}</a>
    <div className="product-page-layout"><div className={`detail-gallery ${view === '3d' && model ? 'gallery-model' : ''}`}>
      {model && <div className="gallery-tabs" role="group" aria-label="商品展示方式"><button aria-pressed={view === 'photo'} onClick={() => setView('photo')}>商品图片</button><button aria-pressed={view === '3d'} onClick={() => setView('3d')}><Box size={15} />3D 看细节</button></div>}
      <div className={`detail-visual ${view === '3d' && model ? 'showing-model' : ''}`}>
        {view === '3d' && model ? <ShoeViewer model={model} layout="fill" /> : <ProductImage key={active || product.id} product={product} srcKey={active} />}
      </div>
      {view !== '3d' && product.images?.length > 0 && <div className="detail-thumbnails">{product.images.map((item, index) => <button aria-label={`查看图片 ${index + 1}`} key={item.id || item.image} onClick={() => setActiveImage(item.image)} aria-pressed={active === item.image}><img src={assetUrl(item.thumbnail || item.image)} alt="" /></button>)}</div>}
    </div><div className="detail-copy"><span className="detail-brand">{product.brand}</span><h1 data-i18n-skip>{product.name}</h1><p className="detail-category">{product.category} · {edition ? product.color : `${product.colorways?.length || 1} 种配色`}</p>
      {edition && <p className="edition-description">{edition.description}</p>}

      {product.colorways?.length > 0 && <fieldset><legend>查看配色</legend><div className="colorways">{product.colorways.map(item => { const selected = product.images?.find(imageItem => imageItem.id === item.mainImageId); return <button key={item.id} aria-pressed={colorway === item.id} onClick={() => { setColorway(item.id); setView('photo'); if (selected) setActiveImage(selected.image); }}>{colorway === item.id && <Check size={13} />}{item.name || item.id}</button>; })}</div></fieldset>}
      {model && <div className="inline-model-note"><Box size={21} /><div><strong>换个角度，看见喜欢</strong><p>拖动旋转，放大查看细节。AI 生成的模型仅供外观参考。</p></div></div>}
      <ProductPurchase product={product} member={member} colorway={colorway} onVariant={id => { const sku = product.skus?.find(v => v.id === id); const image = product.images?.find(v => sku?.imageIds?.includes(v.id)); if (image) setActiveImage(image.image); }} />
      <div className="product-actions"><button className="button secondary" onClick={() => toggle(product.id)}><Heart size={17} fill={isSaved ? 'currentColor' : 'none'} />{isSaved ? '已收藏 · 取消收藏' : '收藏这件好物'}</button></div>
      {!edition && <dl><div><dt>商品编号</dt><dd>{product.id}</dd></div><div><dt>商品规格</dt><dd>{product.skus?.length || 0} 项</dd></div></dl>}
    </div></div>
  </article>;
}

function Footer() {
  const { t } = useI18n();
  return <footer className="site-footer"><div className="footer-main"><div><a href={webHref('home')}><Logo /></a><p>让喜欢，成为日常。</p></div><div className="footer-links"><div><strong>发现莱择</strong><a href={webHref('discover')}>全部好物</a><a href={webHref('discover', { category: '鞋类' })}>鞋履精选</a><a href={webHref('shops')}>探索品牌</a><a href={webHref('community')}>社区</a></div><div><strong>你的主场</strong><button className="footer-cookie-button" data-i18n-skip onClick={openCookiePreferences}>{t('Cookie 与隐私')}</button><a href={webHref('saved')}>心动清单</a><a href={webHref('discover', { category: '潮服' })}>日常穿搭</a><a href={webHref('home')}>回到首页</a></div></div></div><div className="footer-bottom"><span>© 2026 LECTUS 莱择</span><span>让每一次选择，更合心意。</span><a href={webHref('home')}>回到顶部<MoveUpRight size={15} /></a></div></footer>;
}

function App() {
  const [route, go] = useWebRoute(); const member = useMember(); const meta = useCatalog(route.path === 'scan' ? null : 'meta');
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }); }, [route.key]);
  const [saved, setSaved] = useState(() => { try { const value = JSON.parse(preferenceValue('lectus-web-saved') || '[]'); return Array.isArray(value) ? value.filter(value => typeof value === 'string') : []; } catch { return []; } });
  useEffect(() => { const persist = () => savePreference('lectus-web-saved', JSON.stringify(saved)); window.addEventListener(CONSENT_EVENT, persist); return () => window.removeEventListener(CONSENT_EVENT, persist); }, [saved]);
  const onlyCatalog = ['home', 'discover', 'mall'].includes(route.path); const catalogParams = new URLSearchParams({ size: '8', page: String(route.page) }); if (route.category) catalogParams.set('category', route.category); if (route.brand) catalogParams.set('brand', route.brand); if (route.q) catalogParams.set('q', route.q); const catalog = useCatalog(onlyCatalog ? `products?${catalogParams}` : null);
  function toggle(id) { setSaved(values => { const next = values.includes(id) ? values.filter(value => value !== id) : [...values, id]; savePreference('lectus-web-saved', JSON.stringify(next)); return next; }); }

  const content = route.path === 'community' ? <CommunityFeed route={route} member={member} /> : route.path === 'community-post' ? <CommunityPost key={route.id} route={route} member={member} /> : route.path === 'community-publish' ? <CommunityPublish member={member} /> : route.path === 'settings' ? <WebSettings /> : route.path === 'mall-cart' ? <MallCart member={member} go={go} /> : route.path === 'mall-cashier' ? <MallCashier member={member} route={route} /> : route.path === 'mall-orders' ? <MallOrders member={member} /> : route.path === 'login' ? <LoginPage route={route} go={go} member={member} /> : route.path === 'account' ? <AccountPage route={route} member={member} savedCount={saved.length} savedContent={<SavedGrid saved={saved.slice(-3).reverse()} toggle={toggle} />} /> : route.path === 'product' ? <ProductPage key={route.id} route={route} saved={saved} toggle={toggle} member={member} /> : route.path === 'shops' ? <Shops /> : <>{route.path === 'home' && <><Hero /><BrowseStrip /><SceneShelf /><FeaturedShelf saved={saved} toggle={toggle} /></>}<CatalogSection route={route} meta={meta} catalog={catalog} saved={saved} toggle={toggle} />{route.path === 'home' && <><Shops /><div className="closing-note"><span>少一点将就，多一点喜欢。</span><a href={webHref('home')}>回到顶部<MoveUpRight size={17} /></a></div></>}</>;
  return <div className="web-app"><a className="skip-link" href="#main-content" onClick={event => { event.preventDefault(); document.getElementById("main-content").focus(); document.getElementById("main-content").scrollIntoView(); }}>跳到主要内容</a><Header route={route} go={go} savedCount={saved.length} member={member} /><main id="main-content" tabIndex={-1} className="page-wrap">{content}</main><Footer /><CookieNotice /></div>;

}

createRoot(document.getElementById('root')).render(<I18nProvider><App /></I18nProvider>);
