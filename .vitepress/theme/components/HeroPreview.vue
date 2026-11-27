<script setup lang="ts">
// A terminal showing the one thing machines do with BastionVault: fetch a group.
</script>

<template>
  <section class="bv-preview" aria-label="Example: fetch a group as a .env file">
    <div class="bv-terminal">
      <div class="bv-terminal-bar">
        <span class="bv-terminal-dots"><i /><i /><i /></span>
        <span class="bv-terminal-title">ansible@control ~ </span>
        <span class="bv-terminal-state"><span class="bv-dot" />unsealed</span>
      </div>
<pre class="bv-terminal-body"><span class="c">#</span> <span class="m">every secret of the affine group, as a .env file</span>
<span class="p">$</span> curl -s -H <span class="s">"Authorization: Bearer $BV_API_KEY"</span> \
    https://bastionvault.homelab118.home/api/v1/env/<span class="k">affine</span>
<span class="o">DATABASE_URL='postgres://affine:••••••••@192.168.1.140:5432/affine'
REDIS_SERVER_HOST='redis'
AFFINE_SERVER_EXTERNAL_URL='https://affine.homelab118.home'</span>

<span class="p">$</span> curl -s -H <span class="s">"Authorization: Bearer $BV_API_KEY"</span> \
    https://bastionvault.homelab118.home/api/v1/env/<span class="k">postgres</span>
<span class="e">{"error":"Client 'ansible' has no read access to 'postgres'"}</span>  <span class="m"># 403</span></pre>
    </div>
  </section>
</template>

<style scoped>
.bv-preview {
  padding: 0 24px 64px;
}

.bv-terminal {
  max-width: 760px;
  margin: 0 auto;
  border-radius: 16px;
  background: var(--bv-vault);
  border: 1px solid var(--bv-vault-edge);
  box-shadow: var(--bv-door-shadow);
  overflow: hidden;
}

.bv-terminal-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 40px;
  padding: 0 16px;
  border-bottom: 1px solid var(--bv-vault-line);
  font-family: var(--vp-font-family-mono);
  font-size: 12px;
  color: var(--bv-vault-muted);
}

.bv-terminal-dots {
  display: inline-flex;
  gap: 6px;
}

.bv-terminal-dots i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--bv-vault-raised);
}

.bv-terminal-title {
  flex: 1;
}

.bv-terminal-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.bv-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--bv-accent);
}

.bv-terminal-body {
  margin: 0;
  padding: 20px 24px 24px;
  overflow-x: auto;
  font-family: var(--vp-font-family-mono);
  font-size: 13px;
  line-height: 1.7;
  color: var(--bv-vault-text);
  background: none;
}

.p { color: var(--bv-vault-muted); user-select: none; }
.c, .m { color: var(--bv-vault-muted); }
.s { color: #e0c9a6; }
.k { color: var(--bv-accent); }
.o { color: var(--bv-vault-text); opacity: 0.82; }
.e { color: #f08c78; }

@media (max-width: 640px) {
  .bv-preview { padding: 0 16px 48px; }
  .bv-terminal-body { font-size: 12px; padding: 16px; }
}
</style>
