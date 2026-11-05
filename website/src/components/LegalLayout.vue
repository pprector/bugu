<script setup lang="ts">
defineProps<{
  current: 'privacy' | 'terms'
  kicker: string
  title: string
  updated: string
}>()
</script>

<template>
  <div class="legal-page">
    <header class="legal-header">
      <div class="container bar">
        <a class="logo" href="./index.html" aria-label="布谷 BUGU 首页">
          <svg viewBox="0 0 64 64" aria-hidden="true">
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

        <nav aria-label="法务页面导航">
          <a href="./privacy.html" :class="{ active: current === 'privacy' }">隐私政策</a>
          <a href="./terms.html" :class="{ active: current === 'terms' }">用户协议</a>
          <a class="back" href="./index.html">回到首页</a>
        </nav>
      </div>
    </header>

    <main class="legal-main">
      <div class="container">
        <article class="paper">
          <p class="paper-kicker">{{ kicker }}</p>
          <h1 class="paper-title">{{ title }}</h1>
          <p class="paper-meta">最后更新：{{ updated }} · 生效日期：{{ updated }}</p>
          <p class="paper-notice">
            本页内容以布谷 App 内协议全文为基础，并按应用商店上架审核要求补充完善。文中标注「待法务确认」的条目为草案，正式发布前需经法务审阅与确认。
          </p>
          <div class="legal-prose">
            <slot />
          </div>
        </article>
      </div>
    </main>

    <footer class="legal-footer">
      <div class="container row">
        <span>© 2026 布谷 BUGU</span>
        <nav aria-label="页脚导航">
          <a href="./privacy.html">隐私政策</a>
          <a href="./terms.html">用户协议</a>
          <a href="./index.html">回到首页</a>
        </nav>
      </div>
    </footer>
  </div>
</template>
