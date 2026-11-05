<script setup lang="ts">
import {ref,watch} from 'vue';
import {safeImage,type Branding} from './platformBranding';
const props=defineProps<{branding:Branding}>();const failed=ref(false);watch(()=>props.branding.logoUrl,()=>failed.value=false);
</script>
<template><div class="platform-brand"><img v-if="safeImage(branding.logoUrl)&&!failed" :src="safeImage(branding.logoUrl)" alt="平台 Logo" @error="failed=true"/><span v-else class="brand-mark" aria-hidden="true">ISM</span><strong>{{branding.systemName}}</strong></div></template>
<style scoped>.platform-brand{display:flex;align-items:center;gap:12px;min-width:0;max-width:100%;margin-bottom:16px;}.platform-brand img{height:40px;width:40px;object-fit:contain;flex-shrink:0;}.platform-brand strong{overflow-wrap:anywhere;line-height:1.5;min-width:0;}.brand-mark{font-size:12px;padding:10px 6px;background:#e8f3f2;color:#166962;border-radius:6px;flex-shrink:0;}</style>
