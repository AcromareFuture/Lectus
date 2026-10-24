import React, { useEffect, useState } from 'react';
import { ArrowRight, ArrowUpRight, Box, Check, ChevronLeft, ChevronRight, Grid2X2, Heart, ImageOff, Search, ShieldCheck, X } from 'lucide-react';
import { assetUrl, catalogRequest, matchingSkus, exactCatalogModel } from './catalog-api';
import ShoeViewer from './ShoeViewer';
import { useI18n } from './i18n';
import './catalog-store.css';

const categories = ['鞋类','潮服','数码','美妆','家居','手表','包袋','配饰','潮玩','女装'];
function useResource(path) {
  const [attempt, setAttempt] = useState(0);
  const [resource, setResource] = useState({ data: null, error: '', loading: true });
  useEffect(() => {
    const controller = new AbortController();
    setResource({ data: null, error: '', loading: true });
    catalogRequest(path, controller.signal).then(data => {
      if (!controller.signal.aborted) setResource({ data, error: '', loading: false });
    }).catch(error => {
      if (!controller.signal.aborted) setResource({ data: null, error: error.message, loading: false });
    });
    return () => controller.abort();
  }, [path, attempt]);
  return { ...resource, retry: () => setAttempt(value => value + 1) };
}

export function CatalogImage({ image, alt, className = '', ...rest }) {
  const { t } = useI18n();
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [image]);
  return image && !failed ? <img className={`catalog-image ${className}`} src={assetUrl(image)} alt={alt} decoding="async" onError={() => setFailed(true)} {...rest} />
    : <span className={`catalog-no-image ${className}`} role="img" aria-label={t('商品图片待补充')}><ImageOff size={25} strokeWidth={1.2}/><span>{t('商品图片待补充')}</span></span>;
}

function ResourceError({ error, retry }) {
  const { t } = useI18n();
  return <div className="catalog-status" role="alert"><Box size={27} strokeWidth={1.2}/><p>{t(error)}</p><button className="outline-button" onClick={retry}>{t('重新加载')}</button></div>;
}

function useSaved() {
  const [ids, setIds] = useState(() => { try {
    const stored = JSON.parse(localStorage.getItem('lectus-catalog-saved') || '[]');
    return Array.isArray(stored) ? stored.filter(id => typeof id === 'string') : [];
  } catch { return []; } });
  const toggle = (product) => setIds(previous => {
    const next = previous.includes(product.id) ? previous.filter(id => id !== product.id) : [...previous, product.id];
    try {
      localStorage.setItem('lectus-catalog-saved', JSON.stringify(next));
      const snapshots = JSON.parse(localStorage.getItem('lectus-catalog-snapshots') || '{}');
      snapshots[product.id] = product;localStorage.setItem('lectus-catalog-snapshots', JSON.stringify(snapshots));
    } catch { /* Saving is optional when device storage is unavailable. */ }
    return next;
  });
  return [ids, toggle];
}

function CatalogCard({ product, nav, saved, toggle }) {
  const { t } = useI18n();
  return <article className="product-card source-product-card">
    <button className="product-image-button" onClick={() => nav('product', { id: product.id })} aria-label={`${t('查看详情')} ${product.name}`}>
      <CatalogImage image={product.image} alt={product.name} loading="lazy" width="480" height="480" />
      {product.featured && <span className="product-tag">{t('精选')}</span>}
    </button>
    <div className="product-copy"><span className="product-brand" data-i18n-skip>{product.brand}</span>
      <button className="product-name" data-i18n-skip onClick={() => nav('product', { id: product.id })}>{product.name}</button>
      <div className="source-price-line"><span>{t('待定价')}</span><button className="icon-button" aria-label={`${t('收藏')} ${product.name}`} aria-pressed={saved.includes(product.id)} onClick={() => toggle(product)}><Heart size={18} fill={saved.includes(product.id) ? 'currentColor' : 'none'}/></button></div>
      <small className="catalog-variant-count">{t('{count} 组配色', { count: product.colorwayCount })} · {t('{count} 个规格', { count: product.skuCount })}</small>
    </div>
  </article>;
}

