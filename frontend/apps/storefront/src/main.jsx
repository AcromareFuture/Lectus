import React, {
  useState,
  useEffect,
  useLayoutEffect,
  useRef,
  useDeferredValue,
  createContext,
  useContext,
} from "react";
import { createRoot } from "react-dom/client";
import {
  ArrowUpRight,
  ArrowRight,
  ArrowLeft,
  Search,
  ShoppingBag,
  SlidersHorizontal,
  X,
  Plus,
  Minus,
  Check,
  ChevronRight,
  Heart,
  Home,
  Compass,
  ScanLine,
  MessageCircle,
  UserRound,
  ShieldCheck,
  Package,
  Truck,
  Ticket,
  MapPin,
  Settings,
  Headphones,
  RotateCcw,
  ImagePlus,
  Send,
  Share2,
  Grid2X2,
  CheckCircle2,
  FileCheck2,
  CircleHelp,
  Wallet,
  Store,
  CheckCheck,
  Trash2,
  Box,
} from "lucide-react";
import {
  products,
  allProducts,
  categories,
  getProduct,
  photo,
  posts,
  coupons,
  demoNotice,
} from "./data";
import {
  skuKey,
  addCart,
  totals,
  validateAddress,
  filterProducts,
  hydrate,
  initialState,
  rememberSearch,
  validatePriceRange,
} from "./domain";
import "./styles.css";
import imageSizes from "./image-sizes.json";
import { ChoiceRail, Presence, usePressFeedback } from "./motion";
import "./motion.css";
import { I18nProvider, useI18n } from "./i18n.jsx";
import AccountPage from "./AccountPage.jsx";
import ShoeViewer from "./ShoeViewer.jsx";
import { getProductModel, sampleShoeModel } from "./shoe-models.js";
import { getMeshyPreviewModel } from "./meshy-preview.js";
import { CatalogStore, CatalogProduct, SavedCatalog } from "./CatalogStore.jsx";

const scrollToStore = () =>
  document.querySelector("#store")?.scrollIntoView({
    behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
      ? "instant"
      : "smooth",
  });
const money = (n) => `¥${n.toLocaleString("zh-CN")}`;
const readState = () => {
  try {
    return hydrate(JSON.parse(localStorage.getItem("lectus-v1")), allProducts);
  } catch {
    return initialState();
  }
};
const getRoute = () => {
  const [path = "", query = ""] = location.hash.slice(1).split("?");
  return { path: path || "home", params: new URLSearchParams(query) };
};
const IconButton = ({ label, children, onClick, className = "", ...rest }) => (
  <button
    className={`icon-button ${className}`}
    aria-label={label}
    onClick={onClick}
    {...rest}
  >
    {children}
  </button>
);
const LectusLogo = ({ variant = "mobile" }) => (
  <span className={`lectus-logo lectus-logo--${variant}`}>
    <img
      className="lectus-logo-mark"
      src={`${import.meta.env.BASE_URL}lectus-icon.svg`}
      alt=""
      width={variant === "desktop" ? 48 : 34}
      height={variant === "desktop" ? 48 : 34}
      decoding="async"
    />
    <span className="lectus-lockup">
      <span className="lectus-name">
        LECTUS<span className="lectus-period">.</span>
      </span>
      <small>莱择</small>
    </span>
  </span>
);
const Img = ({ name, alt = "", className = "", ...rest }) => (
  <img
    src={photo(name)}
    width={imageSizes[name]?.width}
    height={imageSizes[name]?.height}
    srcSet={`${photo(name + "-480")} ${imageSizes[name]?.smallWidth}w, ${photo(name)} ${imageSizes[name]?.width}w`}
    sizes="(max-width: 600px) 100vw, 430px"
    decoding="async"
    alt={alt}
    className={`${className} ${name?.startsWith("product-") ? "product-shot" : ""}`}
    {...rest}
  />
);
const Empty = ({
  icon: Icon = ShoppingBag,
  title = "这里还空着",
  text = "去发现一些新的喜欢吧。",
  action,
  onAction,
}) => (
  <div className="empty">
    <Icon size={36} strokeWidth={1.2} />
    <h3>{title}</h3>
    <p>{text}</p>
    {action && (
      <button className="black-button" onClick={onAction}>
        {action}
        <ArrowRight size={16} />
      </button>
    )}
  </div>
);
const Stepper = ({ value, onChange, min = 1, max = 99 }) => (
  <div className="stepper">
    <IconButton
      label="减少数量"
      disabled={value <= min}
      onClick={() => onChange(value - 1)}
    >
      <Minus size={16} />
    </IconButton>
    <span><span className="quantity-value" key={value}>{value}</span></span>
    <IconButton
      label="增加数量"
      disabled={value >= max}
      onClick={() => onChange(value + 1)}
    >
      <Plus size={16} />
    </IconButton>
  </div>
);
const Note = ({ children }) => (
  <p className="demo-note">{children || demoNotice}</p>
);

