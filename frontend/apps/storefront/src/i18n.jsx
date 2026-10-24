import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import localeRows from "./locales.tsv?raw";
import webLocaleRows from "./web-locales.tsv?raw";
import { CONSENT_EVENT, preferenceValue, savePreference } from './cookie-consent';
import accountLocaleRows from "./account-locales.tsv?raw";

const localeCatalog = Object.fromEntries(
  ["en", "ja"].map((locale, column) => [
    locale,
    Object.fromEntries(
      `${localeRows}\n${webLocaleRows}\n${accountLocaleRows}`
        .split(/\r?\n/)
        .filter((row) => row && !row.startsWith("#"))
        .map((row) => row.split("\t"))
        .filter((row) => row.length >= 3)
        .map(([source, english, japanese]) => [source, column === 0 ? english : japanese]),
    ),
  ]),
);

export const languageOptions = [
  { id: "system", native: "跟随系统", english: "System default", japanese: "システム設定" },
  { id: "zh", native: "简体中文", english: "Simplified Chinese", japanese: "簡体字中国語" },
  { id: "en", native: "English", english: "English", japanese: "英語" },
  { id: "ja", native: "日本語", english: "Japanese", japanese: "日本語" },
];

const translations = {
  en: {
    "这里还空着": "Nothing here yet", "去发现一些新的喜欢吧。": "Find something to love.",
    "减少数量": "Decrease quantity", "增加数量": "Increase quantity", "参考价格": "Reference price", "样例": "Demo",
    "全部": "All", "鞋履": "Shoes", "服饰": "Apparel", "数码": "Tech", "配饰": "Accessories", "香水": "Fragrance",
    "精选好物": "Selected items", "综合": "Recommended", "筛选排序": "Filter & sort", "筛选你的热爱": "Filter your edit",
    "人气优先": "Most popular", "价格升序": "Price: low to high", "价格降序": "Price: high to low", "商品分类": "Categories",
    "价格区间": "Price range", "人民币 / CNY": "Chinese yuan / CNY", "最低价": "Min. price", "最高价": "Max. price",
    "重置": "Reset", "应用筛选": "Apply filters", "找到你的下一件热爱": "Find your next favorite", "搜索": "Search",
    "本周风格精选": "This week's style edit", "探索本期": "Explore the edit", "鉴别服务": "Authentication", "闲置转售": "Resell", "新人好礼": "Welcome offers",
    "值得一择": "Worth choosing", "发现更多": "Discover more", "搜索商品": "Search products", "最近搜索": "Recent searches", "清除搜索记录": "Clear search history", "灵感热词": "Trending searches",
    "选择规格": "Choose options", "尺码 / 颜色": "Size / Color", "优惠": "Offers", "领取新人优惠": "Get welcome offers", "配送与服务": "Delivery & services", "查看说明": "Learn more",
    "关于这件好物": "About this item", "系列品牌": "Brand", "所属分类": "Category", "商品编号": "Product ID", "规格与库存": "Options & stock", "搭配灵感": "Style inspiration", "去社区": "Visit community",
    "咨询客服": "Contact support", "客服": "Support", "购物袋": "Shopping bag", "打开购物袋": "Open shopping bag", "加入购物袋": "Add to bag", "立即购买": "Buy now",
    "取消全选": "Deselect all", "全选": "Select all", "合计（不含运费）": "Subtotal", "去结算 (": "Checkout (", "还没把喜欢装进来": "Your bag is empty", "去逛逛": "Start exploring",
    "添加收货地址": "Add delivery address", "订单明细": "Order summary", "商品金额": "Items subtotal", "运费": "Delivery", "优惠券": "Coupons", "应付合计": "Total due", "应付": "Total", "提交演示订单": "Place demo order",
    "暂时没有结算商品": "No items to check out", "去购物袋": "Go to bag", "待付款": "Awaiting payment", "待发货": "Processing", "待收货": "Shipped", "已完成": "Completed", "售后中": "Return in progress", "已退款": "Refunded",
    "订单商品": "Order item", "实付": "Paid", "查看详情": "View details", "还没有这类订单": "No orders here yet", "去发现好物": "Discover products", "未找到订单": "Order not found", "查看全部订单": "View all orders", "订单编号": "Order number", "收货信息": "Delivery details", "优惠金额": "Discount", "实付金额": "Amount paid", "查看物流轨迹": "Track delivery", "取消订单": "Cancel order", "申请售后": "Request a return",
    "关注": "Follow", "已关注": "Following", "灵感现场": "Inspiration, live", "发现": "Discover", "发布灵感": "Create a post", "周末去野": "Weekend outdoors", "今日穿搭": "Daily outfits", "球鞋日记": "Sneaker journal", "露营生活": "Camping life", "精选灵感": "Editor's pick", "去发现": "Explore", "分享": "Share",
    "个人设置": "Settings", "收藏": "Saved", "足迹": "Recently viewed", "我的订单": "My orders", "全部订单": "All orders", "退换 / 售后": "Returns & support", "收货地址": "Delivery addresses", "鉴别记录": "Authentication records", "优惠券中心": "Coupon center", "客服与帮助": "Support & help",
    "昵称": "Display name", "个人签名": "Bio", "保存个人资料": "Save profile", "关于 Lectus｜莱择": "About Lectus", "选择你的生活主场。": "Choose your place in life.", "重置本机演示数据": "Reset local demo data", "确认重置（仅本机）": "Reset on this device", "清空当前浏览器内的样例订单、购物袋和个人设置，回到初始状态。": "Reset sample orders, your bag and profile on this device.", "当前为本地访客身份，未接入真实登录、实名认证和隐私协议服务。": "You are browsing locally. Sign-in, identity verification and privacy services are not connected.", "语言与地区": "Language & region", "界面语言": "App language", "跟随系统": "System default", "简体中文": "Simplified Chinese", "语言偏好已保存": "Language preference saved", "价格显示为人民币（CNY），语言切换不会改变价格。": "Prices are shown in Chinese yuan (CNY); changing language does not change prices.",
    "当前语言": "Current language", "精选": "Home", "社区": "Community", "鉴别": "Authenticate", "消息": "Messages", "我的": "Me", "返回": "Back", "好物详情": "Product", "回到首页": "Home", "关闭弹层": "Close panel", "数量": "Quantity", "确认，进入结算": "Continue to checkout",
    ...localeCatalog.en,
  },
  ja: {
    "这里还空着": "まだ何もありません", "去发现一些新的喜欢吧。": "新しいお気に入りを見つけましょう。", "减少数量": "数量を減らす", "增加数量": "数量を増やす", "参考价格": "参考価格", "样例": "サンプル",
    "全部": "すべて", "鞋履": "シューズ", "服饰": "アパレル", "数码": "デジタル", "配饰": "アクセサリー", "香水": "フレグランス", "精选好物": "厳選アイテム", "综合": "おすすめ順", "筛选排序": "絞り込み・並び替え", "人气优先": "人気順", "价格升序": "価格の安い順", "价格降序": "価格の高い順", "商品分类": "カテゴリー",
    "找到你的下一件热爱": "次のお気に入りを探す", "搜索": "検索", "本周风格精选": "今週のスタイル特集", "探索本期": "特集を見る", "鉴别服务": "真贋鑑定", "闲置转售": "リセール", "新人好礼": "初回特典", "值得一择": "選びたいもの", "发现更多": "もっと見る", "搜索商品": "商品を検索", "最近搜索": "最近の検索", "清除搜索记录": "検索履歴を削除", "灵感热词": "注目のキーワード",
    "选择规格": "サイズ・カラーを選ぶ", "尺码 / 颜色": "サイズ / カラー", "优惠": "特典", "领取新人优惠": "初回特典を受け取る", "配送与服务": "配送・サービス", "查看说明": "説明を見る", "关于这件好物": "商品について", "系列品牌": "ブランド", "所属分类": "カテゴリー", "商品编号": "商品番号", "规格与库存": "サイズ・在庫", "搭配灵感": "コーディネートのヒント", "去社区": "コミュニティへ",
    "咨询客服": "サポートに相談", "客服": "サポート", "购物袋": "ショッピングバッグ", "打开购物袋": "バッグを開く", "加入购物袋": "バッグに追加", "立即购买": "今すぐ購入", "取消全选": "すべて解除", "全选": "すべて選択", "合计（不含运费）": "小計（送料別）", "去结算 (": "レジへ (", "还没把喜欢装进来": "バッグは空です", "去逛逛": "商品を探す", "添加收货地址": "配送先を追加", "订单明细": "注文内容", "商品金额": "商品小計", "运费": "送料", "优惠券": "クーポン", "应付合计": "お支払い合計", "应付": "合計", "提交演示订单": "デモ注文を確定",
    "待付款": "支払い待ち", "待发货": "発送準備中", "待收货": "配送中", "已完成": "完了", "售后中": "返品対応中", "已退款": "返金済み", "订单商品": "注文商品", "实付": "支払額", "查看详情": "詳細を見る", "还没有这类订单": "該当する注文がありません", "去发现好物": "商品を探す", "未找到订单": "注文が見つかりません", "查看全部订单": "すべての注文を見る", "订单编号": "注文番号", "收货信息": "配送先情報", "优惠金额": "割引額", "实付金额": "支払金額", "查看物流轨迹": "配送状況を見る", "取消订单": "注文をキャンセル", "申请售后": "返品を申請",
    "关注": "フォロー", "已关注": "フォロー中", "灵感现场": "インスピレーション", "发现": "見つける", "发布灵感": "投稿する", "周末去野": "週末アウトドア", "今日穿搭": "今日のコーデ", "球鞋日记": "スニーカー日記", "露营生活": "キャンプライフ", "精选灵感": "おすすめの投稿", "去发现": "見つけに行く", "分享": "シェア",
    "个人设置": "設定", "收藏": "お気に入り", "足迹": "最近見た商品", "我的订单": "マイオーダー", "全部订单": "すべての注文", "退换 / 售后": "返品・サポート", "收货地址": "配送先住所", "鉴别记录": "鑑定履歴", "优惠券中心": "クーポンセンター", "客服与帮助": "サポート・ヘルプ", "昵称": "表示名", "个人签名": "プロフィール", "保存个人资料": "プロフィールを保存", "关于 Lectus｜莱择": "Lectusについて", "选择你的生活主场。": "あなたの暮らしの居場所を選ぶ。", "重置本机演示数据": "端末内のデモデータをリセット", "确认重置（仅本机）": "この端末でリセット", "清空当前浏览器内的样例订单、购物袋和个人设置，回到初始状态。": "この端末のデモ注文、バッグ、プロフィールを初期状態に戻します。", "当前为本地访客身份，未接入真实登录、实名认证和隐私协议服务。": "現在は端末内のデモです。ログイン、本人確認、プライバシーサービスには接続していません。", "语言与地区": "言語と地域", "界面语言": "アプリの言語", "跟随系统": "システム設定", "简体中文": "簡体字中国語", "当前语言": "現在の言語", "语言偏好已保存": "言語設定を保存しました", "价格显示为人民币（CNY），语言切换不会改变价格。": "価格は中国人民元（CNY）で表示されます。言語を変えても価格は変わりません。", "精选": "ホーム", "社区": "コミュニティ", "鉴别": "鑑定", "消息": "メッセージ", "我的": "マイページ", "返回": "戻る", "好物详情": "商品", "回到首页": "ホーム", "关闭弹层": "パネルを閉じる", "数量": "数量", "确认，进入结算": "レジへ進む",
    ...localeCatalog.ja,
  },
};

