<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import GuguBird from '../components/GuguBird.vue'

// 生产发布直链，与 DownloadSection 保持一致
const APK_URL = 'https://www.yzhzhaohao.cn/downloads/bugu.apk'
const CODE_REGEX = /^\d{6}$/
const ATTEMPT_FLAG = 'bugu.bond.attempted'

const rawCode = ref('')
const hasCodeParam = ref(false)
const status = ref<'idle' | 'opening' | 'failed'>('idle')
const copied = ref(false)
const manual = ref('')
const manualError = ref('')

let timer: number | undefined

const code = computed(() => rawCode.value.trim())
const valid = computed(() => CODE_REGEX.test(code.value))
const deepLink = computed(() => `bugu://bond?code=${code.value}`)

const isMobile = /Android|iPhone|iPad|iPod|HarmonyOS|Mobile/i.test(navigator.userAgent)
const isAndroid = /Android/i.test(navigator.userAgent)

function readParams() {
  const params = new URLSearchParams(window.location.search)
  const fromQuery = params.get('code')
  const fromHash = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('code')
  const value = fromQuery ?? fromHash ?? ''
  hasCodeParam.value = value.trim().length > 0
  rawCode.value = value
}

function openApp() {
  if (!valid.value) return
  status.value = 'opening'
  copied.value = false
  try {
    window.sessionStorage.setItem(ATTEMPT_FLAG, '1')
  } catch {
    // 隐私模式下 sessionStorage 可能不可用，忽略即可
  }
  window.clearTimeout(timer)
  // 已安装 → 系统接住 schema 并切到 App；未安装 → 停留本页，超时后给出回退引导
  timer = window.setTimeout(() => {
    if (document.visibilityState === 'visible') status.value = 'failed'
  }, 1800)
  try {
    window.location.href = deepLink.value
  } catch {
    status.value = 'failed'
  }
}

function onVisibility() {
  if (document.visibilityState === 'hidden') window.clearTimeout(timer)
}

async function copyCode() {
  try {
    await navigator.clipboard.writeText(code.value)
    copied.value = true
    window.setTimeout(() => (copied.value = false), 1800)
  } catch {
    copied.value = false
  }
}

function submitManual() {
  const value = manual.value.trim()
  if (!CODE_REGEX.test(value)) {
    manualError.value = '请输入 6 位数字邀请码'
    return
  }
  manualError.value = ''
  window.location.href = `./?code=${value}`
}

onMounted(() => {
  readParams()
  document.addEventListener('visibilitychange', onVisibility)
  let attempted = false
  try {
    attempted = window.sessionStorage.getItem(ATTEMPT_FLAG) === '1'
  } catch {
    attempted = false
  }
  // 仅 Android 且带合法邀请码时自动唤起一次；重进本页不再循环触发
  if (valid.value && isAndroid && !attempted) {
    window.setTimeout(openApp, 500)
  } else if (valid.value && attempted) {
    status.value = 'failed'
  }
})

onUnmounted(() => {
  window.clearTimeout(timer)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <div class="bond-page">
    <header class="bond-header">
      <div class="container bar">
        <a class="logo" href="../index.html" aria-label="布谷 BUGU 首页">
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
        <nav aria-label="页面导航">
          <a href="../privacy.html">隐私政策</a>
          <a href="../terms.html">用户协议</a>
        </nav>
      </div>
    </header>

    <main class="bond-main">
      <div class="container">
        <section v-if="valid" class="bond-card" aria-labelledby="bond-title">
          <div class="bird">
            <GuguBird mood="happy" />
          </div>

          <p class="eyebrow">收到一份惦记</p>
          <h1 id="bond-title" class="bond-title">有人想和你，<strong>互相惦记</strong>。</h1>
          <p class="lead">
            TA 在布谷里设了一条惦记，想请你收下。接受之后，TA 到点的提醒会送到你手上，
            你的一句回应，TA 也能看见。
          </p>

          <div class="code-box">
            <span class="code-label">邀请码</span>
            <span class="code-value">{{ code }}</span>
            <button type="button" class="copy" @click="copyCode">
              {{ copied ? '已复制' : '复制' }}
            </button>
          </div>

          <div class="actions">
            <button type="button" class="btn btn-primary open-btn" @click="openApp">
              <span v-if="status === 'opening'" class="spinner" aria-hidden="true"></span>
              {{ status === 'opening' ? '正在唤醒布谷…' : '打开布谷，接受这份惦记' }}
            </button>
            <a class="btn btn-ghost" :href="APK_URL">还没装？下载 Android 版</a>
          </div>

          <p v-if="status === 'failed'" class="fallback" role="status">
            没有跳到布谷？若已安装，<button type="button" class="linklike" @click="openApp">点这里再试一次</button>；
            若还没安装，请先下载安装。
          </p>
          <p v-else-if="status === 'idle'" class="hint">
            仅支持 Android 8.0 及以上{{ isMobile ? '' : '，在手机上打开本页可直接唤起布谷' }}。
          </p>
          <p v-if="!isMobile" class="hint">
            当前页面也可在 App 内手动输入上方 6 位邀请码完成绑定。
          </p>
        </section>

        <section v-else class="bond-card invalid" aria-labelledby="invalid-title">
          <div class="bird small">
            <GuguBird mood="sleep" />
          </div>

          <p class="eyebrow">邀请链接</p>
          <h1 id="invalid-title" class="bond-title">这个邀请链接，<strong>不完整</strong>。</h1>
          <p class="lead">
            <template v-if="!hasCodeParam">链接里没有邀请码，请向邀请你的家人朋友再要一次。</template>
            <template v-else>邀请码应为 6 位数字，当前链接里的内容无法识别。</template>
          </p>

          <form class="manual" @submit.prevent="submitManual">
            <label class="code-label" for="manual-code">手动输入 6 位邀请码</label>
            <div class="manual-row">
              <input
                id="manual-code"
                v-model="manual"
                inputmode="numeric"
                maxlength="6"
                placeholder="例如 123456"
                autocomplete="one-time-code"
                aria-describedby="manual-tip"
              />
              <button type="submit" class="btn btn-primary">继续</button>
            </div>
            <p id="manual-tip" class="manual-tip">
              {{ manualError || '邀请码在 App「互伴」页可以重新生成。' }}
            </p>
          </form>

          <a class="download-link" :href="APK_URL">下载布谷 Android 版</a>
        </section>

        <p class="foot-note">
          <a href="../index.html">了解布谷</a>
          <span aria-hidden="true">·</span>
          <a href="../privacy.html">隐私政策</a>
          <span aria-hidden="true">·</span>
          <a href="../terms.html">用户协议</a>
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.bond-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(520px 400px at 78% 8%, rgba(250, 94, 56, 0.1), transparent 70%),
    radial-gradient(420px 340px at 12% 92%, rgba(16, 185, 129, 0.08), transparent 70%),
    var(--canvas);
}

.bond-header {
  border-bottom: 1px solid var(--hairline);
  background: rgba(250, 248, 245, 0.88);
  backdrop-filter: blur(14px) saturate(1.4);
  -webkit-backdrop-filter: blur(14px) saturate(1.4);
}

.bar {
  display: flex;
  align-items: center;
  gap: 20px;
  min-height: 64px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
}

.logo svg {
  width: 26px;
  height: 26px;
}

.logo-name {
  font-size: 18px;
  font-weight: 800;
}

.logo-en {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: var(--coral);
  transform: translateY(3px);
}

.bar nav {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-left: auto;
}

.bar nav a {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink-2);
}

