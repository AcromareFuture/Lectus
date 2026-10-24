<script setup lang="ts">
import { ref } from 'vue';
import { workspaceLogin } from '../workspace';
const emit=defineEmits<{ login: [] }>();
const username=ref('');const password=ref('');const busy=ref(false);const error=ref('');
async function submit(){busy.value=true;error.value='';try{await workspaceLogin(username.value,password.value);emit('login');}catch(e){error.value=(e as Error).message;}finally{busy.value=false;}}
</script>
<template><main class="workspace-login"><form class="panel" @submit.prevent="submit"><span class="eyebrow">LECTUS / WORKSPACE</span><h1>商家工作台登录</h1><p>登录后管理当前店铺的商品和订单。</p><label class="field-label">账号<input v-model="username" autocomplete="username" required maxlength="60" /></label><label class="field-label">密码<input v-model="password" type="password" autocomplete="current-password" required maxlength="64" /></label><p v-if="error" class="form-error" role="alert">{{ error }}</p><button class="primary-button" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button></form></main></template>
<style>.workspace-login{min-height:100vh;display:grid;place-items:center;padding:24px}.workspace-login form{width:min(420px,100%);padding:32px;display:grid;gap:20px}.workspace-login h1{font-size:26px}.workspace-login p{color:var(--text-muted,#687779)}.workspace-login input{width:100%;box-sizing:border-box}</style>
