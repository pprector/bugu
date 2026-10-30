<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import GuguBird from './GuguBird.vue'
import type { GuguMood } from './gugu'

interface Notif {
  mood: GuguMood
  tag: string
  time: string
  text: string
}

/* 文案全部取自布谷 App 真实推送语气（brand/布谷-品牌信息体系.md §3.2） */
const notifs: Notif[] = [
  { mood: 'bell', tag: '布谷铃', time: '08:00', text: '布谷！到点啦——别忘了惦记你的人呀' },
  { mood: 'note', tag: 'TA 的惦记', time: '12:30', text: '降温了，记得把外套穿上再出门' },
  { mood: 'happy', tag: '回音', time: '12:41', text: '「好啦好啦，这就去」——TA 回应了你的惦记' },
  { mood: 'sleep', tag: '温和催办', time: '21:00', text: '这条惦记还在等你～要现在看吗？' },
]

const idx = ref(0)
const manualMood = ref<GuguMood | null>(null)
const notif = computed(() => notifs[idx.value]!)
const birdMood = computed<GuguMood>(() => manualMood.value ?? notif.value.mood)

let timer: number | undefined

function schedule() {
  timer = window.setInterval(() => {
    idx.value = (idx.value + 1) % notifs.length
  }, 4200)
}

function peck() {
  manualMood.value = 'happy'
  window.setTimeout(() => (manualMood.value = null), 900)
}

onMounted(schedule)
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <section id="top" class="hero">
    <span class="hero-mark" aria-hidden="true">布谷</span>

    <div class="container hero-grid">
      <div class="copy">
        <p class="kicker" v-reveal>布谷 BUGU · 陪伴型提醒</p>
        <h1 class="headline" v-reveal="90">
          让每一份惦记，<br />
          <em>都有回音</em>。
        </h1>
        <p class="sub" v-reveal="180">
          布谷布谷，有人惦记你。设一条惦记，布谷准时叫你，也把你的惦记，稳稳送到
          TA 手上。
        </p>
        <div class="actions" v-reveal="270">
          <a class="btn btn-primary" href="#download">下载 Android 版</a>
          <a class="btn btn-ghost" href="#how">看看它怎么叫你</a>
        </div>
        <p class="free-note" v-reveal="360">提醒、闹钟、看板，永久免费。</p>
      </div>

      <div class="stage" v-reveal="150">
        <div class="bird">
          <GuguBird :mood="birdMood" @peck="peck" />
        </div>

        <Transition name="notif" mode="out-in">
          <div class="notif" :key="idx" role="status">
            <div class="notif-head">
              <span class="notif-dot" :class="`dot-${notif.mood}`"></span>
              <span class="notif-tag">布谷 · {{ notif.tag }}</span>
              <span class="notif-time">现在</span>
            </div>
            <p class="notif-text">{{ notif.text }}</p>
          </div>
        </Transition>

        <span class="mini-note note-a" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <circle cx="8" cy="17" r="4" />
            <rect x="10.5" y="4" width="2.6" height="13" rx="1.3" />
            <path d="M13 4 q 7 1.5 7 7.5 q -2.6 -3.4 -7 -2.8 z" />
          </svg>
        </span>
        <span class="mini-note note-b" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <circle cx="8" cy="17" r="4" />
            <rect x="10.5" y="4" width="2.6" height="13" rx="1.3" />
            <path d="M13 4 q 7 1.5 7 7.5 q -2.6 -3.4 -7 -2.8 z" />
          </svg>
        </span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  position: relative;
  min-height: 100svh;
  display: flex;
  align-items: center;
  padding-top: 92px;
  padding-bottom: 48px;
  overflow: hidden;
  background:
    radial-gradient(560px 420px at 74% 46%, rgba(250, 94, 56, 0.09), transparent 70%),
    radial-gradient(420px 320px at 12% 82%, rgba(16, 185, 129, 0.07), transparent 70%);
}

.hero-mark {
  position: absolute;
  right: -0.06em;
  top: 44%;
  transform: translateY(-50%);
  font-size: clamp(180px, 26vw, 330px);
  font-weight: 800;
  line-height: 1;
  color: transparent;
  -webkit-text-stroke: 1.5px #ece6db;
  pointer-events: none;
  user-select: none;
}

.hero-grid {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1fr);
  align-items: center;
  gap: clamp(24px, 4vw, 56px);
  width: 100%;
}

/* 文案列 */
.kicker {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--coral);
}

.headline {
  margin-top: 18px;
  font-size: clamp(40px, 6.4vw, 76px);
  font-weight: 800;
  line-height: 1.16;
  letter-spacing: -0.025em;
}

.headline em {
  font-style: normal;
  color: var(--coral);
}

.sub {
  margin-top: 22px;
  max-width: 460px;
  font-size: clamp(16px, 1.8vw, 18px);
  color: var(--ink-2);
}

.actions {
  margin-top: 34px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.free-note {
  margin-top: 18px;
  font-size: 13.5px;
  color: var(--ink-3);
}

/* 咕咕舞台 */
.stage {
  position: relative;
  max-width: 480px;
  margin-inline: auto;
  width: 100%;
}

.bird {
  width: min(420px, 88%);
  margin-inline: auto;
  animation: bird-in 0.9s var(--ease-spring) 0.2s both;
}

@keyframes bird-in {
  from {
    opacity: 0;
    transform: scale(0.7) translateY(30px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* 通知气泡 */
.notif {
  position: absolute;
  right: -6px;
  bottom: 26px;
  width: min(300px, 72%);
  background: var(--card);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-md);
  padding: 13px 16px 14px;
  box-shadow: 0 18px 40px -18px rgba(30, 27, 24, 0.22);
}

.notif-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notif-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot-bell {
  background: var(--coral);
}

.dot-note {
  background: var(--indigo);
}

.dot-happy {
  background: var(--rose);
}

.dot-sleep {
  background: var(--mint);
}

.notif-tag {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ink-2);
}

.notif-time {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--ink-3);
}

.notif-text {
  margin-top: 5px;
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.5;
}

.notif-enter-active {
  transition:
    opacity 0.4s var(--ease-spring),
    transform 0.4s var(--ease-spring);
}

.notif-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.notif-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.95);
}

.notif-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.97);
}

/* 漂浮音符 */
.mini-note {
  position: absolute;
  animation: note-float 3.4s ease-in-out infinite alternate;
}

.note-a {
  top: 6%;
  left: 4%;
  width: 30px;
  color: var(--mint);
  opacity: 0.8;
}

.note-b {
  top: 16%;
  right: 2%;
  width: 22px;
  color: var(--coral-light);
  opacity: 0.7;
  animation-delay: 1.2s;
}

@keyframes note-float {
  from {
    transform: translateY(0) rotate(-4deg);
  }
  to {
    transform: translateY(-12px) rotate(5deg);
  }
}

/* 移动端 */
@media (max-width: 880px) {
  .hero {
    padding-top: 104px;
  }

  .hero-mark {
    top: 24%;
  }

  .hero-grid {
    grid-template-columns: 1fr;
    gap: 40px;
  }

  .stage {
    max-width: 400px;
  }

  .notif {
    right: 0;
  }
}
</style>
