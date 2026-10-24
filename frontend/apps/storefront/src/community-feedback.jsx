import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Check, CircleAlert, X } from 'lucide-react';

export function useCommunityFeedback() {
  const [notice, setNotice] = useState(null), timer = useRef(null);
  const notify = useCallback((text, error = false) => { clearTimeout(timer.current); setNotice({ text, error }); timer.current = setTimeout(() => setNotice(null), error ? 6000 : 3000); }, []);
  useEffect(() => () => clearTimeout(timer.current), []);
  return { notify, feedback: <div className="community-toast-region" aria-live="polite" aria-atomic="true">{notice && <div className={`community-toast ${notice.error ? 'has-error' : ''}`}>{notice.error ? <CircleAlert size={18} /> : <Check size={18} />}<span>{notice.text}</span><button type="button" aria-label="关闭提示" onClick={() => { clearTimeout(timer.current); setNotice(null); }}><X size={17} /></button></div>}</div> };
}
