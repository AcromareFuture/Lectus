import React, { useState } from 'react';
import { UserRound } from 'lucide-react';
import { avatarSrc } from './avatar-crop';

export default function MemberAvatar({ profile, className = '' }) {
  const src = avatarSrc(profile?.avatarUrl, import.meta.env.BASE_URL);
  const [failed, setFailed] = useState(null);
  const name = profile?.nickname || profile?.username;
  return <span className={`member-avatar ${className}`} aria-hidden="true" data-i18n-skip>
    {src && failed !== src ? <img src={src} alt="" onError={() => setFailed(src)} /> : name ? Array.from(name)[0].toUpperCase() : <UserRound size={22} strokeWidth={1.5} />}
  </span>;
}
