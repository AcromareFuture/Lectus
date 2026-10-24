<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { X } from '@lucide/vue';
const props = defineProps<{ open: boolean; title: string; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();
watch(() => props.open, async open => {
  await nextTick();
  if (open && !dialog.value?.open) dialog.value?.showModal();
  if (!open && dialog.value?.open) dialog.value?.close();
}, { immediate: true });
</script>
<template>
  <dialog ref="dialog" :class="['modal', { wide }]" aria-labelledby="modal-title" @cancel.prevent="emit('close')" @click="e => { if (e.target === dialog) emit('close'); }">
    <header class="modal-heading"><div><span class="eyebrow">LECTUS PLATFORM</span><h2 id="modal-title">{{ title }}</h2></div><button class="icon-button" aria-label="关闭弹窗" @click="emit('close')"><X :size="20" aria-hidden="true" /></button></header>
    <slot />
  </dialog>
</template>
