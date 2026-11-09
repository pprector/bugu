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
            <g transform="translate(-26.43,-19.15)">
              <path fill="#E0523F" d="M46.5,30.5 A3.2,3.2 0 1,1 46.4,30.4 Z"/>
              <path fill="#E0523F" d="M55,27.5 A3.6,3.6 0 1,1 54.9,27.4 Z"/>
              <g transform="rotate(-18 38 62)"><path fill="#E0523F" d="M38,53 C 33.5,53 31,57.5 31,62.5 C 31,67.5 33.5,72 38,72 C 42.5,72 45,67.5 45,62.5 C 45,57.5 42.5,53 38,53 Z"/></g>
              <g transform="rotate(18 70 62)"><path fill="#E0523F" d="M70,53 C 65.5,53 63,57.5 63,62.5 C 63,67.5 65.5,72 70,72 C 74.5,72 77,67.5 77,62.5 C 77,57.5 74.5,53 70,53 Z"/></g>
              <path fill="#FA5E38" d="M54,34 C 66.5,34 75,44 75,55.5 C 75,67 66.5,76 54,76 C 41.5,76 33,67 33,55.5 C 33,44 41.5,34 54,34 Z"/>
              <path fill="#FFF3EC" d="M54,47.5 C 60.8,47.5 65.5,52 65.5,58 C 65.5,64 60.8,68.5 54,68.5 C 47.2,68.5 42.5,64 42.5,58 C 42.5,52 47.2,47.5 54,47.5 Z"/>
              <path fill="#FA5E38" d="M54,62.2 C 52.9,60.9 50.7,61.5 51.2,63.4 C 51.6,64.9 52.8,66 54,66.9 C 55.2,66 56.4,64.9 56.8,63.4 C 57.3,61.5 55.1,60.9 54,62.2 Z"/>
              <path fill="#1C1917" d="M46.4,48.5 A2.1,2.1 0 1,1 46.3,48.4 Z"/>
              <path fill="#1C1917" d="M61.6,48.5 A2.1,2.1 0 1,1 61.5,48.4 Z"/>
              <path fill="#FFFFFF" d="M47.1,47.8 A0.75,0.75 0 1,1 47.0,47.7 Z"/>
              <path fill="#FFFFFF" d="M62.3,47.8 A0.75,0.75 0 1,1 62.2,47.7 Z"/>
              <path fill="#FFB020" d="M50,53 L58,53 L54,58.5 Z"/>
              <path fill="#F59E0B" d="M52,59.5 L56,59.5 L54,63.5 Z"/>
              <path fill="#FFB8A8" d="M40.5,56 C 42.3,56 43.7,57.2 43.7,58.7 C 43.7,60.2 42.3,61.4 40.5,61.4 C 38.7,61.4 37.3,60.2 37.3,58.7 C 37.3,57.2 38.7,56 40.5,56 Z"/>
              <path fill="#FFB8A8" d="M67.5,56 C 69.3,56 70.7,57.2 70.7,58.7 C 70.7,60.2 69.3,61.4 67.5,61.4 C 65.7,61.4 64.3,60.2 64.3,58.7 C 64.3,57.2 65.7,56 67.5,56 Z"/>
              <path fill="none" stroke="#1C1917" stroke-width="1.8" stroke-linecap="round" d="M77.5,41 A5.5,5.5 0 0,1 77.5,51.5"/>
              <path fill="none" stroke="#1C1917" stroke-width="1.8" stroke-linecap="round" stroke-opacity="0.45" d="M82,36.5 A9.5,9.5 0 0,1 82,51"/>
            </g>
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
