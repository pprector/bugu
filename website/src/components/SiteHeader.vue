<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const scrolled = ref(false)
const onScroll = () => {
  scrolled.value = window.scrollY > 8
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <header class="site-header" :class="{ scrolled }">
    <div class="container bar">
      <a class="logo" href="#top" aria-label="布谷 BUGU 首页">
        <svg viewBox="0 0 64 64" class="logo-mark" aria-hidden="true">
          <circle cx="32" cy="36" r="26" fill="#FA5E38" />
          <ellipse cx="32" cy="43" rx="16" ry="13" fill="#FFF6EE" />
          <circle cx="24" cy="30" r="3" fill="#1E1B18" />
          <circle cx="40" cy="30" r="3" fill="#1E1B18" />
          <path d="M32 34 L28 40 L36 40 Z" fill="#FFC966" />
          <path
            d="M32 10 C32 6 36 4 38 6"
            stroke="#10B981"
            stroke-width="4"
            stroke-linecap="round"
            fill="none"
          />
          <circle cx="39" cy="7" r="4.5" fill="#10B981" />
        </svg>
        <span class="logo-name">布谷</span>
        <span class="logo-en">BUGU</span>
      </a>

      <nav class="nav" aria-label="页面导航">
        <a href="#how">它怎么叫你</a>
        <a href="#companion">有人惦记你</a>
        <a href="#promise">不鸽保证</a>
      </nav>

      <a class="btn btn-primary btn-cta" href="#download">下载 App</a>
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: fixed;
  inset-inline: 0;
  top: 0;
  z-index: 50;
  transition:
    background 0.3s ease,
    box-shadow 0.3s ease;
}

.site-header.scrolled {
  background: rgba(250, 248, 245, 0.86);
  backdrop-filter: blur(14px) saturate(1.4);
  -webkit-backdrop-filter: blur(14px) saturate(1.4);
  box-shadow: 0 1px 0 var(--hairline);
}

.bar {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 68px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 30px;
  height: 30px;
}

.logo-name {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.logo-en {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.22em;
  color: var(--coral);
  transform: translateY(4px);
}

.nav {
  display: flex;
  gap: 30px;
  margin-left: auto;
}

.nav a {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-2);
  transition: color 0.2s ease;
}

.nav a:hover {
  color: var(--ink);
}

.btn-cta {
  padding: 10px 22px;
  font-size: 15px;
}

@media (max-width: 720px) {
  .nav {
    display: none;
  }

  .btn-cta {
    margin-left: auto;
  }
}
</style>