const LectusContext = createContext(null);
const useLectus = () => useContext(LectusContext);
function ProductCard({ product: pr, index = 0 }) {
  const { state, favorite, openProduct, openSku } = useLectus();
  return (
    <article className="product-card" style={{ "--i": index }}>
      <div className="product-visual">
        <button
          className="product-image-button"
          onClick={() => openProduct(pr.id)}
          aria-label={`查看${pr.name}`}
        >
          <Img
            name={pr.image}
            alt={pr.name}
            loading="lazy"
            sizes="(max-width: 600px) 44vw, 190px"
          />
          <span className="product-tag">{pr.tag}</span>
        </button>
        <IconButton
          label={`快速加购${pr.name}`}
          className="quick-add glass"
          onClick={() => openSku(pr, "cart")}
        >
          <Plus size={19} />
        </IconButton>
      </div>
      <div className="product-copy">
        <div className="product-brand">{pr.brand}</div>
        <button className="product-name" onClick={() => openProduct(pr.id)}>
          {pr.name}
        </button>
        <div className="product-price-line">
          <b>{money(pr.price)}</b>
          <IconButton
            label={`收藏${pr.name}`}
            aria-pressed={state.favorites.includes(pr.id)}
            onClick={() => favorite(pr.id)}
          >
            <Heart
              size={17}
              fill={state.favorites.includes(pr.id) ? "currentColor" : "none"}
            />
          </IconButton>
        </div>
        <div className="sold">
          {pr.sold ? `${pr.sold.toLocaleString()} 人想要` : "参考价格"} <span>样例</span>
        </div>
      </div>
    </article>
  );
}
function ProductGrid({ list }) {
  const { resetDiscovery } = useLectus();
  const grid = useRef(null);
  const composition = list.map((product) => product.id).join("|");
  useEffect(() => {
    if (!grid.current || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const animation = grid.current.animate(
      [{ opacity: .55, translate: "0 9px" }, { opacity: 1, translate: "0 0" }],
      { duration: 320, easing: "cubic-bezier(.22,1,.36,1)" },
    );
    return () => animation.cancel();
  }, [composition]);
  return list.length ? (
    <div className="product-grid" ref={grid}>
      {list.map((x, i) => (
        <ProductCard key={x.id} product={x} index={i} />
      ))}
    </div>
  ) : (
    <Empty
      icon={Search}
      title="还没有找到这件好物"
      text="换个关键词，或者去其他分类看看。"
      action="查看全部"
      onAction={resetDiscovery}
    />
  );
}
function ProductRow({ row, children }) {
  const { openProduct, price } = useLectus();
  const pr = getProduct(row.id);
  return pr ? (
    <div className="product-row">
      <button onClick={() => openProduct(pr.id)}>
        <Img name={pr.image} alt={pr.name} />
      </button>
      <div className="product-row-info">
        <span className="tiny-brand">{pr.brand}</span>
        <button onClick={() => openProduct(pr.id)}>{pr.name}</button>
        <small>
          {row.size} / {row.color}
        </small>
        <b>{money(pr.price)}</b>
        {children}
      </div>
    </div>
  ) : null;
}
function CatalogToolbar({ count }) {
  const { category, setCategory, sort, priceRange, setPriceRange, setSheet } =
    useLectus();
  const hasPrice = priceRange.min !== "" || priceRange.max !== "";
  return (
    <div className="catalog-toolbar glass">
      <ChoiceRail selected={category} className="category-scroll" aria-label="商品分类筛选">
        {categories.map((c) => (
          <button
            key={c}
            aria-pressed={c === category}
            className={c === category ? "selected" : ""}
            onClick={() => setCategory(c)}
          >
            {c}
          </button>
        ))}
      </ChoiceRail>
      <div className="sort-row">
        <span aria-live="polite">
          {count} 件{category === "全部" ? "精选好物" : category}
        </span>
        <button
          className={hasPrice || sort !== "综合" ? "filter-active" : ""}
          onClick={() => setSheet({ type: "sort" })}
        >
          {sort === "综合" ? "筛选排序" : sort}
          {hasPrice && <i aria-label="价格筛选已生效" />}
          <SlidersHorizontal size={15} />
        </button>
      </div>
      {hasPrice && (
        <div className="active-filters">
          <button
            onClick={() => setPriceRange({ min: "", max: "" })}
            aria-label="清除价格筛选"
          >
            ¥{priceRange.min || "0"} —{" "}
            {priceRange.max ? `¥${priceRange.max}` : "不限"}
            <X size={13} />
          </button>
        </div>
      )}
    </div>
  );
}
function DiscoveryFilters() {
  const {
    category,
    setCategory,
    sort,
    setSort,
    priceRange,
    setPriceRange,
    query,
    route,
    setSheet,
  } = useLectus();
  const [draft, setDraft] = useState({ category, sort, ...priceRange });
  const [error, setError] = useState("");
  const result = filterProducts(
    products,
    draft.category,
    route.path === "search" ? query : "",
    draft.sort,
    draft,
  );
  const valid = validatePriceRange(draft.min, draft.max);
  const patch = (values) => {
    setDraft((d) => ({ ...d, ...values }));
    setError("");
  };
  return (
    <form
      className="discovery-filters"
      onSubmit={(e) => {
        e.preventDefault();
        if (valid) {
          setError(valid);
          return;
        }
        setCategory(draft.category);
        setSort(draft.sort);
        setPriceRange({ min: draft.min, max: draft.max });
        setSheet(null);
      }}
    >
      <fieldset>
        <legend>排序方式</legend>
        <div className="filter-options">
          {["综合", "人气优先", "价格升序", "价格降序"].map((x) => (
            <button
              type="button"
              key={x}
              className={draft.sort === x ? "selected" : ""}
              aria-pressed={draft.sort === x}
              onClick={() => patch({ sort: x })}
            >
              {x}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend>商品分类</legend>
        <div className="filter-options">
          {categories.map((x) => (
            <button
              type="button"
              key={x}
              className={draft.category === x ? "selected" : ""}
              aria-pressed={draft.category === x}
              onClick={() => patch({ category: x })}
            >
              {x}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend>
          价格区间 <span>人民币 / CNY</span>
        </legend>
        <div className="price-range">
          <label>
            <span>最低价</span>
            <input
              inputMode="decimal"
              type="number"
              min="0"
              step="any"
              placeholder="0"
              value={draft.min}
              aria-invalid={!!error}
              aria-describedby={error ? "price-error" : undefined}
              onChange={(e) => patch({ min: e.target.value })}
            />
          </label>
          <span>—</span>
          <label>
            <span>最高价</span>
            <input
              inputMode="decimal"
              type="number"
              min="0"
              step="any"
              placeholder="不限"
              value={draft.max}
              aria-invalid={!!error}
              aria-describedby={error ? "price-error" : undefined}
              onChange={(e) => patch({ max: e.target.value })}
            />
          </label>
        </div>
        {error && (
          <p className="filter-error" id="price-error" role="alert">
            {error}
          </p>
        )}
        <div className="price-presets">
          {[
            ["500 以下", "", "500"],
            ["500–1000", "500", "1000"],
            ["1000 以上", "1000", ""],
          ].map(([label, min, max]) => (
            <button
              type="button"
              key={label}
              className={
                draft.min === min && draft.max === max ? "selected" : ""
              }
              aria-pressed={draft.min === min && draft.max === max}
              onClick={() => patch({ min, max })}
            >
              {label}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="filter-actions">
        <button
          type="button"
          className="outline-button"
          onClick={() =>
            patch({ category: "全部", sort: "综合", min: "", max: "" })
          }
        >
          重置
        </button>
        <button className="black-button" type="submit">
          {valid ? "应用筛选" : `查看 ${result.length} 件好物`}
          <ArrowRight size={16} />
        </button>
      </div>
    </form>
  );
}
function HomePage() {
  const { nav, category, sort, priceRange, openProduct } =
    useLectus();
  const list = filterProducts(products, category, "", sort, priceRange);
  return (
    <>
      <div className="home-search">
        <button onClick={() => nav("search")} className="search-trigger">
          <Search size={17} />
          <span>找到你的下一件热爱</span>
          <span className="search-shortcut">搜索</span>
        </button>
        <IconButton label="商品分类" onClick={() => nav("categories")}>
          <Grid2X2 size={20} />
        </IconButton>
      </div>
      <section className="hero-editorial" aria-label="本周风格精选">
        <div className="edition-line"><span>THE EVERYDAY EDIT</span><span>VOL. 03 / 秋日选物</span></div>
        <div className="hero-heading">
          <h1>
            好风格，
            <br />
            不设限<span className="pink">.</span>
          </h1>
          <button className="edit-explore" onClick={scrollToStore}>探索本期<ArrowUpRight size={22} strokeWidth={1.4} /></button>
        </div>
        <div className="edit-collage">
          <button className="edit-main" onClick={() => openProduct(products[0].id)} aria-label={`探索${products[0].name}`}>
            <span className="product-orbit" aria-hidden="true" />
            <span className="product-podium" aria-hidden="true" />
            <Img name={products[0].image} alt={products[0].name} fetchPriority="high" />
            <span className="edit-image-label">01 / COURT CULTURE</span>
          </button>
          <button className="edit-side edit-outdoor" onClick={() => nav("community")} aria-label="探索山野灵感">
            <Img name="hiker" alt="山间徒步" /><span>去野<ArrowUpRight size={16} /></span>
          </button>
          <button className="edit-side edit-sound" onClick={() => openProduct("wp-macbook")} aria-label="探索 Apple MacBook Pro">
            <Img name="product-macbook" alt="MacBook Pro 笔记本电脑" /><span>轻装开工<ArrowUpRight size={16} /></span>
          </button>
          <button className="edit-caption" onClick={() => openProduct(products[0].id)}>
            <div><small>本周主角 / {products[0].brand}</small><strong>让日常，走出一点不同。</strong></div>
            <span>{money(products[0].price)}<ArrowUpRight size={20} /></span>
          </button>
        </div>
      </section>
      <div className="service-strip">
        <button onClick={() => nav("auth")}>
          <ShieldCheck size={15} />
          鉴别服务
        </button>
        <button onClick={() => nav("sell")}>
          <Store size={15} />
          闲置转售
        </button>
        <button onClick={() => nav("coupons")}>
          <Ticket size={15} />
          新人好礼
          <ArrowUpRight size={12} />
        </button>
      </div>
      <section className="store-section" id="store">
        <div className="section-heading">
          <div><span className="section-kicker">SELECTED FOR YOU</span><h2>值得一择<span className="pink">.</span></h2></div>
          <button onClick={() => nav("categories")}>
            发现更多
            <ArrowUpRight size={15} />
          </button>
        </div>
        <CatalogToolbar count={list.length} />
        <ProductGrid list={list} />
      </section>
      <button className="editorial-banner" onClick={() => nav("community")}>
        <Img name="mountain" alt="辽阔的山野" />
        <div>
          <span>把生活，穿成自己的样子。</span>
          <small>看见不同的生活主场</small>
        </div>
        <ArrowUpRight size={25} />
      </button>
      <Note />
    </>
  );
}
function SearchPage() {
  const {
    state,
    update,
    query,
    setQuery,
    category,
    setCategory,
    sort,
    priceRange,
    deferredQuery,
  } = useLectus();
  const [local, setLocal] = useState(query);
  useEffect(() => setLocal(query), [query]);
  const list = filterProducts(
    products,
    category,
    deferredQuery,
    sort,
    priceRange,
  );
  const search = (value) => {
    const clean = value.trim();
    setLocal(clean);
    setQuery(clean);
    setCategory("全部");
    update((s) => ({
      ...s,
      searchHistory: rememberSearch(s.searchHistory, clean),
    }));
  };
  return (
    <div className="page-pad search-page">
      <form
        className="search-form"
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          search(local);
        }}
      >
        <Search size={18} />
        <input
          aria-label="搜索商品"
          autoFocus
          placeholder="球鞋、品牌、生活灵感…"
          value={local}
          onChange={(e) => {
            setLocal(e.target.value);
            setQuery(e.target.value);
          }}
        />
        {local && (
          <IconButton
            label="清空搜索"
            onClick={() => {
              setLocal("");
              setQuery("");
            }}
            type="button"
          >
            <X size={16} />
          </IconButton>
        )}
        <button type="submit">搜索</button>
      </form>
      {!query.trim() && state.searchHistory.length > 0 && (
        <section className="recent-searches">
          <div>
            <h2>最近搜索</h2>
            <IconButton
              label="清除搜索记录"
              onClick={() => update((s) => ({ ...s, searchHistory: [] }))}
            >
              <Trash2 size={16} />
            </IconButton>
          </div>
          <div className="search-history">
            {state.searchHistory.map((t) => (
              <button key={t} onClick={() => search(t)}>
                {t}
              </button>
            ))}
          </div>
        </section>
      )}
      <div className="search-hot">
        <span>灵感热词</span>
        {["Nike", "New Balance", "衬衫", "香水"].map((t) => (
          <button key={t} onClick={() => search(t)}>
            {t}
            <ArrowUpRight size={12} />
          </button>
        ))}
      </div>
      <CatalogToolbar count={list.length} />
      <ProductGrid list={list} />
    </div>
  );
}
function CategoriesPage() {
  const { nav, category, setCategory } = useLectus();
  return (
    <div className="category-layout">
      <nav className="category-sidebar">
        {categories.map((c) => (
          <button
            key={c}
            className={category === c ? "active" : ""}
            onClick={() => setCategory(c)}
          >
            {c}
          </button>
        ))}
      </nav>
      <section>
        <h2>
          {category === "全部" ? "发现你的风格" : category}
          <span className="pink">.</span>
        </h2>
        <div className="category-visual">
          <Img
            name={(products.find((x) => category === "全部" || x.category === category) || products[0]).image}
            alt="分类灵感"
          />
          <span>
            CHOOSE WHAT
            <br />
            MOVES YOU.
          </span>
        </div>
        <ProductGrid list={filterProducts(products, category, "", "综合")} />
      </section>
    </div>
  );
}
function ShoeDemoPage() {
  const { back } = useLectus();
  const { t } = useI18n();
  return <>
    <ShoeViewer model={sampleShoeModel}/>
    <div className="page-pad"><button className="outline-button full" onClick={back}><ArrowLeft size={16}/>{t("返回上一页")}</button></div>
  </>;
}
function ProductPage() {
  const { t } = useI18n();
  const {
    state,
    nav,
    back,
    category,
    cartCount,
    favorite,
    openSku,
    price,
    p,
    setSheet,
    route,
  } = useLectus();
  const [imageIndex, setImageIndex] = useState(0);
  const previewModel = getMeshyPreviewModel(p.id, {
    enabled: route.params.get('preview3d') === 'meshy',
  });
  const [show3d, setShow3d] = useState(Boolean(previewModel));
  const model = getProductModel(p.id) || previewModel;
  useEffect(() => { setShow3d(Boolean(previewModel)); setImageIndex(0); }, [p.id, previewModel]);
  const galleryGesture = useRef(null);
  return (
    <>
      {model && <div className="detail-view-switch" role="group" aria-label={t("商品展示方式")}>
        <button aria-pressed={!show3d} onClick={() => setShow3d(false)}>{t("商品照片")}</button>
        <button aria-pressed={show3d} onClick={() => setShow3d(true)}><Box size={15}/>{t("商品3D展示")}</button>
      </div>}
      {show3d && model ? <ShoeViewer key={p.id} model={model}/> : <><div className="detail-gallery" data-view={imageIndex}
        onPointerDown={(event) => {
          if (!event.isPrimary || event.target.closest("button")) return;
          galleryGesture.current = { x: event.clientX, y: event.clientY, id: event.pointerId };
        }}
        onPointerCancel={() => { galleryGesture.current = null; }}
        onPointerUp={(event) => {
          const start = galleryGesture.current;
          galleryGesture.current = null;
          if (!start || start.id !== event.pointerId) return;
          const dx = event.clientX - start.x, dy = event.clientY - start.y;
          if (Math.abs(dx) > 45 && Math.abs(dx) > Math.abs(dy) * 1.4)
            setImageIndex((index) => (index + (dx < 0 ? 1 : 2)) % 3);
        }}>
        <Img
          name={p.image}
          alt={p.name}
          className={`detail-shot shot-${imageIndex}`}
        />
        <IconButton
          label={`收藏${p.name}`}
          className="glass favorite-float"
          onClick={() => favorite(p.id)}
          aria-pressed={state.favorites.includes(p.id)}
        >
          <Heart
            size={20}
            fill={state.favorites.includes(p.id) ? "currentColor" : "none"}
          />
        </IconButton>
        <button
          className="gallery-arrow gallery-prev"
          aria-label="上一张图片"
          onClick={() => setImageIndex((imageIndex + 2) % 3)}
        >
          <ArrowLeft size={17} />
        </button>
        <button
          className="gallery-arrow gallery-next"
          aria-label="下一张图片"
          onClick={() => setImageIndex((imageIndex + 1) % 3)}
        >
          <ArrowRight size={17} />
        </button>
        <span className="glass image-counter">细节 {imageIndex + 1} / 3</span>
      </div>
      <div className="detail-dots">
        {[0, 1, 2].map((i) => (
          <button
            key={i}
            onClick={() => setImageIndex(i)}
            aria-label={`图片取景${i + 1}`}
            className={i === imageIndex ? "current" : ""}
          >
            <i />
          </button>
        ))}
      </div>
      </>}
      <div className="page-pad detail-body">
        {p.category === "鞋履" && !model && <div className="product-model-note">
          <p>{t("该款暂未提供专属3D模型")}</p>
          <button onClick={() => nav("shoe-demo")}>{t("查看独立3D演示")}<ArrowUpRight size={15}/></button>
        </div>}
        <div className="tiny-brand">{p.en}</div>
        <div className="detail-title-row">
          <h1>{p.name}</h1>
          <b>{money(p.price)}</b>
        </div>
        <p className="muted">{p.description}</p>
        <div className="detail-proof">
          <span>
            <ShieldCheck size={14} />
            可申请鉴别
          </span>
          <span>
            <Package size={14} />
            全新商品 · 样例
          </span>
        </div>
        <button className="row-link" onClick={() => openSku(p, "cart")}>
          <span>选择规格</span>
          <span>
            {state.variantPreferences[p.id]
              ? `${state.variantPreferences[p.id].size} / ${state.variantPreferences[p.id].color}`
              : "尺码 / 颜色"}
            <ChevronRight size={16} />
          </span>
        </button>
        <button className="row-link" onClick={() => nav("coupons")}>
          <span>优惠</span>
          <span className="pink">
            领取新人优惠
            <ChevronRight size={16} />
          </span>
        </button>
        <button
          className="row-link"
          onClick={() => setSheet({ type: "delivery" })}
        >
          <span>配送与服务</span>
          <span>
            查看说明
            <ChevronRight size={16} />
          </span>
        </button>
        <div className="detail-section">
          <h2>关于这件好物</h2>
          <dl>
            <div>
              <dt>系列品牌</dt>
              <dd>{p.brand}</dd>
            </div>
            <div>
              <dt>所属分类</dt>
              <dd>{p.category}</dd>
            </div>
            <div>
              <dt>商品编号</dt>
              <dd>LX-{p.id.toUpperCase()}-001</dd>
            </div>
            <div>
              <dt>规格与库存</dt>
              <dd>前端演示规格，未连接实时库存</dd>
            </div>
          </dl>
          <Note>图片分页为同一摄影的细节取景；上架前需替换真实商品资料。</Note>
        </div>
        <div className="section-heading">
          <h2>搭配灵感</h2>
          <button onClick={() => nav("community")}>
            去社区
            <ArrowUpRight size={16} />
          </button>
        </div>
        <ProductGrid list={products.filter((x) => x.id !== p.id).slice(0, 4)} />
      </div>
      <div className="fixed-action purchase-actions glass">
        <IconButton label="咨询客服" onClick={() => nav("chat")}>
          <Headphones size={21} />
          <small>客服</small>
        </IconButton>
        <IconButton label="打开购物袋" onClick={() => nav("cart")}>
          <ShoppingBag size={21} />
          <small>购物袋</small>
          {cartCount > 0 && <i className="badge">{cartCount}</i>}
        </IconButton>
        <button className="outline-button" onClick={() => openSku(p, "cart")}>
          加入购物袋
        </button>
        <button className="black-button" onClick={() => openSku(p, "buy")}>
          立即购买
        </button>
      </div>
    </>
  );
}
function CartPage() {
  const {
    state,
    update,
    nav,
    cartCount,
    selectedTotal,
    startCartCheckout,
    checkout,
  } = useLectus();
  return (
    <div className="page-pad cart-page">
      {state.cart.length ? (
        <>
          <div className="cart-intro">
            <span>{cartCount} 件好物，等待出发。</span>
            <button
              onClick={() =>
                update((s) => ({
                  ...s,
                  cart: s.cart.map((x) => ({
                    ...x,
                    checked: !s.cart.every((y) => y.checked),
                  })),
                }))
              }
            >
              {state.cart.every((x) => x.checked) ? "取消全选" : "全选"}
            </button>
          </div>
          {state.cart.map((row) => (
            <div className="cart-card" key={skuKey(row)}>
              <button
                className={`check-control ${row.checked ? "is-checked" : ""}`}
                aria-label={`选择${getProduct(row.id)?.name}`}
                aria-pressed={row.checked}
                onClick={() =>
                  update((s) => ({
                    ...s,
                    cart: s.cart.map((x) =>
                      skuKey(x) === skuKey(row)
                        ? { ...x, checked: !x.checked }
                        : x,
                    ),
                  }))
                }
              >
                {row.checked && <Check size={13} />}
              </button>
              <div className="cart-card-product">
                <ProductRow row={row}>
                  <div className="cart-card-bottom">
                    <Stepper
                      min={0}
                      value={row.qty}
                      onChange={(value) =>
                        update((s) => ({
                          ...s,
                          cart: s.cart
                            .map((x) =>
                              skuKey(x) === skuKey(row)
                                ? { ...x, qty: value }
                                : x,
                            )
                            .filter((x) => x.qty > 0),
                        }))
                      }
                    />
                    <IconButton
                      label={`删除${getProduct(row.id)?.name}`}
                      onClick={() =>
                        update((s) => ({
                          ...s,
                          cart: s.cart.filter((x) => skuKey(x) !== skuKey(row)),
                        }))
                      }
                    >
                      <Trash2 size={16} />
                    </IconButton>
                  </div>
                </ProductRow>
              </div>
            </div>
          ))}
          <Note>结算为本机流程演示，不会发起真实付款。</Note>
          <div className="fixed-action cart-checkout glass">
            <div>
              <small>合计（不含运费）</small>
              <strong>{money(selectedTotal.total)}</strong>
            </div>
            <button className="black-button" onClick={startCartCheckout}>
              去结算 ({selectedTotal.count})<ArrowRight size={17} />
            </button>
          </div>
        </>
      ) : (
        <Empty
          title="还没把喜欢装进来"
          text="挑几件好物，让热爱从这里出发。"
          action="去逛逛"
          onAction={() => nav("home")}
        />
      )}
    </div>
  );
}
function CheckoutPage() {
  const { nav, checkout, coupon, price, address, createOrder, p, setSheet } =
    useLectus();
  return (
    <div className="page-pad">
      {checkout?.length ? (
        <>
          <button
            className="address-choose surface"
            onClick={() => nav("addresses", { select: "1" })}
          >
            <MapPin size={22} />
            <div>
              {address ? (
                <>
                  <b>
                    {address.name} <span>{address.phone}</span>
                  </b>
                  <p>
                    {address.region}
                    {address.detail}
                  </p>
                </>
              ) : (
                <>
                  <b>添加收货地址</b>
                  <p>填写后即可继续订单演示</p>
                </>
              )}
            </div>
            <ChevronRight size={18} />
          </button>
          <div className="surface order-products">
            {checkout.map((row) => (
              <ProductRow row={row} key={skuKey(row)}>
                <small>数量 × {row.qty}</small>
              </ProductRow>
            ))}
          </div>
          <div className="surface bill">
            <h3>订单明细</h3>
            <div>
              <span>商品金额</span>
              <b>{money(price.subtotal)}</b>
            </div>
            <div>
              <span>运费</span>
              <span>¥0 · 演示</span>
            </div>
            <button onClick={() => setSheet({ type: "coupon-select" })}>
              <span>优惠券</span>
              <span>
                {price.discount
                  ? `− ${money(price.discount)}`
                  : "选择可用优惠券"}
                <ChevronRight size={15} />
              </span>
            </button>
            <div className="bill-total">
              <span>应付合计</span>
              <strong>{money(price.total)}</strong>
            </div>
          </div>
          <Note>地址保存在当前浏览器。支付、配送与订单状态均为本地模拟。</Note>
          <div className="fixed-action glass">
            <div className="checkout-amount">
              <small>应付</small>
              <b>{money(price.total)}</b>
            </div>
            <button className="black-button" onClick={createOrder}>
              提交演示订单
              <ArrowRight size={17} />
            </button>
          </div>
        </>
      ) : (
        <Empty
          title="暂时没有结算商品"
          action="去购物袋"
          onAction={() => nav("cart")}
        />
      )}
    </div>
  );
}
function OrdersPage() {
  const { state, nav, category, price, orderFilter, setOrderFilter } =
    useLectus();
  const filters = ["全部", "待付款", "待发货", "待收货", "已完成", "售后中"];
  const list = state.orders.filter(
    (x) => orderFilter === "全部" || x.status === orderFilter,
  );
  return (
    <div className="page-pad">
      <div className="category-scroll">
        {filters.map((f) => (
          <button
            key={f}
            className={orderFilter === f ? "selected" : ""}
            onClick={() => setOrderFilter(f)}
          >
            {f}
          </button>
        ))}
      </div>
      {list.length ? (
        list.map((o) => (
          <button
            className="order-card surface"
            key={o.id}
            onClick={() => nav("order", { id: o.id })}
          >
            <div className="order-card-heading">
              <span>LECTUS 莱择</span>
              <span className="pink">{o.status}</span>
            </div>
            {o.items.map((x, i) => (
              <div className="mini-order-product" key={i}>
                <Img name={getProduct(x.id)?.image || "shoe"} alt="订单商品" />
                <div>
                  <b>{getProduct(x.id)?.name}</b>
                  <small>
                    {x.size} / {x.color} × {x.qty}
                  </small>
                </div>
              </div>
            ))}
            <div className="order-card-foot">
              <span>实付 {money(o.price.total)}</span>
              <span>
                查看详情
                <ChevronRight size={14} />
              </span>
            </div>
          </button>
        ))
      ) : (
        <Empty
          icon={Package}
          title="还没有这类订单"
          action="去发现好物"
          onAction={() => nav("home")}
        />
      )}
    </div>
  );
}
function OrderPage() {
  const {
    state,
    update,
    nav,
    price,
    address,
    paymentBusy,
    payOrder,
    cancelOrder,
    route,
    p,
    setSheet,
  } = useLectus();
  const o = state.orders.find((x) => x.id === route.params.get("id"));
  if (!o)
    return (
      <Empty
        title="未找到订单"
        action="查看全部订单"
        onAction={() => nav("orders")}
      />
    );
  const progress = ["待付款", "待发货", "待收货", "已完成"];
  return (
    <div className="page-pad">
      <div className="order-state">
        <span className="tiny-brand">YOUR LECTUS ORDER</span>
        <h1>
          {o.status}
          <span className="pink">.</span>
        </h1>
        <p>
          {
            {
              待付款: "订单已准备好，完成模拟支付继续体验。",
              待发货: "已完成模拟付款，可继续体验发货与物流。",
              待收货: "你的好物正在路上，物流为示例数据。",
              已完成: "感谢你，让热爱有了新的去处。",
              已取消: "本地订单已取消，没有任何扣款。",
              售后中: "售后申请已记录，等待模拟处理。",
              已退款: "退款流程演示完成，没有资金流转。",
            }[o.status]
          }
        </p>
      </div>
      <div className="order-progress">
        {progress.map((x, i) => (
          <span
            key={x}
            className={progress.indexOf(o.status) >= i ? "passed" : ""}
          >
            <i />
            {x}
          </span>
        ))}
      </div>
      <div className="surface order-products">
        {o.items.map((x, i) => (
          <ProductRow row={x} key={i}>
            <small>× {x.qty}</small>
          </ProductRow>
        ))}
      </div>
      <div className="surface bill">
        <div>
          <span>订单编号</span>
          <small>{o.id}</small>
        </div>
        <div>
          <span>收货信息</span>
          <span>
            {o.address.name} · {o.address.phone}
          </span>
        </div>
        <p>
          {o.address.region}
          {o.address.detail}
        </p>
        <div>
          <span>优惠金额</span>
          <span>−{money(o.price.discount)}</span>
        </div>
        <div className="bill-total">
          <span>实付金额</span>
          <b>{money(o.price.total)}</b>
        </div>
      </div>
      {["待收货", "已完成"].includes(o.status) && (
        <button
          className="row-link surface"
          onClick={() => setSheet({ type: "logistics", order: o })}
        >
          <Truck size={19} />
          查看物流轨迹
          <ChevronRight size={17} />
        </button>
      )}
      <div className="order-actions">
        {o.status === "待付款" && (
          <>
            <button className="outline-button" onClick={() => cancelOrder(o)}>
              取消订单
            </button>
            <button
              className="black-button"
              disabled={paymentBusy}
              onClick={() => payOrder(o)}
            >
              {paymentBusy ? "正在模拟…" : "模拟付款（不扣款）"}
            </button>
          </>
        )}
        {o.status === "待发货" && (
          <button
            className="black-button"
            onClick={() =>
              update((s) => ({
                ...s,
                orders: s.orders.map((x) =>
                  x.id === o.id ? { ...x, status: "待收货" } : x,
                ),
              }))
            }
          >
            模拟商家发货
            <Truck size={17} />
          </button>
        )}
        {o.status === "待收货" && (
          <button
            className="black-button"
            onClick={() =>
              update((s) => ({
                ...s,
                orders: s.orders.map((x) =>
                  x.id === o.id ? { ...x, status: "已完成" } : x,
                ),
              }))
            }
          >
            确认收货（演示）
            <Check size={17} />
          </button>
        )}
        {["待发货", "待收货", "已完成"].includes(o.status) && (
          <button
            className="outline-button"
            onClick={() => nav("refund", { id: o.id })}
          >
            申请售后
          </button>
        )}
        {o.status === "售后中" && (
          <button
            className="black-button"
            onClick={() =>
              update((s) => ({
                ...s,
                orders: s.orders.map((x) =>
                  x.id === o.id ? { ...x, status: "已退款" } : x,
                ),
              }))
            }
          >
            模拟售后处理完成
          </button>
        )}
      </div>
      <Note />
    </div>
  );
}

function CommunityPage() {
  const {
    state,
    nav,
    communityTab,
    setCommunityTab,
    postFilter,
    setPostFilter,
    toggleLike,
  } = useLectus();
  const all = [...state.localPosts, ...posts]
    .filter((x) => communityTab !== "关注" || state.followed.includes(x.author))
    .filter((x) => postFilter === "全部" || x.topic === postFilter);
  return (
    <div className="page-pad community">
      <div className="community-masthead"><span className="section-kicker">THE LECTUS JOURNAL</span><h1>灵感现场<span className="pink">.</span></h1><p>穿什么，去哪儿。生活由你定义。</p></div>
      <div className="community-heading">
        <div>
          {["发现", "关注"].map((t) => (
            <button
              className={communityTab === t ? "active" : ""}
              aria-pressed={communityTab === t}
              key={t}
              onClick={() => setCommunityTab(t)}
            >
              {t}
            </button>
          ))}
        </div>
        <IconButton
          label="发布灵感"
          className="black-circle"
          onClick={() => nav("publish")}
        >
          <Plus size={20} />
        </IconButton>
      </div>
      <button
        className="community-feature"
        onClick={() => {
          setPostFilter("周末去野");
          setCommunityTab("发现");
        }}
      >
        <Img name="mountain" alt="山峰" />
        <div>
          <span className="feature-kicker">WEEKEND / OFFLINE</span>
          <span>
            把周末，
            <br />
            还给旷野。
          </span>
        </div>
        <span className="feature-footer"><span># 周末去野<small>从城市出发，寻找另一种节奏</small></span><ArrowUpRight size={23} /></span>
      </button>
      <div className="journal-label"><span>发现同频的人</span><small>STYLE, SHARED.</small></div>
      <ChoiceRail selected={postFilter} className="topic-scroll">
        {["全部", "周末去野", "今日穿搭", "球鞋日记", "露营生活"].map((t) => (
          <button
            key={t}
            className={postFilter === t ? "selected" : ""}
            aria-pressed={postFilter === t}
            onClick={() => setPostFilter(t)}
          >
            {t === "全部" ? t : "# " + t}
          </button>
        ))}
      </ChoiceRail>
      {all.length ? (
        <div className="community-grid" key={`${communityTab}-${postFilter}`}>
          {all.map((post, i) => (
            <article className="post-card" key={post.id}>
              <button
                onClick={() => nav("post", { id: post.id })}
                className="post-image"
              >
                <Img name={post.image} alt={post.title} loading="lazy" />
                {i === 0 && (
                  <span className="glass post-featured">精选灵感</span>
                )}
              </button>
              <button
                className="post-title"
                onClick={() => nav("post", { id: post.id })}
              >
                {post.title}
              </button>
              <div className="post-meta">
                <span>
                  <i>{post.avatar}</i>
                  {post.author.split(" / ")[0]}
                </span>
                <button
                  aria-label={`点赞${post.title}`}
                  aria-pressed={state.likes.includes(post.id)}
                  onClick={() => toggleLike(post.id)}
                >
                  <Heart
                    size={13}
                    fill={
                      state.likes.includes(post.id) ? "currentColor" : "none"
                    }
                  />
                  {post.likes + (state.likes.includes(post.id) ? 1 : 0)}
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <Empty
          icon={Compass}
          title="关注有趣的人"
          text="在灵感详情中关注作者，这里会出现他们的动态。"
          action="去发现"
          onAction={() => {
            setCommunityTab("发现");
            setPostFilter("全部");
          }}
        />
      )}
      <Note>社区内容为演示素材，你发布的文字仅保存在本机。</Note>
    </div>
  );
}
function PostPage() {
  const { state, update, notify, openProduct, price, toggleLike, route, p } =
    useLectus();
  const post = [...state.localPosts, ...posts].find(
    (x) => x.id === route.params.get("id"),
  );
  const [comment, setComment] = useState(""),
    [comments, setComments] = useState([]);
  if (!post) return <Empty title="这条灵感不存在" />;
  return (
    <>
      <div className="post-author">
        <span className="avatar">{post.avatar}</span>
        <div>
          <b>{post.author}</b>
          <small>用自己的方式，定义日常。</small>
        </div>
        <button
          className={
            state.followed.includes(post.author)
              ? "outline-button"
              : "black-button"
          }
          onClick={() =>
            update((s) => ({
              ...s,
              followed: s.followed.includes(post.author)
                ? s.followed.filter((x) => x !== post.author)
                : [...s.followed, post.author],
            }))
          }
        >
          {state.followed.includes(post.author) ? "已关注" : "关注"}
        </button>
      </div>
      <Img name={post.image} alt={post.title} className="post-detail-image" />
      <div className="page-pad">
        <h1 className="post-detail-title">{post.title}</h1>
        <p className="post-text">{post.text}</p>
        <span className="pink"># {post.topic}</span>
        {post.product && (
          <button
            className="post-linked-product surface"
            onClick={() => openProduct(post.product)}
          >
            <Img name={getProduct(post.product).image} alt="相关好物" />
            <div>
              <small>灵感同款</small>
              <b>{getProduct(post.product).name}</b>
              <span>{money(getProduct(post.product).price)}</span>
            </div>
            <ArrowUpRight size={19} />
          </button>
        )}
        <div className="post-actions">
          <button
            onClick={() => toggleLike(post.id)}
            aria-pressed={state.likes.includes(post.id)}
          >
            <Heart
              size={20}
              fill={state.likes.includes(post.id) ? "currentColor" : "none"}
            />
            {post.likes + (state.likes.includes(post.id) ? 1 : 0)}
          </button>
          <button
            onClick={() => {
              navigator.clipboard
                ?.writeText(location.href)
                .then(() => notify("已复制灵感链接"))
                .catch(() => notify("当前浏览器无法复制链接"));
            }}
          >
            <Share2 size={19} />
            分享
          </button>
        </div>
        <h3>聊聊这份灵感</h3>
        {comments.map((x, i) => (
          <p className="local-comment" key={i}>
            <b>{state.profile.name}</b>
            {x}
          </p>
        ))}
        <form
          className="comment-form"
          onSubmit={(e) => {
            e.preventDefault();
            if (comment.trim()) {
              setComments((x) => [...x, comment.trim()]);
              setComment("");
              notify("评论已添加到当前页面");
            }
          }}
        >
          <input
            aria-label="写下评论"
            placeholder="写下你的想法…"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={200}
          />
          <button aria-label="发送评论">
            <Send size={18} />
          </button>
        </form>
        <Note />
      </div>
    </>
  );
}
function AuthPage() {
  const { nav, p } = useLectus();
  return (
    <div className="page-pad auth-page">
      <div className="auth-hero">
        <div className="auth-orbit">
          <ScanLine size={64} strokeWidth={1} />
          <span className="scan-beam" />
        </div>
        <h1>
          让每一份热爱，
          <br />
          更有底气<span className="pink">.</span>
        </h1>
        <p>从细节出发，认识你手中的好物。</p>
        <button className="black-button" onClick={() => nav("authform")}>
          申请图片鉴别
          <ArrowUpRight size={18} />
        </button>
        <small>流程演示 · 不提供真实鉴别结论</small>
      </div>
      <div className="auth-steps">
        {[
          ["01", "提交商品图片", "上传细节，补充商品信息"],
          ["02", "查看受理进度", "体验模拟受理与进度展示"],
          ["03", "查看演示报告", "了解报告结构与结果说明"],
        ].map((x) => (
          <div key={x[0]}>
            <span>{x[0]}</span>
            <div>
              <h3>{x[1]}</h3>
              <p>{x[2]}</p>
            </div>
          </div>
        ))}
      </div>
      <button className="row-link surface" onClick={() => nav("authrecord")}>
        <FileCheck2 size={20} />
        我的鉴别记录
        <ChevronRight size={18} />
      </button>
      <button className="row-link surface" onClick={() => nav("support")}>
        <CircleHelp size={20} />
        鉴别服务说明
        <ChevronRight size={18} />
      </button>
    </div>
  );
}
function MessagesPage() {
  const { state, update, notify, nav, route, p } = useLectus();
  const msgs = [
    {
      id: "service",
      icon: Headphones,
      title: "莱择服务助手",
      text: "选购、订单与售后，有问题来找我。",
      time: "在线",
      route: "chat",
    },
    {
      id: "orders",
      icon: Package,
      title: "交易通知",
      text: state.orders.length
        ? `你有 ${state.orders.length} 笔本地演示订单`
        : "订单进展，将在这里为你更新。",
      time: "系统",
      route: "orders",
    },
    {
      id: "community",
      icon: Heart,
      title: "互动消息",
      text: "遇见同频的喜欢，发现新的灵感。",
      time: "社区",
      route: "community",
    },
    {
      id: "auth",
      icon: ShieldCheck,
      title: "鉴别进度",
      text: state.appraisals.length
        ? `${state.appraisals.length} 条鉴别申请等待查看`
        : "申请鉴别后，可在这里查看进度。",
      time: "服务",
      route: "authrecord",
    },
  ];
  return (
    <div className="page-pad">
      <div className="messages-intro">
        <h1>
          每一条消息，
          <br />
          都有回响<span className="pink">.</span>
        </h1>
        <button
          onClick={() => {
            update((s) => ({ ...s, readMessages: msgs.map((x) => x.id) }));
            notify("已将消息标记为已读");
          }}
        >
          <CheckCheck size={17} />
          全部已读
        </button>
      </div>
      {msgs.map((x) => (
        <button
          className="message-row"
          key={x.id}
          onClick={() => {
            update((s) => ({
              ...s,
              readMessages: [...new Set([...s.readMessages, x.id])],
            }));
            nav(x.route);
          }}
        >
          <span className="message-icon">
            <x.icon size={23} />
            {!state.readMessages.includes(x.id) && <i />}
          </span>
          <div>
            <b>{x.title}</b>
            <p>{x.text}</p>
          </div>
          <small>{x.time}</small>
        </button>
      ))}
    </div>
  );
}
function MinePage() {
  const { state, nav, setOrderFilter, p } = useLectus();
  return (
    <div className="page-pad mine">
      <div className="profile-header">
        <span className="profile-avatar">
          L<span className="pink">.</span>
        </span>
        <div>
          <h1>{state.profile.name}</h1>
          <p>{state.profile.bio}</p>
          <small>LECTUS ID · LX00001</small>
        </div>
        <IconButton label="个人设置" onClick={() => nav("settings")}>
          <Settings size={20} />
        </IconButton>
      </div>
      <div className="profile-stats">
        {[
          [state.favorites.length, "收藏", "favorites"],
          [state.history.length, "足迹", "history"],
          [state.claimed.length, "优惠券", "coupons"],
        ].map(([n, label, path]) => (
          <button key={path} onClick={() => nav(path)}>
            <b>{n}</b>
            <span>{label}</span>
          </button>
        ))}
      </div>
      <section className="order-hub surface">
        <div className="section-heading">
          <h2>我的订单</h2>
          <button
            onClick={() => {
              setOrderFilter("全部");
              nav("orders");
            }}
          >
            全部订单
            <ChevronRight size={15} />
          </button>
        </div>
        <div className="order-shortcuts">
          {[
            [Wallet, "待付款"],
            [Package, "待发货"],
            [Truck, "待收货"],
            [RotateCcw, "售后中"],
          ].map(([Icon, label]) => (
            <button
              key={label}
              onClick={() => {
                setOrderFilter(label);
                nav("orders");
              }}
            >
              <Icon size={23} />
              <span>{label === "售后中" ? "退换 / 售后" : label}</span>
              {state.orders.filter((x) => x.status === label).length > 0 && (
                <i>{state.orders.filter((x) => x.status === label).length}</i>
              )}
            </button>
          ))}
        </div>
      </section>
      <button className="seller-banner" onClick={() => nav("seller")}>
        <Store size={27} />
        <div>
          <b>让闲置，成为新的喜欢。</b>
          <small>进入卖家中心 / SELL WITH LECTUS</small>
        </div>
        <ArrowUpRight size={22} />
      </button>
      <div className="surface mine-links">
        {[
          [MapPin, "收货地址", "addresses"],
          [UserRound, "账号与云端订单", "account"],
          [ShieldCheck, "鉴别记录", "authrecord"],
          [Ticket, "优惠券中心", "coupons"],
          [Headphones, "客服与帮助", "support"],
          [Settings, "个人设置", "settings"],
        ].map(([Icon, label, path]) => (
          <button className="row-link" key={path} onClick={() => nav(path)}>
            <Icon size={19} />
            <span>{label}</span>
            <ChevronRight size={17} />
          </button>
        ))}
      </div>
      <Note />
    </div>
  );
}

function AddressesPage() {
  const { state, update, notify, nav, checkout, address, route, p } =
    useLectus();
  const [edit, setEdit] = useState(null),
    [form, setForm] = useState({
      name: "",
      phone: "",
      region: "",
      detail: "",
      isDefault: false,
    }),
    [errors, setErrors] = useState({});
  const submit = (e) => {
    e.preventDefault();
    const errs = validateAddress(form);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const data = { ...form, id: edit === "new" ? "addr" + Date.now() : edit };
    update((s) => {
      let rows = s.addresses.filter((x) => x.id !== data.id);
      if (data.isDefault) rows = rows.map((x) => ({ ...x, isDefault: false }));
      return { ...s, addresses: [...rows, data] };
    });
    setEdit(null);
    notify("地址已保存到本机");
  };
  return (
    <div className="page-pad">
      {edit ? (
        <form className="form-stack surface" onSubmit={submit} noValidate>
          <h2>{edit === "new" ? "添加地址" : "编辑地址"}</h2>
          {[
            ["name", "收件人", "请输入姓名"],
            ["phone", "手机号", "请输入 11 位手机号"],
            ["region", "所在地区", "省 / 市 / 区"],
            ["detail", "详细地址", "街道、楼栋与门牌号"],
          ].map(([key, label, placeholder]) => (
            <label key={key}>
              {label}
              <input
                value={form[key]}
                onChange={(e) =>
                  setForm((x) => ({ ...x, [key]: e.target.value }))
                }
                placeholder={placeholder}
                inputMode={key === "phone" ? "tel" : "text"}
                aria-invalid={!!errors[key]}
                aria-describedby={errors[key] ? key + "-error" : undefined}
              />
              {errors[key] && (
                <span className="field-error" id={key + "-error"}>
                  {errors[key]}
                </span>
              )}
            </label>
          ))}
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.isDefault}
              onChange={(e) =>
                setForm((x) => ({ ...x, isDefault: e.target.checked }))
              }
            />
            设为默认地址
          </label>
          <button className="black-button" type="submit">
            保存地址
          </button>
          <button
            type="button"
            className="outline-button"
            onClick={() => setEdit(null)}
          >
            取消
          </button>
          <Note>可填写虚构信息体验流程，请勿在演示中输入真实敏感信息。</Note>
        </form>
      ) : (
        <>
          {state.addresses.length ? (
            state.addresses.map((a) => (
              <div key={a.id} className="surface address-card">
                <div>
                  <b>
                    {a.name} <span>{a.phone}</span>
                  </b>
                  {a.isDefault && <small className="pill">默认</small>}
                </div>
                <p>
                  {a.region}
                  {a.detail}
                </p>
                <div className="address-card-actions">
                  {route.params.get("select") && (
                    <button
                      onClick={() => {
                        update((s) => ({
                          ...s,
                          addresses: s.addresses.map((x) => ({
                            ...x,
                            isDefault: x.id === a.id,
                          })),
                        }));
                        nav("checkout");
                      }}
                    >
                      使用此地址
                      <Check size={15} />
                    </button>
                  )}
                  <button
                    onClick={() => {
                      setForm({ ...a });
                      setEdit(a.id);
                      setErrors({});
                    }}
                  >
                    编辑
                  </button>
                  <button
                    onClick={() => {
                      update((s) => ({
                        ...s,
                        addresses: s.addresses.filter((x) => x.id !== a.id),
                      }));
                      notify("地址已删除");
                    }}
                  >
                    删除
                  </button>
                </div>
              </div>
            ))
          ) : (
            <Empty
              icon={MapPin}
              title="收货地址还是空的"
              text="添加地址后，可以体验完整的订单流程。"
            />
          )}
          <button
            className="black-button full"
            onClick={() => {
              setEdit("new");
              setForm({
                name: "",
                phone: "",
                region: "",
                detail: "",
                isDefault: !state.addresses.length,
              });
              setErrors({});
            }}
          >
            <Plus size={18} />
            添加收货地址
          </button>
        </>
      )}
    </div>
  );
}
function CouponPage() {
  const { state, update, notify, coupon, p } = useLectus();
  return (
    <div className="page-pad">
      <div className="page-intro">
        <h1>
          给喜欢，
          <br />
          一个好理由<span className="pink">.</span>
        </h1>
        <p>莱择好礼，与你见面。</p>
      </div>
      {coupons.map((c) => (
        <div className="coupon-card" key={c.id}>
          <div>
            <small>¥</small>
            <strong>{c.amount}</strong>
            <span>满 {c.minimum} 可用</span>
          </div>
          <section>
            <h3>{c.name}</h3>
            <p>{c.desc}</p>
          </section>
          <button
            onClick={() => {
              if (state.claimed.includes(c.id)) {
                notify("结算时可选择这张优惠券");
                return;
              }
              update((s) => ({ ...s, claimed: [...s.claimed, c.id] }));
              notify("已领取，结算时可以使用");
            }}
          >
            {state.claimed.includes(c.id) ? "已领取" : "领取"}
          </button>
        </div>
      ))}
      <Note>优惠为本地演示规则，无实际权益。</Note>
    </div>
  );
}
function SellerPage() {
  const { state, nav, price } = useLectus();
  return (
    <div className="page-pad">
      <div className="seller-intro">
        <Store size={32} />
        <h1>
          新的喜欢，
          <br />
          从你这里开始<span className="pink">.</span>
        </h1>
        <button className="black-button" onClick={() => nav("sell")}>
          发布闲置
          <Plus size={17} />
        </button>
      </div>
      <div className="seller-stats surface">
        <div>
          <strong>{state.listings.length}</strong>
          <span>我的商品</span>
        </div>
        <div>
          <strong>
            {state.listings.filter((x) => x.status === "上架中").length}
          </strong>
          <span>上架中</span>
        </div>
        <div>
          <strong>0</strong>
          <span>成交 · 演示</span>
        </div>
      </div>
      <h2 className="small-heading">我的出售</h2>
      {state.listings.length ? (
        state.listings.map((x) => (
          <button
            className="listing-row surface"
            key={x.id}
            onClick={() => nav("listing", { id: x.id })}
          >
            <Img name={x.image} alt={x.title} />
            <div>
              <b>{x.title}</b>
              <small>
                {x.condition} · {x.status}
              </small>
              <strong>{money(Number(x.price))}</strong>
            </div>
            <ChevronRight size={18} />
          </button>
        ))
      ) : (
        <Empty
          icon={Store}
          title="让好物继续被喜欢"
          text="发布仅保存在本机，不会上架到真实平台。"
        />
      )}
    </div>
  );
}
function SellPage() {
  const { update, notify, nav, category, price, p } = useLectus();
  const [f, setF] = useState({
      title: "",
      category: "球鞋",
      price: "",
      condition: "全新未使用",
      description: "",
      image: "shoe",
    }),
    [error, setError] = useState("");
  return (
    <div className="page-pad">
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (
            !f.title.trim() ||
            !Number.isFinite(Number(f.price)) ||
            Number(f.price) <= 0
          ) {
            setError("请填写商品名称及大于 0 的有效售价");
            return;
          }
          const id = "sell" + Date.now();
          update((s) => ({
            ...s,
            listings: [{ ...f, id, status: "上架中" }, ...s.listings],
          }));
          nav("listing", { id });
          notify("已发布到本地卖家中心");
        }}
      >
        <p className="muted">给闲置一个新去处，分享给下一位同好。</p>
        <label>展示图片</label>
        <div className="image-picker">
          {["shoe", "boots", "jacket", "backpack"].map((im) => (
            <button
              type="button"
              className={f.image === im ? "selected" : ""}
              key={im}
              onClick={() => setF((x) => ({ ...x, image: im }))}
            >
              <Img name={im} alt={`使用${im}示例图`} />
              {f.image === im && <Check size={15} />}
            </button>
          ))}
        </div>
        <small className="muted">本轮使用演示图片，真实商品上传待接入。</small>
        <label>
          商品名称
          <input
            placeholder="品牌、型号与商品名称"
            value={f.title}
            onChange={(e) => setF((x) => ({ ...x, title: e.target.value }))}
            maxLength={50}
          />
        </label>
        <label>
          所属分类
          <select
            value={f.category}
            onChange={(e) => setF((x) => ({ ...x, category: e.target.value }))}
          >
            {categories
              .filter((x) => x !== "全部")
              .map((x) => (
                <option key={x}>{x}</option>
              ))}
          </select>
        </label>
        <label>
          成色
          <select
            value={f.condition}
            onChange={(e) => setF((x) => ({ ...x, condition: e.target.value }))}
          >
            {["全新未使用", "几乎全新", "轻微使用痕迹", "明显使用痕迹"].map(
              (x) => (
                <option key={x}>{x}</option>
              ),
            )}
          </select>
        </label>
        <label>
          售价（元）
          <input
            type="number"
            min="1"
            max="999999"
            placeholder="输入你的期望售价"
            value={f.price}
            onChange={(e) => setF((x) => ({ ...x, price: e.target.value }))}
          />
        </label>
        <label>
          商品描述
          <textarea
            placeholder="描述商品细节、附件和使用情况…"
            value={f.description}
            onChange={(e) =>
              setF((x) => ({ ...x, description: e.target.value }))
            }
            maxLength={500}
          />
        </label>
        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}
        <button className="black-button">
          发布演示商品
          <ArrowUpRight size={17} />
        </button>
        <Note />
      </form>
    </div>
  );
}
function AuthForm() {
  const { update, notify, nav, p } = useLectus();
  const [f, setF] = useState({ brand: "", name: "", note: "" }),
    [images, setImages] = useState([]),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const inputRef = useRef();
  const files = (e) => {
    const items = [...e.target.files].slice(0, 4);
    if (
      items.some(
        (x) =>
          !["image/jpeg", "image/png", "image/webp"].includes(x.type) ||
          x.size > 5 * 1024 * 1024,
      )
    ) {
      setError("请选择 JPG、PNG 或 WebP 图片，单张不超过 5MB");
      return;
    }
    Promise.all(
      items.map(
        (file) =>
          new Promise((resolve) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.readAsDataURL(file);
          }),
      ),
    ).then(setImages);
  };
  return (
    <div className="page-pad">
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!f.brand.trim() || !f.name.trim() || images.length < 1) {
            setError("请填写品牌、商品名称，并至少添加一张图片");
            return;
          }
          setBusy(true);
          const id = "AU" + Date.now().toString().slice(-8);
          update((s) => ({
            ...s,
            appraisals: [
              {
                ...f,
                id,
                images: [],
                imageCount: images.length,
                status: "待受理",
                date: new Date().toLocaleDateString("zh-CN"),
              },
              ...s.appraisals,
            ],
          }));
          setBusy(false);
          nav("authrecord");
          notify("鉴别申请已保存在本机");
        }}
      >
        <div className="form-banner">
          <ScanLine size={25} />
          <p>
            上传细节，让认识更进一步。
            <small>本地流程演示，不提供真伪判断。</small>
          </p>
        </div>
        <label>
          商品品牌
          <input
            value={f.brand}
            onChange={(e) => setF((x) => ({ ...x, brand: e.target.value }))}
            placeholder="填写品牌名称"
            maxLength={50}
          />
        </label>
        <label>
          商品名称
          <input
            value={f.name}
            onChange={(e) => setF((x) => ({ ...x, name: e.target.value }))}
            placeholder="型号、款式或系列"
            maxLength={80}
          />
        </label>
        <label>细节图片（最多 4 张）</label>
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          hidden
          onChange={files}
        />
        <div className="upload-grid">
          {images.map((src, i) => (
            <img key={i} src={src} alt={`鉴别细节${i + 1}`} />
          ))}
          <button
            type="button"
            className="upload-button"
            onClick={() => inputRef.current?.click()}
          >
            <ImagePlus size={23} />
            <span>选择图片</span>
          </button>
        </div>
        <small className="muted">
          仅在当前页面预览，不会上传。记录只保存图片数量。
        </small>
        <label>
          补充说明
          <textarea
            value={f.note}
            onChange={(e) => setF((x) => ({ ...x, note: e.target.value }))}
            placeholder="可以补充购买渠道、关注的细节等"
            maxLength={300}
          />
        </label>
        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}
        <button className="black-button" disabled={busy}>
          {busy ? "正在记录…" : "提交演示申请"}
        </button>
      </form>
    </div>
  );
}
function AuthRecords() {
  const { state, nav, p, setSheet } = useLectus();
  return (
    <div className="page-pad">
      {state.appraisals.length ? (
        state.appraisals.map((a) => (
          <div key={a.id} className="surface appraisal">
            <div>
              <span>{a.id}</span>
              <span className="pill">{a.status}</span>
            </div>
            <h3>{a.name}</h3>
            <p>
              {a.brand} · {a.imageCount} 张图片 · {a.date}
            </p>
            <button
              className="outline-button"
              onClick={() => setSheet({ type: "report", data: a })}
            >
              查看进度与报告
              <FileCheck2 size={16} />
            </button>
          </div>
        ))
      ) : (
        <Empty
          icon={ScanLine}
          title="还没有鉴别申请"
          action="开始申请"
          onAction={() => nav("authform")}
        />
      )}
      <Note>报告只演示服务流程，不能作为商品真伪凭证。</Note>
    </div>
  );
}
function PublishPage() {
  const { update, notify, nav, p } = useLectus();
  const [f, setF] = useState({
      title: "",
      text: "",
      topic: "今日穿搭",
      image: "jacket",
    }),
    [error, setError] = useState("");
  return (
    <div className="page-pad">
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!f.title.trim() || !f.text.trim()) {
            setError("给灵感写一个标题，再聊聊你的想法");
            return;
          }
          const id = "local" + Date.now();
          update((s) => ({
            ...s,
            localPosts: [
              {
                ...f,
                id,
                author: s.profile.name,
                avatar: s.profile.name.slice(0, 1),
                likes: 0,
              },
              ...s.localPosts,
            ],
          }));
          nav("post", { id });
          notify("灵感已发布到本机社区");
        }}
      >
        <h2>热爱，值得被看见。</h2>
        <div className="image-picker">
          {["hiker", "jacket", "sneaker", "tent"].map((im) => (
            <button
              key={im}
              type="button"
              className={f.image === im ? "selected" : ""}
              onClick={() => setF((x) => ({ ...x, image: im }))}
            >
              <Img name={im} alt="灵感示例图" />
            </button>
          ))}
        </div>
        <small className="muted">选择一张演示摄影</small>
        <label>
          标题
          <input
            placeholder="给这份灵感起个名字"
            maxLength={40}
            value={f.title}
            onChange={(e) => setF((x) => ({ ...x, title: e.target.value }))}
          />
        </label>
        <label>
          你的想法
          <textarea
            placeholder="穿搭心得、出行日记、好物分享…"
            value={f.text}
            onChange={(e) => setF((x) => ({ ...x, text: e.target.value }))}
            maxLength={1000}
          />
        </label>
        <label>
          选择话题
          <select
            value={f.topic}
            onChange={(e) => setF((x) => ({ ...x, topic: e.target.value }))}
          >
            {["今日穿搭", "周末去野", "球鞋日记", "露营生活"].map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
        {error && (
          <p className="field-error" role="alert">
            {error}
          </p>
        )}
        <button className="black-button">
          发布灵感
          <ArrowUpRight size={17} />
        </button>
        <Note>仅保存到当前浏览器，不向真实用户发布。</Note>
      </form>
    </div>
  );
}
function SettingsPage() {
  const { state, update, notify, setCheckout, p } = useLectus();
  const { preference, language, setPreference, options, t } = useI18n();
  const [f, setF] = useState(state.profile);
  return (
    <div className="page-pad">
      <form
        className="form-stack"
        onSubmit={(e) => {
          e.preventDefault();
          if (!f.name.trim()) {
            notify("昵称不能为空");
            return;
          }
          update((s) => ({
            ...s,
            profile: { name: f.name.trim(), bio: f.bio.trim() },
          }));
          notify("个人资料已保存");
        }}
      >
        <div className="profile-avatar big">
          L<span className="pink">.</span>
        </div>
        <label>
          昵称
          <input
            value={f.name}
            onChange={(e) => setF((x) => ({ ...x, name: e.target.value }))}
            maxLength={20}
          />
        </label>
        <label>
          个人签名
          <input
            value={f.bio}
            onChange={(e) => setF((x) => ({ ...x, bio: e.target.value }))}
            maxLength={60}
          />
        </label>
        <button className="black-button">保存个人资料</button>
      </form>
      <section className="surface language-settings" aria-labelledby="language-title">
        <div className="settings-section-heading">
          <div>
            <span className="section-kicker">GLOBAL READY</span>
            <h2 id="language-title">{t("语言与地区")}</h2>
          </div>
          <span className="language-globe" aria-hidden="true">◎</span>
        </div>
        <label className="language-select-label" htmlFor="language-select">{t("界面语言")}</label>
        <select
          id="language-select"
          className="language-select"
          value={preference}
          onChange={(e) => { setPreference(e.target.value); notify(t("语言偏好已保存")); }}
        >
          {options.map((option) => <option key={option.id} value={option.id}>{option.native} · {option[language === "ja" ? "japanese" : language === "en" ? "english" : "native"]}</option>)}
        </select>
        <p className="language-current"><span className="language-dot" /> {t("当前语言")}: {options.find((option) => option.id === language)?.native || options.find((option) => option.id === "en")?.native}</p>
        <p className="language-note">{t("价格显示为人民币（CNY），语言切换不会改变价格。")}</p>
      </section>
      <div className="surface settings-about">
        <h3>关于 Lectus｜莱择</h3>
        <p>选择你的生活主场。</p>
        <small>React 前端原型 · v0.1.0</small>
        <Note>
          当前为本地访客身份，未接入真实登录、实名认证和隐私协议服务。
        </Note>
      </div>
      <details className="reset-demo">
        <summary>重置本机演示数据</summary>
        <p>清空当前浏览器内的样例订单、购物袋和个人设置，回到初始状态。</p>
        <button
          className="outline-button full"
          onClick={() => {
            update(() => initialState());
            setCheckout(null);
            notify("演示数据已重置");
          }}
        >
          确认重置（仅本机）
        </button>
      </details>
    </div>
  );
}
function ChatPage() {
  const { state, update, p, chatText, setChatText } = useLectus();
  const base = [
    {
      role: "assistant",
      text: "你好，这里是莱择服务助手（演示）。你可以询问订单、鉴别、退换或配送。",
    },
  ];
  const send = (e) => {
    e.preventDefault();
    if (!chatText.trim()) return;
    const text = chatText.trim(),
      answer = /退|换|售后/.test(text)
        ? "在「我的订单」打开订单，可申请退换售后。本原型中的审核和退款均为本地模拟。"
        : /鉴别|真假/.test(text)
          ? "前往「鉴别」提交图片申请。本原型演示申请与报告结构，不会作出真实真伪结论。"
          : /物流|配送|发货/.test(text)
            ? "订单详情中可模拟发货，查看示例物流轨迹；当前没有真实包裹。"
            : "你可以在「我的」查看订单，或在首页搜索感兴趣的商品。这是本地规则回复，尚未连接人工客服。";
    update((s) => ({
      ...s,
      messages: [
        ...s.messages,
        { role: "user", text },
        { role: "assistant", text: answer },
      ],
    }));
    setChatText("");
  };
  return (
    <div className="chat-page">
      <span className="chat-status">
        <i />
        服务助手 · 演示会话
      </span>
      {[...base, ...state.messages].map((m, i) => (
        <div key={i} className={`chat-bubble ${m.role}`}>
          {m.role === "assistant" && <span className="chat-avatar">L.</span>}
          <p>{m.text}</p>
        </div>
      ))}
      <form className="chat-compose glass" onSubmit={send}>
        <input
          aria-label="咨询内容"
          placeholder="关于订单、鉴别、售后…"
          value={chatText}
          onChange={(e) => setChatText(e.target.value)}
          maxLength={500}
        />
        <button className="black-circle" aria-label="发送消息">
          <Send size={19} />
        </button>
      </form>
    </div>
  );
}
function SupportPage() {
  const { nav, p } = useLectus();
  return (
    <div className="page-pad">
      <div className="page-intro">
        <h1>
          有疑问，
          <br />
          我们一起解答<span className="pink">.</span>
        </h1>
      </div>
      {[
        [
          "如何购买商品？",
          "商品详情选择尺码与颜色后，可加入购物袋或立即购买。提交本地订单后可模拟付款、发货和确认收货。",
        ],
        [
          "鉴别服务如何使用？",
          "在鉴别页上传商品细节，填写品牌与型号。当前只演示申请、进度与报告页面，不提供真实鉴别结论。",
        ],
        [
          "如何申请退换？",
          "在已付款订单详情点击申请售后，填写原因与说明。当前为本地流程，退款没有真实资金流转。",
        ],
        [
          "发货时间与配送范围？",
          "演示商品没有实际库存和配送承诺；正式上线后需要接入商家与物流规则。",
        ],
        [
          "如何出售闲置？",
          "在我的进入卖家中心，填写商品名称、价格与成色，即可体验本地发布、下架与重新上架。",
        ],
      ].map(([q, a]) => (
        <details className="faq" key={q}>
          <summary>
            {q}
            <Plus size={16} />
          </summary>
          <p>{a}</p>
        </details>
      ))}
      <button className="black-button full" onClick={() => nav("chat")}>
        <Headphones size={18} />
        咨询服务助手
      </button>
    </div>
  );
}
function RefundPage() {
  const { state, update, notify, nav, price, route } = useLectus();
  const o = state.orders.find((x) => x.id === route.params.get("id"));
  const [reason, setReason] = useState("不喜欢 / 不合适"),
    [text, setText] = useState("");
  return (
    <div className="page-pad">
      {o ? (
        <form
          className="form-stack"
          onSubmit={(e) => {
            e.preventDefault();
            update((s) => ({
              ...s,
              orders: s.orders.map((x) =>
                x.id === o.id
                  ? { ...x, status: "售后中", refund: { reason, text } }
                  : x,
              ),
            }));
            nav("order", { id: o.id });
            notify("售后申请已保存");
          }}
        >
          <div className="surface bill">
            <h3>退换与售后</h3>
            <div>
              <span>订单编号</span>
              <small>{o.id}</small>
            </div>
            <div>
              <span>可申请金额</span>
              <b>{money(o.price.total)}</b>
            </div>
          </div>
          <label>
            售后原因
            <select value={reason} onChange={(e) => setReason(e.target.value)}>
              {[
                "不喜欢 / 不合适",
                "商品质量问题",
                "商品与描述不符",
                "其他原因",
              ].map((x) => (
                <option key={x}>{x}</option>
              ))}
            </select>
          </label>
          <label>
            补充说明
            <textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={300}
              placeholder="描述遇到的问题，便于后续处理"
            />
          </label>
          <button className="black-button">提交售后演示申请</button>
          <Note />
        </form>
      ) : (
        <Empty title="未找到订单" />
      )}
    </div>
  );
}
function ListingPage() {
  const { state, update, category, price, route, p } = useLectus();
  const item = state.listings.find((x) => x.id === route.params.get("id"));
  return item ? (
    <div className="page-pad">
      <Img
        name={item.image}
        alt={item.title}
        className="listing-detail-image"
      />
      <span className="pill">{item.status}</span>
      <h1 className="listing-title">{item.title}</h1>
      <p>
        {item.category} / {item.condition}
      </p>
      <h2>{money(Number(item.price))}</h2>
      <p className="muted">{item.description || "卖家未补充描述"}</p>
      <button
        className="black-button full"
        onClick={() =>
          update((s) => ({
            ...s,
            listings: s.listings.map((x) =>
              x.id === item.id
                ? { ...x, status: x.status === "上架中" ? "已下架" : "上架中" }
                : x,
            ),
          }))
        }
      >
        {item.status === "上架中" ? "下架演示商品" : "重新上架演示商品"}
      </button>
      <Note>此商品仅存在于本机卖家中心，没有公开发布。</Note>
    </div>
  ) : (
    <Empty title="商品记录不存在" />
  );
}

