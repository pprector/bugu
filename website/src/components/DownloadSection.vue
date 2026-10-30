<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import GuguBird from './GuguBird.vue'

// 生产发布直链:服务器 Nginx 托管 /var/www/bugu-web/downloads/bugu.apk
const APK_URL = 'https://www.yzhzhaohao.cn/downloads/bugu.apk'

const mood = ref<'happy' | 'bell'>('happy')
let ringTimer: number | undefined

// 每隔一会儿让咕咕唱两句「布谷～布谷～」
function scheduleRing() {
  ringTimer = window.setInterval(() => {
    mood.value = 'bell'
    window.setTimeout(() => (mood.value = 'happy'), 1400)
  }, 5200)
}

onMounted(scheduleRing)
onUnmounted(() => window.clearInterval(ringTimer))

function peck() {
  mood.value = 'bell'
  window.clearInterval(ringTimer)
  scheduleRing()
}
</script>

<template>
  <section id="download" class="section dl">
    <div class="container dl-inner">
      <div class="bird" v-reveal>
        <GuguBird :mood="mood" @peck="peck" />
      </div>
      <h2 class="section-title" v-reveal="90">把这只布谷，带回家。</h2>
      <p class="meta" v-reveal="170">免费 · Android 8.0 以上 · 安装包约 20MB</p>
      <a class="btn btn-primary dl-btn" :href="APK_URL" v-reveal="250">下载 Android 版</a>
      <p class="reassure" v-reveal="330">装好之后，它会先对你说一声：布谷布谷，有人惦记你。</p>
    </div>
  </section>
</template>

<style scoped>
.dl {
  text-align: center;
}

.dl-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.bird {
  width: min(210px, 52vw);
  margin-bottom: 8px;
}

.meta {
  margin-top: 14px;
  font-size: 14px;
  color: var(--ink-3);
  letter-spacing: 0.02em;
}

.dl-btn {
  margin-top: 30px;
  min-width: 220px;
}

.reassure {
  margin-top: 20px;
  font-size: 14px;
  color: var(--ink-2);
}
</style>