function CatalogResults({ filters, nav }) {
  const { t } = useI18n();
  const [page, setPage] = useState(0);
  const [items, setItems] = useState([]);
  const [saved, toggle] = useSaved();
  const resource = useResource(`products?${new URLSearchParams({ ...filters, page, size: 24 })}`);
  useEffect(() => {
    if (resource.data) setItems(previous => page === 0 ? resource.data.items : [...previous.filter(p => !resource.data.items.some(next => next.id === p.id)), ...resource.data.items]);
  }, [resource.data, page]);
  return <>
    <div className="source-result-line" aria-live="polite"><span>{resource.data ? t('{count} 件好物', { count: resource.data.total }) : t('正在查找好物')}</span><span>{t('商品列表')}</span></div>
    {items.length > 0 && <div className="product-grid source-grid">{items.map(product => <CatalogCard key={product.id} {...{ product, nav, saved, toggle }}/>)}</div>}
    {resource.loading && <div className="catalog-loading" role="status">{t('正在加载商品')}<span aria-hidden="true"/></div>}
    {resource.error && <ResourceError {...resource}/>}
    {resource.data && resource.data.total === 0 && <div className="catalog-status"><Search size={28} strokeWidth={1.2}/><h3>{t('没有找到匹配的商品')}</h3><p>{t('试试其他品牌、型号或分类。')}</p><button className="outline-button" onClick={() => nav('categories')}>{t('清除筛选')}</button></div>}
    {resource.data?.hasMore && !resource.loading && <button className="outline-button catalog-more" onClick={() => setPage(value => value + 1)}>{t('加载更多')}<ArrowRight size={16}/></button>}
  </>;
}

function StoreHero({ nav }) {
  const { t } = useI18n();
  const resource = useResource('products?q=Gel-Kayano+14&size=1');
  const product = resource.data?.items[0];
  return <section className="source-hero" aria-label={t('本周风格精选')}>
    <div className="source-hero-heading"><h1>{t('好风格，')}<br/>{t('不设限')}<span className="pink">.</span></h1><button onClick={() => nav('categories', { category: '鞋类' })}>{t('探索鞋履')}<ArrowUpRight size={20}/></button></div>
    <button className="source-hero-image" disabled={!product} onClick={() => nav('product', { id: product.id })} aria-label={product ? `${t('查看详情')} ${product.name}` : t('正在加载商品')}>
      {product ? <CatalogImage image={product.detailImage} alt={product.name} width="960" height="960" fetchPriority="high"/> : <div className="catalog-hero-placeholder"/>}
      <span className="source-hero-caption"><span><b data-i18n-skip>{product?.brand || 'ASICS'}</b><strong data-i18n-skip>{product?.name || 'Gel-Kayano 14'}</strong></span><ArrowUpRight size={24}/></span>
    </button>
    <div className="source-service-line"><span><ShieldCheck size={15}/>{t('实拍商品图库')}</span><button onClick={() => nav('categories')}>{t('探索全部分类')}<Grid2X2 size={15}/></button></div>
  </section>;
}

