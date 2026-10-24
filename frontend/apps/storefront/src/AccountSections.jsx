import React, { useEffect, useState } from 'react';
import { ArrowRight, Inbox, MapPin, ShieldCheck } from 'lucide-react';
import AvatarEditor from './AvatarEditor';
import { memberRequest } from './member-api';
import { useI18n } from './i18n';
import { webHref } from './web-route';
import { accountItems } from './account-navigation';
import AddressFields from './AddressFields';
import { validRegion } from './address-regions';

export function useAccountResource(path) {
  const [result, setResult] = useState({ path, data: null, error: '', loading: true });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    setResult({ path, data: null, error: '', loading: true });
    memberRequest(path, { signal: controller.signal }).then(data => setResult({ path, data, error: '', loading: false })).catch(error => { if (!controller.signal.aborted) setResult({ path, data: null, error: error.message, loading: false }); });
    return () => controller.abort();
  }, [path, attempt]);
  return { ...(result.path === path ? result : { data: null, error: '', loading: true }), reload: () => setAttempt(value => value + 1) };
}
export function EmptyState({ title = '这里还空着', copy = '新的记录，会在这里与你见面。', children }) {
  const { t } = useI18n();
  return <div className="member-orders-empty account-empty"><Inbox size={32} strokeWidth={1.3} /><h3>{t(title)}</h3><p>{t(copy)}</p>{children}</div>;
}
function ResourceState({ resource, children }) {
  const { t } = useI18n();
  return resource.loading ? <p className="account-loading" role="status">{t('正在加载…')}</p> : resource.error ? <div className="account-load-error"><p className="member-inline-error" role="alert">{t(resource.error)}</p><button className="button secondary" onClick={resource.reload}>{t('重新加载')}</button></div> : children;
}
function Feedback({ error, notice }) { const { t } = useI18n(); return <>{error && <p className="member-inline-error" role="alert">{t(error)}</p>}{notice && <p className="member-inline-success" role="status">{t(notice)}</p>}</>; }
export function AccountSection({ type, member }) {
  const { t } = useI18n();
  return <><div className="account-panel-heading"><h2>{t(accountItems.find(([id]) => id === type)?.[1])}</h2></div>
    {type === 'profile' ? <Profile member={member} /> : type === 'security' ? <Security member={member} /> : <Addresses member={member} />}
  </>;
}