function App() {
  const motionRoot = usePressFeedback();
  const i18n = useI18n();
  const [state, setState] = useState(readState),
    [route, setRoute] = useState(getRoute),
    [toast, setToast] = useState(""),
    [sheet, setSheet] = useState(null),
    [query, setQuery] = useState(""),
    [category, setCategory] = useState("全部"),
    [sort, setSort] = useState("综合"),
    [priceRange, setPriceRange] = useState({ min: "", max: "" }),
    [sku, setSku] = useState(null),
    [skuSize, setSkuSize] = useState(""),
    [skuColor, setSkuColor] = useState(""),
    [qty, setQty] = useState(1),
    [checkout, setCheckout] = useState(null),
    [checkoutFromCart, setCheckoutFromCart] = useState(false),
    [coupon, setCoupon] = useState(""),
    [orderFilter, setOrderFilter] = useState("全部"),
    [communityTab, setCommunityTab] = useState("发现"),
    [postFilter, setPostFilter] = useState("全部"),
    [chatText, setChatText] = useState(""),
    [paymentBusy, setPaymentBusy] = useState(false);
  const deferredQuery = useDeferredValue(query),
    toastTimer = useRef(),
    paymentTimer = useRef(),
    pendingPaymentId = useRef(null),
    scrollPositions = useRef(new Map()),
    entryKey = useRef("initial"),
    entrySequence = useRef(0);
  const resetDiscovery = () => {
    setQuery("");
    setCategory("全部");
    setPriceRange({ min: "", max: "" });
  };
  const update = (fn) => setState((s) => fn(s));
  const notify = (text, action = null) => {
    clearTimeout(toastTimer.current);
    setToast({ text, action });
    toastTimer.current = setTimeout(() => setToast(""), action ? 5000 : 2300);
  };
  const nav = (path, params = {}) => {
    setSheet(null);
    setSku(null);
    const qs = new URLSearchParams(params).toString(),
      target = path + (qs ? "?" + qs : "");
    if (location.hash.slice(1) === target) return;
    history.pushState(
      {
        lectusDepth: (history.state?.lectusDepth || 0) + 1,
        lectusKey: `entry-${++entrySequence.current}`,
      },
      "",
      "#" + target,
    );
    window.dispatchEvent(new HashChangeEvent("hashchange"));
  };
  const back = () => {
    setSheet(null);
    setSku(null);
    if ((history.state?.lectusDepth || 0) > 0) history.back();
    else nav("home");
  };
  useEffect(() => {
    const handle = () => {
      scrollPositions.current.set(
        entryKey.current,
        document.querySelector(".phone-scroll")?.scrollTop || 0,
      );
      entryKey.current =
        history.state?.lectusKey || `entry-${++entrySequence.current}`;
      if (!history.state?.lectusKey)
        history.replaceState(
          { ...history.state, lectusKey: entryKey.current },
          "",
        );
      setRoute(getRoute());
      setSheet(null);
      setSku(null);
      setToast("");
    };
    window.addEventListener("hashchange", handle);
    history.replaceState({ lectusDepth: 0, lectusKey: "initial" }, "");
    return () => window.removeEventListener("hashchange", handle);
  }, []);

  useLayoutEffect(() => {
    document.querySelector(".phone-scroll")?.scrollTo({
      top: scrollPositions.current.get(entryKey.current) || 0,
      behavior: "instant",
    });
  }, [route]);
  useEffect(() => {
    try {
      localStorage.setItem("lectus-v1", JSON.stringify(state));
    } catch {
      notify("本机存储空间不足，当前操作仍可继续");
    }
  }, [state]);
  useEffect(() => {
    if (route.path === "chat") {
      const pane = document.querySelector(".phone-scroll");
      pane?.scrollTo({
        top: pane.scrollHeight,
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
          ? "instant"
          : "smooth",
      });
    }
  }, [state.messages.length, route.path]);
  useEffect(
    () => () => {
      clearTimeout(toastTimer.current);
      clearTimeout(paymentTimer.current);
    },
    [],
  );
  const cartCount = state.cart.reduce((n, x) => n + x.qty, 0),
    isMain = ["home", "community", "auth", "messages", "mine"].includes(
      route.path,
    );
  const favorite = (id) =>
    update((s) => ({
      ...s,
      favorites: s.favorites.includes(id)
        ? s.favorites.filter((x) => x !== id)
        : [...s.favorites, id],
    }));
  const openProduct = (id) => {
    update((s) => ({
      ...s,
      history: [id, ...s.history.filter((x) => x !== id)].slice(0, 30),
    }));
    nav("product", { id });
  };
  const openSku = (p, mode) => {
    setSku({ product: p, mode });
    const remembered = state.variantPreferences[p.id];
    setSkuSize(remembered?.size || (p.sizes.length === 1 ? p.sizes[0] : ""));
    setSkuColor(remembered?.color || p.colors[0]);
    setQty(1);
  };
  const finishSku = () => {
    if (!skuSize) {
      notify("先选择适合你的尺码");
      return;
    }
    const row = {
      id: sku.product.id,
      size: skuSize,
      color: skuColor,
      qty,
      checked: true,
    };
    update((s) => ({
      ...s,
      variantPreferences: {
        ...s.variantPreferences,
        [row.id]: { size: row.size, color: row.color },
      },
    }));
    if (sku.mode === "buy") {
      setCheckout([row]);
      setCheckoutFromCart(false);
      setCoupon("");
      setSku(null);
      nav("checkout");
    } else {
      update((s) => ({ ...s, cart: addCart(s.cart, row, allProducts) }));
      setSku(null);
      notify("已加入购物袋", { label: "查看", path: "cart" });
    }
  };
  const selectedTotal = totals(state.cart, allProducts);
  const startCartCheckout = () => {
    if (!selectedTotal.count) {
      notify("请先选择需要结算的商品");
      return;
    }
    setCheckout(selectedTotal.selected);
    setCheckoutFromCart(true);
    setCoupon("");
    nav("checkout");
  };
  const activeCoupon = coupons.find(
      (x) => x.id === coupon && state.claimed.includes(x.id),
    ),
    price = totals(checkout || [], allProducts, activeCoupon);
  const address =
    state.addresses.find((x) => x.isDefault) || state.addresses[0];
  const createOrder = () => {
    if (!address) {
      nav("addresses", { select: "1" });
      return;
    }
    if (!checkout?.length) {
      nav("cart");
      return;
    }
    const id = "LX" + Date.now().toString().slice(-11);
    const order = {
      id,
      items: checkout.map((x) => ({ ...x })),
      price: {
        subtotal: price.subtotal,
        discount: price.discount,
        total: price.total,
      },
      address: { ...address },
      status: "待付款",
      createdAt: new Date().toISOString(),
    };
    update((s) => ({
      ...s,
      orders: [order, ...s.orders],
      cart: checkoutFromCart
        ? s.cart.filter((x) => !checkout.some((y) => skuKey(y) === skuKey(x)))
        : s.cart,
    }));
    setCheckout(null);
    nav("order", { id });
    notify("已创建本地演示订单");
  };
  const payOrder = (o) => {
    if (paymentBusy || o.status !== "待付款") return;
    setPaymentBusy(true);
    pendingPaymentId.current = o.id;
    paymentTimer.current = setTimeout(() => {
      update((s) => ({
        ...s,
        orders: s.orders.map((x) =>
          x.id === o.id && x.status === "待付款"
            ? { ...x, status: "待发货" }
            : x,
        ),
      }));
      setPaymentBusy(false);
      pendingPaymentId.current = null;
      notify("模拟支付完成，没有扣款");
    }, 650);
  };
  const cancelOrder = (o) => {
    if (pendingPaymentId.current === o.id) {
      clearTimeout(paymentTimer.current);
      pendingPaymentId.current = null;
      setPaymentBusy(false);
    }
    update((s) => ({
      ...s,
      orders: s.orders.map((x) =>
        x.id === o.id && x.status === "待付款" ? { ...x, status: "已取消" } : x,
      ),
    }));
    notify("演示订单已取消，没有扣款");
  };
  const toggleLike = (id) =>
    update((s) => ({
      ...s,
      likes: s.likes.includes(id)
        ? s.likes.filter((x) => x !== id)
        : [...s.likes, id],
    }));
  const titleMap = {
    account: "莱择账号",
    cart: "购物袋",
    search: "找一件喜欢",
    categories: "分类",
    checkout: "确认订单",
    orders: "我的订单",
    order: "订单详情",
    addresses: "收货地址",
    coupons: "优惠券",
    favorites: "我的收藏",
    history: "最近看过",
    sell: "我要出售",
    seller: "卖家中心",
    authform: "申请鉴别",
    authrecord: "鉴别记录",
    publish: "发布灵感",
    settings: "个人设置",
    chat: "莱择服务助手",
    support: "帮助与售后",
    refund: "申请售后",
    post: "灵感详情",
    listing: "出售详情",
    "shoe-demo": "独立3D演示",
  };
  const p = getProduct(route.params.get("id")) || products[0];
  const pages = {
    account: <AccountPage />,
    home: <CatalogStore route={route} nav={nav} />,
    search: <CatalogStore route={route} nav={nav} />,
    categories: <CatalogStore route={route} nav={nav} />,
    product: route.params.get("id")?.startsWith("SPU-") ? <CatalogProduct key={route.params.get("id")} id={route.params.get("id")} nav={nav} /> : <ProductPage key={p.id} />,
    "shoe-demo": <ShoeDemoPage />,
    cart: <CartPage />,
    checkout: <CheckoutPage />,
    orders: <OrdersPage />,
    order: <OrderPage />,
    community: <CommunityPage />,
    post: <PostPage key={route.params.get("id")} />,
    auth: <AuthPage />,
    messages: <MessagesPage />,
    mine: <MinePage />,
    addresses: <AddressesPage />,
    coupons: <CouponPage />,
    seller: <SellerPage />,
    sell: <SellPage />,
    authform: <AuthForm />,
    authrecord: <AuthRecords />,
    publish: <PublishPage />,
    settings: <SettingsPage />,
    chat: <ChatPage />,
    support: <SupportPage />,
    refund: <RefundPage />,
    listing: <ListingPage />,
    favorites: (
      <div className="page-pad">
        <SavedCatalog nav={nav}>{allProducts.some(x => state.favorites.includes(x.id)) && <ProductGrid
          list={allProducts.filter((x) => state.favorites.includes(x.id))}
        />}</SavedCatalog>
      </div>
    ),
    history: (
      <div className="page-pad">
        <SavedCatalog nav={nav} history>{state.history.map(getProduct).filter(Boolean).length > 0 && <ProductGrid list={state.history.map(getProduct).filter(Boolean)} />}</SavedCatalog>
      </div>
    ),
  };
  const isModal = !!sheet || !!sku;
  useEffect(() => {
    if (!isModal) return;
    const previous = document.activeElement;
    const focusable = () =>
      [
        ...document.querySelectorAll(
          ".overlay-layer button,.overlay-layer input,.overlay-layer select",
        ),
      ].filter((x) => !x.disabled);
    const first = focusable()[0];
    first?.focus({ preventScroll: true });
    const handler = (e) => {
      if (e.key === "Escape") {
        setSheet(null);
        setSku(null);
      }
      if (e.key === "Tab") {
        const els = focusable(),
          a = els[0],
          z = els[els.length - 1];
        if (e.shiftKey && document.activeElement === a) {
          e.preventDefault();
          z?.focus();
        } else if (!e.shiftKey && document.activeElement === z) {
          e.preventDefault();
          a?.focus();
        }
      }
    };
    document.addEventListener("keydown", handler);
    return () => {
      document.removeEventListener("keydown", handler);
      previous?.isConnected && previous.focus({ preventScroll: true });
    };
  }, [isModal]);
  return (
    <LectusContext.Provider
      value={{
        state,
        ...i18n,
        update,
        notify,
        nav,
        back,
        query,
        setQuery,
        category,
        setCategory,
        sort,
        setSort,
        priceRange,
        setPriceRange,
        resetDiscovery,
        deferredQuery,
        cartCount,
        favorite,
        openProduct,
        openSku,
        selectedTotal,
        startCartCheckout,
        checkout,
        setCheckout,
        coupon,
        setCoupon,
        price,
        address,
        createOrder,
        orderFilter,
        setOrderFilter,
        paymentBusy,
        payOrder,
        cancelOrder,
        communityTab,
        setCommunityTab,
        postFilter,
        setPostFilter,
        toggleLike,
        route,
        p,
        setSheet,
        chatText,
        setChatText,
      }}
    >
      <div className="desktop-stage" ref={motionRoot}>
        <aside className="desktop-intro">
          <div className="desktop-wordmark">
            <LectusLogo variant="desktop" />
          </div>
          <div className="desktop-rule" />
          <h1>
            风格有千万种，
            <br />
            热爱，自成一择<span>.</span>
          </h1>
          <p>
            从日常好物到生活灵感，
            <br />
            选择你的生活主场。
          </p>
          <div className="desktop-footer">
            <span>THE ART OF CHOOSING.</span>
            <span>移动商城 · 商品库预览</span>
          </div>
          <div className="desktop-shortcuts">
            <button onClick={() => nav("home")}>
              精选商城
              <ArrowUpRight size={15} />
            </button>
            <button onClick={() => nav("community")}>
              灵感社区
              <ArrowUpRight size={15} />
            </button>
            <button onClick={() => nav("mine")}>
              个人中心
              <ArrowUpRight size={15} />
            </button>
          </div>
        </aside>
        <div className="device-shell">
          <div className="phone-app">
            <div className="app-underlay" inert={isModal || undefined}>
              <header
                className={`app-header glass ${route.path === "product" ? "detail-header" : ""}`}
              >
                {isMain ? (
                  <>
                    <button
                      className="brand"
                      onClick={() => nav("home")}
                      aria-label="Lectus 莱择首页"
                    >
                      <LectusLogo />
                    </button>
                    <div className="header-actions">
                      <span className="preview-pill">
                        <i />
                        PREVIEW
                      </span>
                      <IconButton
                        label={`购物袋，${cartCount}件商品`}
                        onClick={() => nav("cart")}
                      >
                        <ShoppingBag size={21} />
                        {cartCount > 0 && (
                          <i className="badge" key={cartCount}>
                            {cartCount > 99 ? "99+" : cartCount}
                          </i>
                        )}
                      </IconButton>
                    </div>
                  </>
                ) : (
                  <>
                    <IconButton label="返回" onClick={back}>
                      <ArrowLeft size={21} />
                    </IconButton>
                    <span className="page-header-title">
                      {route.path === "product"
                        ? "好物详情"
                        : titleMap[route.path] || "Lectus 莱择"}
                    </span>
                    <IconButton label="回到首页" onClick={() => nav("home")}>
                      <Home size={19} />
                    </IconButton>
                  </>
                )}
              </header>
              <main
                className={`phone-scroll route-${route.path} ${!isMain ? "subpage" : ""}`}
              >
                <div className="route-content" key={route.path}>
                {pages[route.path] || (
                  <Empty
                    title="页面不存在"
                    action="返回首页"
                    onAction={() => nav("home")}
                  />
                )}
                </div>
              </main>
              {isMain && (
                <ChoiceRail as="nav" selected={route.path} className="bottom-navigation glass" aria-label="主导航">
                  {[
                    [Home, "精选", "home"],
                    [Compass, "社区", "community"],
                    [ScanLine, "鉴别", "auth"],
                    [MessageCircle, "消息", "messages"],
                    [UserRound, "我的", "mine"],
                  ].map(([Icon, label, path]) => (
                    <button
                      key={path}
                      className={route.path === path ? "active" : ""}
                      aria-current={route.path === path ? "page" : undefined}
                      onClick={() => nav(path)}
                    >
                      <span className="nav-icon-wrap">
                        <Icon
                          size={21}
                          strokeWidth={route.path === path ? 2 : 1.5}
                        />
                        {path === "messages" &&
                          state.readMessages.length < 4 && <i />}
                      </span>
                      <span>{label}</span>
                      {route.path === path && (
                        <span className="nav-active-dot" />
                      )}
                    </button>
                  ))}
                </ChoiceRail>
              )}
            </div>
            <Presence show={isModal} className="modal-presence">
              <div
                className="overlay-layer"
                onClick={() => {
                  setSheet(null);
                  setSku(null);
                }}
              >
                <section
                  className="bottom-sheet"
                  role="dialog"
                  aria-modal="true"
                  aria-labelledby="sheet-title"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="sheet-grabber" />
                  <div className="sheet-head">
                    <h2 id="sheet-title">
                      {sku
                        ? "选择你的那一款"
                        : {
                            sort: "筛选你的热爱",
                            delivery: "配送与服务",
                            logistics: "物流轨迹 · 示例",
                            report: "鉴别进度 · 演示",
                            "coupon-select": "选择优惠券",
                          }[sheet?.type]}
                    </h2>
                    <IconButton
                      label="关闭弹层"
                      onClick={() => {
                        setSheet(null);
                        setSku(null);
                      }}
                    >
                      <X size={19} />
                    </IconButton>
                  </div>
                  <div className="sheet-body">
                    {sku && (
                      <>
                        <div className="sku-summary">
                          <Img
                            name={sku.product.image}
                            alt={sku.product.name}
                          />
                          <div>
                            <span className="tiny-brand">
                              {sku.product.brand}
                            </span>
                            <h3>{sku.product.name}</h3>
                            <strong>{money(sku.product.price)}</strong>
                            <small>
                              {skuSize
                                ? `${skuSize} / ${skuColor}`
                                : "请选择规格"}
                            </small>
                          </div>
                        </div>
                        <div className="sku-option">
                          <span>尺码 / SIZE</span>
                          <div className="sku-sizes">
                            {sku.product.sizes.map((s) => (
                              <button
                                key={s}
                                className={skuSize === s ? "selected" : ""}
                                aria-pressed={skuSize === s}
                                onClick={() => setSkuSize(s)}
                              >
                                {s}
                              </button>
                            ))}
                          </div>
                        </div>
                        <div className="sku-option">
                          <span>配色 / COLOR</span>
                          <div className="sku-sizes">
                            {sku.product.colors.map((c) => (
                              <button
                                key={c}
                                className={skuColor === c ? "selected" : ""}
                                aria-pressed={skuColor === c}
                                onClick={() => setSkuColor(c)}
                              >
                                {c}
                              </button>
                            ))}
                          </div>
                        </div>
                        <div className="sku-quantity">
                          <span>数量</span>
                          <Stepper value={qty} onChange={setQty} />
                        </div>
                        <div className="sku-total">
                          <span>
                            商品合计 <small>{qty} 件</small>
                          </span>
                          <strong>{money(sku.product.price * qty)}</strong>
                        </div>
                        <button
                          className="black-button full"
                          onClick={finishSku}
                        >
                          {sku.mode === "buy" ? "确认，进入结算" : "加入购物袋"}
                          <ArrowRight size={17} />
                        </button>
                        <Note>规格与库存为样例数据。</Note>
                      </>
                    )}
                    {sheet?.type === "sort" && <DiscoveryFilters />}
                    {sheet?.type === "delivery" && (
                      <div className="service-explanation">
                        <ShieldCheck size={35} />
                        <h3>每一步，都更清楚。</h3>
                        <p>
                          本轮为独立前端原型，商品库存、配送时效、质保与鉴别能力尚未接入真实服务。
                        </p>
                        <p>
                          结算展示的运费为演示值。正式规则需要在接入商家、物流与鉴别团队后确认。
                        </p>
                        <button
                          className="black-button full"
                          onClick={() => nav("support")}
                        >
                          查看帮助中心
                        </button>
                      </div>
                    )}
                    {sheet?.type === "coupon-select" && (
                      <>
                        <button
                          className="sheet-option"
                          onClick={() => {
                            setCoupon("");
                            setSheet(null);
                          }}
                        >
                          不使用优惠券{!coupon && <Check size={18} />}
                        </button>
                        {coupons
                          .filter((c) => state.claimed.includes(c.id))
                          .map((c) => (
                            <button
                              className="sheet-option coupon-option"
                              disabled={price.subtotal < c.minimum}
                              key={c.id}
                              onClick={() => {
                                setCoupon(c.id);
                                setSheet(null);
                              }}
                            >
                              <div>
                                <b>
                                  {c.name} −{money(c.amount)}
                                </b>
                                <small>
                                  {price.subtotal < c.minimum
                                    ? `满${c.minimum}可用，当前未达门槛`
                                    : c.desc}
                                </small>
                              </div>
                              {coupon === c.id && <Check size={18} />}
                            </button>
                          ))}
                        {!state.claimed.length && (
                          <Empty
                            icon={Ticket}
                            title="还没有领取优惠券"
                            action="去领券"
                            onAction={() => nav("coupons")}
                          />
                        )}
                      </>
                    )}
                    {sheet?.type === "logistics" && (
                      <div className="timeline">
                        {[
                          ["运输中", "包裹正在前往收货城市"],
                          ["已发出", "演示商家已完成打包"],
                          ["已创建", "订单信息已生成"],
                        ].map(([a, b], i) => (
                          <div key={a}>
                            <i className={i === 0 ? "pink-bg" : ""} />
                            <b>{a}</b>
                            <p>{b}</p>
                            <small>模拟物流 · 无真实运单</small>
                          </div>
                        ))}
                      </div>
                    )}
                    {sheet?.type === "report" && (
                      <div className="report">
                        <div className="report-seal">
                          <FileCheck2 size={40} />
                        </div>
                        <h3>{sheet.data.name}</h3>
                        <span className="pill">{sheet.data.status}</span>
                        <p>申请编号 {sheet.data.id}</p>
                        <p>已记录 {sheet.data.imageCount} 张图片的提交信息。</p>
                        <p className="muted">
                          这是一份演示报告，不包含真实真伪判断，也不构成鉴别凭证。
                        </p>
                        {sheet.data.status === "待受理" && (
                          <button
                            className="black-button full"
                            onClick={() => {
                              update((s) => ({
                                ...s,
                                appraisals: s.appraisals.map((x) =>
                                  x.id === sheet.data.id
                                    ? { ...x, status: "演示已受理" }
                                    : x,
                                ),
                              }));
                              setSheet(null);
                              notify("已推进模拟进度");
                            }}
                          >
                            模拟受理申请
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </section>
              </div>
            </Presence>
            <Presence show={!!toast} className="toast-presence">
              <div className="toast glass-dark" role="status">
                <CheckCircle2 size={17} />
                <span>{toast.text}</span>
                {toast?.action && (
                  <button
                    onClick={() => {
                      nav(toast.action.path);
                      setToast("");
                    }}
                  >
                    {toast.action.label}
                    <ArrowRight size={14} />
                  </button>
                )}
              </div>
            </Presence>
          </div>
        </div>
      </div>
    </LectusContext.Provider>
  );
}
createRoot(document.getElementById("root")).render(
  <I18nProvider>
    <App />
  </I18nProvider>,
);