export function CatalogStore({ route, nav }) {
  const { t } = useI18n();
  const metadata = useResource('meta');
  const category = route.params.get('category') || '';
  const brand = route.params.get('brand') || '';
  const q = route.params.get('q') || '';
  const [query, setQuery] = useState(q);
  useEffect(() => setQuery(q), [q]);
  const filters = { category, brand, q };
  const change = patch => nav(route.path, Object.fromEntries(Object.entries({ ...filters, ...patch }).filter(([,value]) => value)));
  return <div className="source-store">
    <form className="source-search" role="search" onSubmit={event => { event.preventDefault();change({ q: query.trim() }); }}>
      <Search size={18}/><input aria-label={t('搜索商品')} placeholder={t('搜索品牌、型号、生活好物')} maxLength="100" value={query} onChange={event => setQuery(event.target.value)}/>
      {query && <button type="button" aria-label={t('清空搜索')} onClick={() => { setQuery('');change({ q: '' }); }}><X size={17}/></button>}
      <button type="submit">{t('搜索')}</button>
    </form>
    {route.path === 'home' && !category && !brand && !q && <>
      <StoreHero nav={nav}/>
      <section className="source-preview-models" aria-label={t('3D 好物体验')}>
        <div className="section-heading"><h2>{t('3D 好物体验')}<span className="pink">.</span></h2><Box size={20}/></div>
        <div className="source-preview-links">
          {[['wp-kobe', 'Nike Kobe 5 Protro Dodgers', 'product-kobe'], ['wp-samba', 'adidas Samba OG 白黑', 'product-samba']].map(([id, name, image]) =>
            <button key={id} onClick={() => nav('product', { id, preview3d: 'meshy' })} aria-label={`${t('体验3D')} ${name}`}>
              <img src={`${import.meta.env.BASE_URL}images/${image}-480.webp`} alt="" loading="lazy" width="160" height="120"/>
              <span><strong data-i18n-skip>{name}</strong><small>{t('拖动旋转 · 双指缩放')}</small></span><ArrowUpRight size={17}/>
            </button>)}
        </div>
      </section>
    </>}
    <section className="source-catalog" id="store">
      <div className="section-heading"><h2>{t(route.path === 'search' ? '搜索商品' : '值得一择')}<span className="pink">.</span></h2>{metadata.data && <span className="catalog-total">{t('{count} 件好物', { count: metadata.data.total })}</span>}</div>
      <div className="source-filters">
        <div className="source-categories" role="group" aria-label={t('商品分类筛选')}>
          {['', ...categories].map(name => <button key={name} aria-pressed={category === name} className={category === name ? 'selected' : ''} onClick={() => change({ category: name })}>{t(name || '全部')}</button>)}
        </div>
        <div className="source-brand-row"><label><span>{t('品牌')}</span><select aria-label={t('筛选品牌')} value={brand} onChange={event => change({ brand: event.target.value })}><option value="">{t('全部品牌')}</option>{metadata.data?.brands.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          {(category || brand || q) && <button onClick={() => nav(route.path)}>{t('重置')}<X size={14}/></button>}
        </div>
      </div>
      <CatalogResults key={JSON.stringify(filters)} filters={filters} nav={nav}/>
    </section>
    <p className="catalog-footnote">{t('商品信息正在完善，确认售价和库存后开放购买。')}</p>
  </div>;
}

function ProductContent({ product, nav }) {
  const { t } = useI18n();
  const [colorId, setColorId] = useState(() => {
    const main = product.images.find(image => image.image === product.detailImage);
    return product.colorways.find(color => color.imageIds.includes(main?.id))?.id || product.colorways[0]?.id || '';
  });
  const [selection, setSelection] = useState({});
  const [specificSku, setSpecificSku] = useState('');
  const [photoId, setPhotoId] = useState('');
  const [view3D, setView3D] = useState(false);
  const [saved, toggle] = useSaved();
  const color = product.colorways.find(item => item.id === colorId);
  const colorSkus = product.skus.filter(sku => sku.colorwayId === colorId);
  const dimensions = [...new Set(colorSkus.flatMap(sku => Object.keys(sku.specs)))];
  const matches = matchingSkus(colorSkus, selection);
  const picked = matches.find(sku => sku.id === specificSku) || (matches.length === 1 ? matches[0] : null);
  const allowedIds = picked ? picked.imageIds : color?.imageIds || [];
  const gallery = product.images.filter(image => allowedIds.includes(image.id));
  const image = gallery.find(item => item.id === photoId) || gallery[0];
  const index = gallery.findIndex(item => item.id === image?.id);
  const model = exactCatalogModel(product, color);
  const chooseColor = id => { setColorId(id);setSelection({});setSpecificSku('');setPhotoId('');setView3D(false); };
  const chooseSpec = (key, value) => {
    const before = dimensions.slice(0, dimensions.indexOf(key));
    setSelection(previous => ({ ...Object.fromEntries(Object.entries(previous).filter(([k]) => before.includes(k))), [key]: value }));
    setSpecificSku('');setPhotoId('');
  };
  const dimensionLabel = key => t(({Size:'尺码',Color:'颜色',Version:'版本',Sensor:'传感器',Type:'类型',Switches:'轴体','Switch Type':'轴体',Model:'型号',Backlight:'背光',Shade:'色号',SHADE:'色号',Finish:'妆效',Style:'款式',Title:'规格'})[key] || key);
  return <div className="source-detail">
    <div className="source-view-tabs" role="group" aria-label={t('展示方式')}><button aria-pressed={!view3D} onClick={() => setView3D(false)}>{t('商品图片')}</button>{model && <button aria-pressed={view3D} onClick={() => setView3D(true)}><Box size={16}/>{t('专属3D模型')}</button>}</div>
    {view3D && model ? <ShoeViewer key={model.src} model={model}/> : <div className="source-gallery">
      <CatalogImage key={image?.id || colorId} image={image?.image} alt={`${product.name} ${color?.name || ''}`} width="960" height="960"/>
      {gallery.length > 1 && <><button className="gallery-prev icon-button" aria-label={t('上一张商品图')} onClick={() => setPhotoId(gallery[(index - 1 + gallery.length) % gallery.length].id)}><ChevronLeft size={22}/></button><button className="gallery-next icon-button" aria-label={t('下一张商品图')} onClick={() => setPhotoId(gallery[(index + 1) % gallery.length].id)}><ChevronRight size={22}/></button><span className="source-image-count" aria-live="polite">{index + 1} / {gallery.length}</span></>}
    </div>}
    {!view3D && gallery.length > 1 && <div className="source-thumbnails" aria-label={t('商品图库')}>{gallery.map((item, i) => <button key={item.id} aria-pressed={item.id === image.id} aria-label={t('查看第 {count} 张商品图', { count: i + 1 })} onClick={() => setPhotoId(item.id)}><CatalogImage image={item.thumbnail} alt="" width="96" height="96" loading="lazy"/></button>)}</div>}
    <div className="source-detail-body">
      <div className="source-detail-brand"><span data-i18n-skip>{product.brand}</span><button className="icon-button" aria-label={t('收藏商品')} aria-pressed={saved.includes(product.id)} onClick={() => toggle(product)}><Heart size={21} fill={saved.includes(product.id) ? 'currentColor' : 'none'}/></button></div>
      <h1 data-i18n-skip>{product.name}</h1>
      <div className="source-detail-price"><strong>{t('待定价')}</strong><span>{t('暂未开售')}</span></div>
      <p className="source-preview-note">{t('商品信息正在完善，确认售价和库存后开放购买。')}</p>
      <section className="source-options"><h2>{t('配色')} <span>{product.colorways.length}</span></h2><p data-i18n-skip>{color?.name || t('配色待确认')}</p>
        <div className="source-colorways">{product.colorways.map(item => {
          const thumb = product.images.find(i => item.imageIds.includes(i.id));
          return <button key={item.id} aria-pressed={item.id === colorId} onClick={() => chooseColor(item.id)}><CatalogImage image={thumb?.thumbnail} alt="" width="120" height="120" loading="lazy"/><span data-i18n-skip>{item.name || t('配色待确认')}</span>{item.id === colorId && <Check size={14}/>}</button>;
        })}</div>
      </section>
      <section className="source-options"><h2>{t('选择规格')} <span>{colorSkus.length}</span></h2>
        {dimensions.length ? dimensions.map((key, dimensionIndex) => <fieldset key={key}><legend>{dimensionLabel(key)}</legend><div className="source-specs">{[...new Set(colorSkus.map(sku => sku.specs[key]).filter(Boolean))].sort((a,b) => a.localeCompare(b, undefined, { numeric: true })).map(value => {
          const earlier = Object.fromEntries(Object.entries(selection).filter(([k]) => dimensions.indexOf(k) < dimensionIndex));
          const exists = matchingSkus(colorSkus, { ...earlier, [key]: value }).length > 0;
          return <button key={value} disabled={!exists} aria-pressed={selection[key] === value} onClick={() => chooseSpec(key, value)} data-i18n-skip>{value}</button>;
        })}</div></fieldset>) : <p>{t('来源暂未标注详细规格')}</p>}
        {matches.length > 1 && dimensions.every(key => selection[key]) && <label className="source-sku-select"><span>{t('来源规格')}</span><select aria-label={t('选择来源规格')} value={specificSku} onChange={event => {setSpecificSku(event.target.value);setPhotoId('');}}><option value="">{t('选择具体规格')}</option>{matches.map(sku => <option key={sku.id} value={sku.id}>{Object.values(sku.specs).join(' / ') || t('规格待确认')} · {sku.sourceSku || sku.id}</option>)}</select></label>}
        {Object.keys(selection).length > 0 && <button className="source-reset-specs" onClick={() => { setSelection({});setSpecificSku('');setPhotoId(''); }}>{t('清除规格选择')}<X size={13}/></button>}
        <dl className="source-facts"><div><dt>{t('货号')}</dt><dd data-i18n-skip>{color?.styleCode || '—'}</dd></div><div><dt>SKU</dt><dd data-i18n-skip>{picked?.sourceSku || picked?.id || '—'}</dd></div><div><dt>{t('库存')}</dt><dd>{t('待确认')}</dd></div></dl>
      </section>
      {product.category === '鞋类' && !model && <div className="source-model-pending"><Box size={23} strokeWidth={1.2}/><div><b>{t('专属3D模型待接入')}</b><p>{t('当前配色暂无对应模型，先查看实拍细节。')}</p></div></div>}
      <div className="source-detail-actions"><button className="black-button" disabled>{t('暂未开售')}</button><button className="outline-button" onClick={() => nav('categories', { category: product.category })}>{t('继续逛逛')}<ArrowRight size={17}/></button></div>
    </div>
  </div>;
}

export function CatalogProduct({ id, nav }) {
  const resource = useResource(`products/${encodeURIComponent(id)}`);
  const { t } = useI18n();
  useEffect(() => {
    if (!resource.data) return;
    try {
      const previous = JSON.parse(localStorage.getItem('lectus-catalog-history') || '[]');
      localStorage.setItem('lectus-catalog-history', JSON.stringify([id,...previous.filter(item => item !== id)].slice(0,50)));
      const snapshots = JSON.parse(localStorage.getItem('lectus-catalog-snapshots') || '{}');snapshots[id] = resource.data;
      localStorage.setItem('lectus-catalog-snapshots', JSON.stringify(snapshots));
    } catch { /* Browsing works without history storage. */ }
  }, [resource.data, id]);
  return resource.loading ? <div className="catalog-loading" role="status">{t('正在加载商品')}<span/></div> : resource.error ? <ResourceError {...resource}/> : <ProductContent key={id} product={resource.data} nav={nav}/>;
}

export function SavedCatalog({ nav, history = false, children }) {
  const [saved, toggle] = useSaved();
  let list = [];
  try {
    const snapshots = JSON.parse(localStorage.getItem('lectus-catalog-snapshots') || '{}');
    const ids = history ? JSON.parse(localStorage.getItem('lectus-catalog-history') || '[]') : saved;
    list = ids.map(id => snapshots[id]).filter(Boolean);
  } catch { /* Empty collection is handled by the incumbent page. */ }
  const { t } = useI18n();
  return <>{list.length > 0 && <div className="product-grid source-grid">{list.map(product => <CatalogCard key={product.id} {...{ product, nav, saved, toggle }}/>)}</div>}{children}{!list.length && !children && <div className="catalog-status"><Heart size={28} strokeWidth={1.2}/><p>{t('这里还空着')}</p><button className="outline-button" onClick={() => nav('home')}>{t('去发现好物')}<ArrowRight size={16}/></button></div>}</>;
}
