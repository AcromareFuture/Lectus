import React, { useState } from 'react';
import { Check, ChevronLeft, Globe2 } from 'lucide-react';
import { useI18n } from './i18n';
import { CookieSettings } from './CookiePreferences';
import { webHref } from './web-route';

export default function WebSettings() {
  const { preference, language, setPreference, options, t, persisted } = useI18n();
  const [changed, setChanged] = useState(false);
  const current = options.find(option => option.id === language);
  return <section className="web-settings route-view" data-i18n-skip>
    <a className="back-link" href={webHref('account')}><ChevronLeft size={16} />{t('返回个人页')}</a>
    <h1>{t('个人设置')}</h1>
    <div className="settings-layout">
      <aside className="settings-nav"><a href={webHref('settings')} aria-current="page"><Globe2 size={18} />{t('语言与地区')}</a></aside>
      <section className="settings-content" id="language-settings" aria-labelledby="language-title">
        <h2 id="language-title">{t('界面语言')}</h2>
        <p className="settings-description">{t('选择你习惯的语言，切换后立即生效。')}</p>
        <fieldset className="language-choices"><legend className="settings-sr-only">{t('界面语言')}</legend>
          {options.map(option => <label className="language-choice" key={option.id}>
            <input type="radio" name="interface-language" value={option.id} checked={preference === option.id} onChange={() => { setPreference(option.id); setChanged(true); }} />
            <span className="language-choice-copy"><strong lang={option.id === 'system' ? undefined : option.id === 'zh' ? 'zh-CN' : option.id}>{option.id === 'system' ? t('跟随系统') : option.native}</strong>
              <small>{option.id === 'system' ? t('使用浏览器的首选语言') : option[language === 'ja' ? 'japanese' : language === 'en' ? 'english' : 'native'] === option.native ? null : option[language === 'ja' ? 'japanese' : language === 'en' ? 'english' : 'native']}</small></span>
            {preference === option.id && <Check size={20} aria-hidden="true" />}
          </label>)}
        </fieldset>
        <p className="settings-current">{t('当前语言')}：<span lang={language}>{current?.native}</span></p>
        <div className="settings-notes"><p>{t('价格显示为人民币（CNY），语言切换不会改变价格。')}</p><p>{t('商品名称和个人资料保留原文。')}</p></div>
        <p className="settings-feedback" role="status">{changed ? t(persisted ? '语言偏好已保存' : '语言已切换；允许偏好存储后可记住选择。') : ''}</p>
        <CookieSettings />
      </section>
    </div>
  </section>;
}