.bar nav a:hover {
  color: var(--ink);
}

.bond-main {
  flex: 1;
  display: flex;
  align-items: center;
  padding-block: clamp(32px, 6vw, 64px) clamp(48px, 8vw, 88px);
}

.bond-card {
  max-width: 560px;
  margin-inline: auto;
  background: var(--card);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-lg);
  padding: clamp(22px, 4vw, 40px);
  box-shadow: 0 30px 64px -40px rgba(30, 27, 24, 0.28);
  text-align: center;
}

.bird {
  width: 150px;
  margin: -6px auto 2px;
}

.bird.small {
  width: 110px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: var(--coral);
}

.eyebrow::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--coral);
}

.bond-title {
  margin-top: 12px;
  font-size: clamp(25px, 5vw, 34px);
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.32;
}

.bond-title strong {
  color: var(--coral);
  font-weight: 800;
}

.lead {
  margin-top: 14px;
  font-size: 15px;
  line-height: 1.75;
  color: var(--ink-2);
}

.code-box {
  margin-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px dashed var(--track);
  border-radius: 14px;
  background: var(--warm-fill);
  flex-wrap: wrap;
}

.code-label {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-2);
  letter-spacing: 0.06em;
}

.code-value {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 0.22em;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  transform: translateX(0.11em);
}

.copy {
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--hairline);
  background: #fff;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--coral-deep);
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s ease;
}

.copy:hover {
  background: var(--coral-tint);
}

.actions {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.open-btn {
  width: 100%;
  padding-block: 15px;
}

.btn .spinner {
  width: 15px;
  height: 15px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.55);
  border-top-color: #fff;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.fallback {
  margin-top: 16px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--ink-2);
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--coral-tint);
}

.linklike {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-weight: 700;
  color: var(--coral-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.hint {
  margin-top: 12px;
  font-size: 12.5px;
  color: var(--ink-2);
}

/* 非法 / 缺失邀请码 */
.manual {
  margin-top: 24px;
  text-align: left;
}

.manual-row {
  margin-top: 8px;
  display: flex;
  gap: 10px;
}

.manual-row input {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--track);
  border-radius: 12px;
  background: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: var(--ink);
  font-family: inherit;
}

.manual-row input:focus {
  outline: 2px solid var(--coral);
  outline-offset: 1px;
  border-color: transparent;
}

.manual-row .btn {
  padding: 12px 24px;
  font-size: 15px;
  flex: none;
}

.manual-tip {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--ink-2);
  min-height: 18px;
}

.download-link {
  display: inline-block;
  margin-top: 18px;
  font-size: 14px;
  font-weight: 700;
  color: var(--coral-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.foot-note {
  margin-top: 22px;
  text-align: center;
  font-size: 12.5px;
  color: var(--ink-2);
  display: flex;
  justify-content: center;
  gap: 10px;
}

.foot-note a {
  color: var(--ink-2);
}

.foot-note a:hover {
  color: var(--ink);
}

@media (max-width: 480px) {
  .bond-card {
    border-radius: var(--radius-md);
  }

  .code-value {
    font-size: 24px;
  }

  .manual-row {
    flex-direction: column;
  }

  .manual-row .btn {
    width: 100%;
  }

  .bar nav a {
    font-size: 12.5px;
  }
}
</style>
