import React, { useEffect, useState } from 'react';
import { ArrowRight, ChevronDown, Heart, LogOut, MapPin, Package, Settings, ShieldCheck, UserRound } from 'lucide-react';
import { memberRequest, saveSession } from './member-api';
import { useI18n } from './i18n';
import { webHref } from './web-route';
import { accountGroups, accountItems, accountTab } from './account-navigation';
import MemberAvatar from './MemberAvatar';
import AccountOrderPanel from './AccountOrderPanel';
import { AccountSection } from './AccountSections';

const icons = { MapPin, Package, ShieldCheck, UserRound };
export default function AccountCenter({ route, member, savedCount, savedContent }) {
  const { t } = useI18n();
  const active = accountTab(route.params.get('tab'));
  const [navOpen, setNavOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const [error, setError] = useState('');
  const profile = member.profile;
  const name = profile?.nickname || profile?.username;
  const current = accountItems.find(([id]) => id === active)[1];
  const signIn = webHref('login', { next: webHref('account', { tab: active }) });
  useEffect(() => { setNavOpen(false); setError(''); }, [active]);
  async function logout() {
    setLeaving(true); setError('');
    try { await memberRequest('member/logout', { method: 'POST' }); saveSession(null); }
    catch (e) { setError(e.message); } finally { setLeaving(false); }
  }
  return <section className="account-dashboard account-center route-view" data-i18n-skip>
    <div className="account-page-heading"><div><h1>{t('你的主场')}<span>.</span></h1><p>{t('喜欢、选择，还有属于你的日常。')}</p></div><a href={webHref('discover')}>{t('接着发现好物')}<ArrowRight size={16} /></a></div>
    <div className="account-center-layout">
      <aside className="account-sidebar">
        <div className="account-sidebar-identity"><a href={webHref('account', { tab: 'profile' })} aria-label={t('编辑头像与资料')}><MemberAvatar profile={profile} /></a><div><strong>{name || t('欢迎来到莱择')}</strong><span>{t(member.session ? profile?.phoneVerified ? '手机号已验证' : '已登录' : '登录，收好每一次喜欢。')}</span></div></div>
        <button className="account-mobile-nav" onClick={() => setNavOpen(value => !value)} aria-expanded={navOpen} aria-controls="account-navigation">{t(current)}<ChevronDown size={17} /></button>
        <nav id="account-navigation" className={navOpen ? 'is-open' : ''} aria-label={t('个人中心导航')}>
          {accountGroups.map(group => <div className="account-nav-group" key={group.label}><h2>{t(group.label)}</h2>{group.items.map(([id, label, icon]) => { const Icon = icons[icon]; return <a key={id} href={webHref('account', { tab: id })} aria-current={active === id ? 'page' : undefined}><Icon size={18} strokeWidth={1.6} /><span>{t(label)}</span>{active === id && <span className="account-active-dot" aria-hidden="true" />}</a>; })}{group.label === '我的莱择' && <a href={webHref('saved')}><Heart size={18} strokeWidth={1.6} /><span>{t('心动清单')}</span><b>{savedCount}</b></a>}</div>)}
          <a className="account-settings-entry" href={webHref('settings')}><Settings size={18} strokeWidth={1.6} />{t('语言与设置')}<ArrowRight size={14} /></a>
          {member.session && <div className="account-sidebar-logout"><button onClick={logout} disabled={leaving}><LogOut size={16} />{t(leaving ? '正在退出…' : '退出登录')}</button>{error && <p className="member-inline-error" role="alert">{t(error)}</p>}</div>}
        </nav>
      </aside>
      <div className="account-main" key={`${active}-${member.session?.token || 'guest'}`}>
        {!member.session ? <><div className="account-guest-intro"><h2>{t('把喜欢，留在你的主场。')}</h2><p>{t('登录后管理订单与资料，下次回来，接着喜欢。')}</p><a className="button primary" href={signIn}>{t('登录 / 注册')}<ArrowRight size={16} /></a></div><SavedShelf savedCount={savedCount} savedContent={savedContent} /></> : member.loading && !profile ? <p className="member-muted" role="status">{t('正在读取个人资料…')}</p> : !profile ? <><p className="member-inline-error" role="alert">{t(member.error || '账户暂时无法打开')}</p><button className="button secondary" onClick={member.refresh}>{t('重新加载')}</button></> : active === 'overview' ? <><AccountOrderPanel member={member} compact /><SavedShelf savedCount={savedCount} savedContent={savedContent} /></> : active === 'orders' ? <AccountOrderPanel member={member} /> : <AccountSection type={active} member={member} />}
      </div>
    </div>
  </section>;
}
function SavedShelf({ savedCount, savedContent }) {
  const { t } = useI18n();
  return <section className="account-saved-shelf"><div className="account-section-heading"><div><h2>{t('留给喜欢的位置')}</h2><p>{t('你的心动清单，随时回来看看。')}</p></div><a href={webHref('saved')}>{t('查看全部')}<ArrowRight size={15} /></a></div>{savedCount ? savedContent : <div className="account-saved-empty"><Heart size={24} strokeWidth={1.4} /><div><h3>{t('下一件喜欢，等你发现')}</h3><p>{t('遇到喜欢的好物，点一下爱心就能收藏。')}</p></div><a className="text-link" href={webHref('discover')}>{t('去逛好物')}<ArrowRight size={16} /></a></div>}</section>;
}
