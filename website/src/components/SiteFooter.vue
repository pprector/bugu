<template>
  <footer class="footer">
    <div class="container">
      <div class="top">
        <div class="brand">
          <div class="brand-row">
            <span class="name">布谷</span>
            <span class="en">BUGU</span>
          </div>
          <p class="slogan">让每一份惦记，都有回音。</p>
        </div>

        <nav class="links" aria-label="页脚导航">
          <a href="#how">它怎么叫你</a>
          <a href="#companion">有人惦记你</a>
          <a href="#promise">不鸽保证</a>
          <a href="#download">下载 App</a>
        </nav>
      </div>

      <div class="bottom">
        <span>© 2026 布谷 BUGU · v1.0.0</span>
        <span>布谷布谷，有人惦记你。</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  border-top: 1px solid var(--hairline);
  background: var(--warm-fill);
  padding-block: 52px 36px;
}

.top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 32px;
  flex-wrap: wrap;
}

.brand-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.name {
  font-size: 22px;
  font-weight: 800;
}

.en {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.22em;
  color: var(--coral);
}

.slogan {
  margin-top: 8px;
  font-size: 14px;
  color: var(--ink-2);
}

.links {
  display: flex;
  gap: 26px;
  flex-wrap: wrap;
}

.links a {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
  transition: color 0.2s ease;
}

.links a:hover {
  color: var(--ink);
}

.bottom {
  margin-top: 40px;
  padding-top: 22px;
  border-top: 1px solid var(--hairline);
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--ink-3);
}
</style>