function Profile({ member }) {
  const { t } = useI18n();
  const profile = member.profile;
  const [form, setForm] = useState({ nickname: profile.nickname || '', email: profile.email || '', sex: profile.sex || '0', birthday: profile.birthday || '' });
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const set = key => event => { setForm(value => ({ ...value, [key]: event.target.value })); setNotice(''); };
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try { member.setProfile(await memberRequest('member/profile', { method: 'PUT', body: { ...form, nickname: form.nickname.trim(), birthday: form.birthday || undefined } })); setNotice('个人资料已保存。'); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <><AvatarEditor member={member} /><dl className="account-profile-summary"><div><dt>{t('登录账号')}</dt><dd>{profile.username}</dd></div><div><dt>{t('手机号')}</dt><dd>{profile.phone ? `${profile.phone.slice(0, 3)} **** ${profile.phone.slice(-4)}` : t('未绑定手机号')}</dd></div></dl><form className="account-form" onSubmit={save}>
    <label>{t('昵称')}<input autoComplete="nickname" required maxLength={30} value={form.nickname} onChange={set('nickname')} /></label>
    <label>{t('邮箱')}<input type="email" autoComplete="email" maxLength={80} value={form.email} onChange={set('email')} /></label>
    <label>{t('性别')}<select value={form.sex} onChange={set('sex')}><option value="0">{t('不透露')}</option><option value="1">{t('男')}</option><option value="2">{t('女')}</option></select></label>
    <label>{t('生日')}<input type="date" autoComplete="bday" max={new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })} value={form.birthday} onChange={set('birthday')} /></label>
    <div className="account-form-actions"><button className="button primary" disabled={busy}>{t(busy ? '正在保存…' : '保存个人资料')}</button></div><Feedback error={error} notice={notice} />
  </form></>;
}
function Security({ member }) {
  const { t } = useI18n();
  return <div className="account-security"><ShieldCheck size={36} strokeWidth={1.3} /><h3>{t(member.profile.phoneVerified ? '手机号已验证' : '管理你的登录方式')}</h3><p>{t('使用手机验证码找回或更新密码，让账户更安心。')}</p><dl className="account-profile-summary"><div><dt>{t('登录账号')}</dt><dd>{member.profile.username}</dd></div><div><dt>{t('手机号')}</dt><dd>{member.profile.phone ? `${member.profile.phone.slice(0, 3)} **** ${member.profile.phone.slice(-4)}` : t('未绑定手机号')}</dd></div></dl><a className="button secondary" href={webHref('login', { action: 'reset', next: webHref('account', { tab: 'security' }) })}>{t('通过手机验证码重设密码')}<ArrowRight size={16} /></a></div>;
}
const blankAddress = { contactName: '', phone: '', province: '', city: '', district: '', detail: '', isDefault: 0 };
function Addresses({ member }) {
  const { t } = useI18n();
  const resource = useAccountResource('addresses');
  const [form, setForm] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState(''), [deleting, setDeleting] = useState(null);
  async function run(path, method, body, success) {
    setBusy(true); setError(''); setNotice('');
    try { await memberRequest(path, { method, body }); resource.reload(); member.refresh(); setNotice(success); setForm(null); setDeleting(null); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <><Feedback error={error} notice={notice} /><ResourceState resource={resource}>{!resource.data?.length ? <EmptyState title="还没有收货地址" copy="添加常用地址，让好物顺利抵达。" /> : <div className="account-records">{resource.data.map(address => <article className="account-address" key={address.addressId}><div><h3>{address.contactName}<span>{address.phone}</span>{address.isDefault === 1 && <small>{t('默认地址')}</small>}</h3><p>{address.province} {address.city} {address.district} {address.detail}</p></div><div className="account-row-actions"><button disabled={busy} onClick={() => setForm({ ...address })}>{t('编辑')}</button>{address.isDefault !== 1 && <button disabled={busy} onClick={() => run(`addresses/${address.addressId}/default`, 'PUT', undefined, '默认地址已更新。')}>{t('设为默认')}</button>}<button disabled={busy} onClick={() => setDeleting(address.addressId)}>{t('删除')}</button></div>{deleting === address.addressId && <div className="account-delete-confirm"><span>{t('确定删除这个地址？')}</span><button disabled={busy} onClick={() => run(`addresses/${address.addressId}`, 'DELETE', undefined, '地址已删除。')}>{t('确认删除')}</button><button onClick={() => setDeleting(null)}>{t('取消')}</button></div>}</article>)}</div>}</ResourceState>
    {!form ? <button className="button secondary" onClick={() => { setForm({ ...blankAddress }); setError(''); setNotice(''); }}>{t('添加收货地址')}<MapPin size={17} /></button> : <form className="account-form account-address-form" onSubmit={event => { event.preventDefault(); if (!validRegion(form) || !form.detail.trim()) { setError('请选择完整的省市区，并填写详细地址。'); return; } run(form.addressId ? `addresses/${form.addressId}` : 'addresses', form.addressId ? 'PUT' : 'POST', form, '收货地址已保存。'); }}>
      <h3>{t(form.addressId ? '编辑收货地址' : '添加收货地址')}</h3>{[['contactName', '收货人'], ['phone', '手机号']].map(([key, label]) => <label key={key}>{t(label)}<input required maxLength={key === 'detail' ? 200 : 50} type={key === 'phone' ? 'tel' : 'text'} pattern={key === 'phone' ? '1[3-9][0-9]{9}' : undefined} disabled={busy} value={form[key]} onChange={event => setForm(value => ({ ...value, [key]: event.target.value }))} /></label>)}<AddressFields value={form} onChange={setForm} disabled={busy} maxLength={200} /><label className="account-checkbox"><input type="checkbox" checked={form.isDefault === 1} onChange={event => setForm(value => ({ ...value, isDefault: event.target.checked ? 1 : 0 }))} />{t('设为默认地址')}</label><div className="account-form-actions"><button className="button primary" disabled={busy}>{t(busy ? '正在保存…' : '保存地址')}</button><button className="button secondary" type="button" disabled={busy} onClick={() => setForm(null)}>{t('取消')}</button></div>
    </form>}
  </>;
}
