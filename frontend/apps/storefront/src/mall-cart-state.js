import { useEffect, useState } from 'react';
import { memberRequest } from './member-api';

export const CART_EVENT = 'lectus-mall-cart';
export function cartChanged(items) { window.dispatchEvent(new CustomEvent(CART_EVENT, { detail: items })); }
export function useCartCount(token) {
 const [count, setCount] = useState(0);
 useEffect(() => {
  setCount(0); if (!token) return;
  const controller = new AbortController(); let revision = 0;
  const update = e => { revision++; setCount(e.detail.reduce((n, item) => n + item.quantity, 0)); };
  const initial = revision;
  memberRequest('mall/cart', { signal: controller.signal }).then(items => { if (revision === initial) setCount(items.reduce((n, item) => n + item.quantity, 0)); }).catch(() => {});
  window.addEventListener(CART_EVENT, update);
  return () => { controller.abort(); window.removeEventListener(CART_EVENT, update); };
 }, [token]);
 return count;
}
