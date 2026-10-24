export function cropRect(width, height, zoom = 1, x = 0, y = 0) {
  if (!(width > 0 && height > 0)) throw new Error('Invalid image dimensions');
  const side = Math.min(width, height) / Math.min(4, Math.max(1, zoom));
  const clamp = value => Math.max(-1, Math.min(1, value));
  return { sx: (width - side) * (1 - clamp(x)) / 2, sy: (height - side) * (1 - clamp(y)) / 2, side };
}
export function avatarSrc(url, base = '/') {
  return /^\/api\/member\/avatar\/[a-f0-9]{32}\.png$/.test(url || '') ? `${base.replace(/\/$/, '')}${url}` : null;
}
