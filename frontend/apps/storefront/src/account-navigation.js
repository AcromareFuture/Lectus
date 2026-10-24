export const accountGroups = [
  { label: '我的莱择', items: [['overview', '我的主页', 'UserRound'], ['orders', '我的订单', 'Package']] },
  { label: '账户管理', items: [['profile', '个人资料', 'UserRound'], ['addresses', '收货地址', 'MapPin'], ['security', '账号安全', 'ShieldCheck']] },
];
export const accountItems = accountGroups.flatMap(group => group.items);
export function accountTab(value) { return accountItems.some(([id]) => id === value) ? value : 'overview'; }
