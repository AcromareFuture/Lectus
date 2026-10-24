import { serviceMessage } from '../../../packages/business-records';
import { ref } from 'vue';
export const workspaceSession = ref(JSON.parse(sessionStorage.getItem('lectus-platform-staff') || 'null'));
export const workspaceError = ref('');
export async function workspaceRequest(path: string, body?: unknown, method = 'POST'): Promise<any> {
 const response = await fetch('/api/mall/workspace/' + path, { method: body === undefined ? 'GET' : method, headers: { 'Content-Type': 'application/json', ...(workspaceSession.value ? { Authorization: 'Bearer ' + workspaceSession.value.token } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
 const result = await response.json().catch(() => null);
 if (response.status === 401) { workspaceSession.value = null; sessionStorage.removeItem('lectus-platform-staff'); }
 if (!response.ok || !result?.success) throw new Error(serviceMessage(result?.message) || '服务暂时不可用，请稍后重试。');
 return result.data;
}
export async function workspaceLogin(username: string, password: string) {
 const value = await workspaceRequest('login', { username, password });
 if(value.role !== 'PLATFORM') { await workspaceRequest('logout', {}, 'POST').catch(() => {}); throw new Error('请使用平台账号登录'); }
 workspaceSession.value=value;sessionStorage.setItem('lectus-platform-staff',JSON.stringify(value));
}
export async function workspaceLogout(){ await workspaceRequest('logout',{}).catch(()=>{});workspaceSession.value=null;sessionStorage.removeItem('lectus-platform-staff'); }