const I18nContext = createContext(null);
const detectLanguage = () => navigator.language?.toLowerCase().startsWith("ja") ? "ja" : navigator.language?.toLowerCase().startsWith("zh") ? "zh" : "en";
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const translate = (value, language) => {
  const dictionary = translations[language];
  if (!dictionary || !value) return value;
  if (Object.hasOwn(dictionary, value)) return dictionary[value];
  for (const [source, target] of Object.entries(dictionary)) {
    if (!source.includes("{")) continue;
    const names = [];
    const pattern = new RegExp(`^${escapeRegExp(source).replace(/\\\{([^}]+)\\\}/g, (_, name) => { names.push(name.trim()); return "(.*?)"; })}$`);
    const match = value.match(pattern);
    if (!match) continue;
    return names.reduce((result, name, index) => result.replace(`{${name}}`, match[index + 1]), target);
  }
  return value;
};

export function I18nProvider({ children }) {
  const validPreference = value => languageOptions.some(option => option.id === value) ? value : 'system';
  const [preference, updatePreference] = useState(() => { try { return validPreference(preferenceValue('lectus-language')); } catch { return 'system'; } });
  const [persisted, setPersisted] = useState(true);
  const [systemLanguage, setSystemLanguage] = useState(detectLanguage);
  const setPreference = value => updatePreference(validPreference(value));
  const language = preference === "system" ? systemLanguage : preference;
  useEffect(() => {
    const detect = () => setSystemLanguage(detectLanguage());
    const sync = event => { if (event.key === 'lectus-language') updatePreference(validPreference(event.newValue)); };
    window.addEventListener('languagechange', detect);
    window.addEventListener('storage', sync);
    return () => { window.removeEventListener('languagechange', detect); window.removeEventListener('storage', sync); };
  }, []);
  useEffect(() => {
    const persist = () => setPersisted(savePreference('lectus-language', preference));
    persist(); window.addEventListener(CONSENT_EVENT, persist);
    return () => window.removeEventListener(CONSENT_EVENT, persist);
  }, [preference]);
  useEffect(() => {
    document.documentElement.lang = language === "ja" ? "ja-JP" : language === "en" ? "en" : "zh-CN";
    document.title = language === "en" ? "Lectus · Choose your place in life" : language === "ja" ? "Lectus · あなたの暮らしを選ぶ" : "Lectus 莱择 · 选择你的生活主场";
    const apply = () => {
      const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      let node;
      while ((node = walker.nextNode())) {
        if (node.parentElement?.closest("script,style,[data-i18n-skip],.route-community,.route-post")) continue;
        const current = node.nodeValue.trim();
        if (!current) continue;
        const previousTranslation = node.__lectusTranslation;
        if (!node.__lectusSource || (previousTranslation && current !== previousTranslation)) {
          node.__lectusSource = current;
        }
        const source = node.__lectusSource;
        const next = translate(source, language);
        if (next !== current) node.nodeValue = node.nodeValue.replace(current, next);
        node.__lectusTranslation = next;
      }
      document.querySelectorAll("[aria-label],[placeholder],[title],[alt]").forEach((el) => {
        if (el.closest("[data-i18n-skip],.route-community,.route-post")) return;
        for (const attr of ["aria-label", "placeholder", "title", "alt"]) {
          const current = el.getAttribute(attr);
          if (!current) continue;
          el.__lectusAttributeSources ||= {};
          el.__lectusAttributeTranslations ||= {};
          const previousTranslation = el.__lectusAttributeTranslations[attr];
          if (!el.__lectusAttributeSources[attr] || (previousTranslation && current !== previousTranslation)) {
            el.__lectusAttributeSources[attr] = current;
          }
          const next = translate(el.__lectusAttributeSources[attr], language);
          if (next !== current) el.setAttribute(attr, next);
          el.__lectusAttributeTranslations[attr] = next;
        }
      });
    };
    apply();
    const observer = new MutationObserver(apply);
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    return () => observer.disconnect();
  }, [preference, language]);
  const value = useMemo(() => ({ preference, language, setPreference, persisted, t: (value, params = {}) => Object.entries(params).reduce((text, [key, replacement]) => text.replaceAll(`{${key}}`, String(replacement)), translate(value, language)), options: languageOptions }), [preference, language, persisted]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}
export const useI18n = () => useContext(I18nContext);
