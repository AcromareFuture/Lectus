import React, { useEffect, useState } from 'react';
import { ArrowRight, Check, ChevronLeft, Heart, LogOut, RefreshCw, UserRound } from 'lucide-react';
import { memberRequest, saveSession, storedSession } from './member-api';
import { webHref } from './web-route';
import { useI18n } from './i18n';
import './member-pages.css';

function Alert({ children }) { return children ? <p className="member-alert" role="alert">{children}</p> : null; }
export function useMember() {
  const [session, setSession] = useState(storedSession);
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(Boolean(session));
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const update = () => setSession(storedSession());
    window.addEventListener('lectus-session', update); window.addEventListener('storage', update);
    return () => { window.removeEventListener('lectus-session', update); window.removeEventListener('storage', update); };
  }, []);
  useEffect(() => {
    if (!session) { setProfile(null); setLoading(false); setError(''); return; }
    const controller = new AbortController(); setLoading(true); setError('');
    memberRequest('member/me', { signal: controller.signal }).then(setProfile).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [session?.token, attempt]);
  return { session, profile, error, loading, refresh: () => setAttempt(n => n + 1), setProfile };
}

export function LoginPage({ route, go, member }) {
  const [mode, setMode] = useState('sms');
  const [action, setAction] = useState(route.params.get('action') === 'reset' ? 'reset' : 'login');
  const [phone, setPhone] = useState(''); const [code, setCode] = useState('');
  const [username, setUsername] = useState(''); const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [challenge, setChallenge] = useState(null); const [retryAt, setRetryAt] = useState(0); const [now, setNow] = useState(Date.now());
  const [status, setStatus] = useState(null); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false); const [sending, setSending] = useState(false);
  const destination = route.params.get('next');
  const next = destination?.startsWith('#/') && !destination.startsWith('#/login') && !destination.startsWith('#/scan') ? destination : webHref('account');
  useEffect(() => { const c = new AbortController(); memberRequest('member/sms/status', { anonymous: true, signal: c.signal }).then(setStatus).catch(e => { if (e.name !== 'AbortError') setError(e.message); }); return () => c.abort(); }, []);
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => { if (member.session && action !== 'reset') go(next); }, [member.session?.token, action, next]);
  function switchFlow(nextMode, nextAction) { setMode(nextMode); setAction(nextAction); setChallenge(null); setCode(''); setError(''); setNotice(''); }
  async function send() {
    setError(''); setNotice('');
    if (!/^1[3-9]\d{9}$/.test(phone)) { setError('请输入有效的 11 位中国大陆手机号。'); return; }
    setSending(true);
    try { const result = await memberRequest('member/sms/send', { method: 'POST', anonymous: true, body: { phone, purpose: action === 'register' ? 'REGISTER' : action === 'reset' ? 'RESET' : 'LOGIN' } }); setChallenge({ ...result, phone, action }); setRetryAt(Date.now() + result.retryAfterSeconds * 1000); setNotice('验证码已发送，5 分钟内有效。'); }
    catch (e) { setError(e.message); } finally { setSending(false); }
  }
  async function submit(event) {
    event.preventDefault(); setError(''); setNotice(''); setBusy(true);
    try {
      let result;
      if (mode === 'password') result = await memberRequest(`member/${action === 'register' ? 'register' : 'login'}`, { method: 'POST', anonymous: true, body: { username, password, ...(action === 'register' ? { nickname: nickname || username } : {}) } });
      else {
        if (!challenge || challenge.phone !== phone || challenge.action !== action) throw new Error('请先为当前手机号获取验证码。');
        const body = { phone, code, challengeId: challenge.challengeId, nickname: action === 'register' ? (nickname || `莱择会员${phone.slice(-4)}`) : undefined };
        if (action === 'reset') {
          await memberRequest('member/phone/password/reset', { method: 'POST', anonymous: true, body: { ...body, newPassword: password } });
          saveSession(null); setAction('login'); setChallenge(null); setCode(''); setNotice('密码已设置。可使用短信登录，或使用个人页中的账号和新密码登录。'); return;
        }
        result = await memberRequest(`member/phone/${action}`, { method: 'POST', anonymous: true, body });
      }
      saveSession(result); go(next);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  const seconds = Math.max(0, Math.ceil((retryAt - now) / 1000));
  return <section className="auth-page route-view"><div className="auth-intro"><a className="back-link" href={webHref('discover')}><ChevronLeft size={16} />继续逛好物</a><h1>喜欢的日常，<br />从这里接着。</h1><p>登录莱择，收好你的心动，<br />也留住每一次选择。</p><a className="text-link" href={webHref('saved')}>看看心动清单<ArrowRight size={16} /></a></div><div className="auth-form-wrap"><h2>{action === 'reset' ? '设置 / 重置密码' : action === 'register' ? '注册莱择账号' : '欢迎回来'}</h2><div className="member-tabs" role="group" aria-label="登录方式"><button aria-pressed={mode === 'sms'} onClick={() => switchFlow('sms', 'login')}>手机验证码</button><button aria-pressed={mode === 'password'} onClick={() => switchFlow('password', 'login')}>账号密码</button></div><form className="member-form" onSubmit={submit}>
    {mode === 'sms' ? <><label>手机号<input name="phone" type="tel" inputMode="numeric" autoComplete="tel-national" placeholder="请输入 11 位手机号" pattern="1[3-9][0-9]{9}" maxLength={11} required value={phone} disabled={sending || busy} onChange={e => { setPhone(e.target.value); setChallenge(null); setCode(''); }} /></label><label>短信验证码<div className="code-input"><input name="code" autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} placeholder="6 位验证码" required value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))} /><button type="button" onClick={send} disabled={sending || busy || seconds > 0 || !status?.available}>{sending ? '发送中…' : seconds > 0 ? `${seconds} 秒后重发` : '获取验证码'}</button></div></label>{status?.available === false && <p className="form-hint">短信登录暂不可用，请使用账号密码登录。</p>}</> : <><label>账号<input name="username" autoComplete="username" placeholder={action === 'register' ? '3–50 位字母、数字或下划线' : '输入登录账号'} maxLength={50} pattern={action === 'register' ? '[A-Za-z0-9_]{3,50}' : undefined} required value={username} onChange={e => setUsername(e.target.value)} /></label><label>密码<input name="password" type="password" autoComplete={action === 'register' ? 'new-password' : 'current-password'} minLength={action === 'register' ? 8 : undefined} maxLength={64} placeholder={action === 'register' ? '至少 8 位密码' : '输入登录密码'} required value={password} onChange={e => setPassword(e.target.value)} /></label></>}
    {action === 'register' && <label>昵称<span className="optional-label">选填</span><input name="nickname" autoComplete="nickname" placeholder="怎么称呼你" maxLength={30} value={nickname} onChange={e => setNickname(e.target.value)} /></label>}
    {action === 'reset' && <label>新密码<input type="password" name="newPassword" autoComplete="new-password" minLength={8} maxLength={64} required value={password} onChange={e => setPassword(e.target.value)} placeholder="至少 8 位密码" /></label>}
    <Alert>{error}</Alert>{notice && <p className="form-notice" role="status">{notice}</p>}<button className="button primary auth-submit" disabled={busy || sending || (mode === 'sms' && !status?.available)}>{busy ? '正在处理…' : action === 'reset' ? '保存新密码' : action === 'register' ? '注册并登录' : '登录'}<ArrowRight size={17} /></button>
    <div className="auth-actions"><button type="button" onClick={() => switchFlow(mode, action === 'register' ? 'login' : 'register')}>{action === 'register' ? '已有账号？去登录' : '还没有账号？去注册'}</button>{action !== 'reset' && <button type="button" onClick={() => switchFlow('sms', 'reset')}>设置 / 找回密码</button>}</div><p className="form-hint">{mode === 'sms' ? '验证码 5 分钟内有效，请勿向他人透露。' : '请妥善保管密码，勿与他人共享账号。'}</p>
  </form></div></section>;
}

function SignInPrompt({ next }) { return <div className="empty-state"><UserRound size={32} /><h1>登录，回到你的主场</h1><p>使用手机验证码或已有账号密码登录。</p><a className="button primary" href={webHref('login', { next })}>去登录<ArrowRight size={17} /></a></div>; }
export { default as AccountPage } from './AccountCenter';

