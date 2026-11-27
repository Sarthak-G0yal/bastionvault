<script setup lang="ts">
import { withBase } from 'vitepress'
</script>

<template>
  <div class="bv-hero-logo">
    <img :src="withBase('/logo.svg')" alt="" width="88" height="88" />
    <span class="bv-hero-status"><span class="bv-dot" />Sealed until you say so</span>
  </div>
</template>

<style scoped>
.bv-hero-logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.bv-hero-logo img {
  width: 88px;
  height: 88px;
  border-radius: 22px;
  box-shadow: var(--bv-door-shadow);
}

.bv-hero-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--vp-c-border);
  border-radius: 8px;
  background: var(--vp-c-bg-elv);
  color: var(--vp-c-text-2);
  font-family: var(--vp-font-family-mono);
  font-size: 12px;
}

.bv-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--bv-accent);
}
</style>
