import React, { useEffect, useRef, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { useI18n } from './i18n';
import { CONSENT_EVENT, readConsent, saveConsent } from './cookie-consent';
import './member-preferences.css';

const OPEN_EVENT = 'lectus-open-cookies';
export function openCookiePreferences() { window.dispatchEvent(new Event(OPEN_EVENT)); }
export function CookieSettings() {
  const { t } = useI18n();
  const [consent, setConsent] = useState(readConsent);
  useEffect(() => { const update = () => setConsent(readConsent()); window.addEventListener(CONSENT_EVENT, update); return () => window.removeEventListener(CONSENT_EVENT, update); }, []);
  return <section className="cookie-settings" aria-labelledby="cookie-settings-title"><h2 id="cookie-settings-title">{t('Cookie 与隐私')}</h2><p>{t('你可以决定是否在此浏览器记住语言与心动清单。')}</p><p className="cookie-current">{t(!consent ? '尚未设置偏好' : consent.preferences ? '已允许偏好存储' : '仅使用必要存储')}</p><button className="button secondary" onClick={openCookiePreferences}>{t('管理 Cookie 偏好')}<ShieldCheck size={17} /></button></section>;
}
export default function CookieNotice() {
  const { t } = useI18n();
  const [open, setOpen] = useState(() => !readConsent()), [details, setDetails] = useState(false), [optional, setOptional] = useState(() => Boolean(readConsent()?.preferences)), [error, setError] = useState('');
  const heading = useRef(null), returnFocus = useRef(null), manuallyOpened = useRef(false);
  useEffect(() => {
    const show = () => { returnFocus.current = document.activeElement; manuallyOpened.current = true; setOptional(Boolean(readConsent()?.preferences)); setDetails(true); setError(''); setOpen(true); };
    const sync = () => { if (!manuallyOpened.current) setOpen(!readConsent()); };
    window.addEventListener(OPEN_EVENT, show); window.addEventListener(CONSENT_EVENT, sync);
    return () => { window.removeEventListener(OPEN_EVENT, show); window.removeEventListener(CONSENT_EVENT, sync); };
  }, []);
  useEffect(() => { if (open && manuallyOpened.current) heading.current?.focus(); }, [open, details]);
  function close() { setOpen(false); manuallyOpened.current = false; returnFocus.current?.focus(); }
  function save(value) { if (!saveConsent(value)) { setError('浏览器未允许保存 Cookie，请检查浏览器设置。'); return; } close(); }
  if (!open) return null;
  return <aside className="cookie-notice" data-i18n-skip aria-labelledby="cookie-notice-title" role="region">
    <div className="cookie-notice-copy"><h2 ref={heading} tabIndex={-1} id="cookie-notice-title">{t('让选择，由你决定。')}</h2><p>{t('莱择使用必要的 Cookie 和本地存储来维持登录及保存隐私选择。经你同意，我们也会记住语言与心动清单。')}</p><p className="cookie-scope">{t('仅管理莱择本站的存储，不读取其他网站的 Cookie。')}</p></div>
    {details && <fieldset className="cookie-options"><legend>{t('存储偏好')}</legend><label><input type="checkbox" checked disabled /><span><strong>{t('必要存储')}</strong><small>{t('用于登录与安全，以及保存你的隐私选择，始终启用。')}</small></span></label><label><input type="checkbox" checked={optional} onChange={event => setOptional(event.target.checked)} /><span><strong>{t('偏好存储')}</strong><small>{t('在此浏览器记住语言与心动清单。关闭后清除已存偏好，当前页面仍可使用。')}</small></span></label><p>{t('隐私选择保存 180 天。当前未使用广告或访问统计 Cookie。')}</p></fieldset>}
    <div className="cookie-actions"><button className="button secondary" onClick={() => save(false)}>{t('仅使用必要项')}</button>{details ? <button className="button secondary" onClick={() => save(optional)}>{t('保存我的选择')}</button> : <button className="button secondary" onClick={() => setDetails(true)}>{t('自定义设置')}</button>}<button className="button primary" onClick={() => save(true)}>{t('允许偏好存储')}</button>{manuallyOpened.current && <button className="text-link" onClick={close}>{t('取消')}</button>}</div>
    {error && <p className="member-inline-error" role="alert">{t(error)}</p>}
  </aside>;
}
