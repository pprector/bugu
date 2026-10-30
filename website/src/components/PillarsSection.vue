<script setup lang="ts">
const pillars = [
  {
    title: '绝不鸽的提醒',
    desc: '到点布谷铃响两声；没回应，它会再唤你一次。这个承诺，写进了布谷的代码里。',
    icon: 'bell',
    tone: 'coral',
  },
  {
    title: '温柔不催的陪伴',
    desc: '漏了就是漏了，咕咕从不责备。它只会轻轻说：「这条惦记还在等你～」。',
    icon: 'leaf',
    tone: 'mint',
  },
  {
    title: '有人惦记你',
    desc: '把惦记送给重要的人，也收到 TA 发来的那一条。提醒是功能，惦记是关系。',
    icon: 'heart',
    tone: 'rose',
  },
] as const
</script>

<template>
  <section id="why" class="section">
    <div class="container">
      <p class="eyebrow" v-reveal>为什么是布谷</p>
      <h2 class="section-title" v-reveal="80">提醒是功能，惦记是关系。</h2>

      <div class="pillars">
        <article
          v-for="(p, i) in pillars"
          :key="p.title"
          class="pillar"
          v-reveal="i * 120"
        >
          <span class="chip" :class="`chip-${p.tone}`" aria-hidden="true">
            <svg
              v-if="p.icon === 'bell'"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M6 9a6 6 0 0 1 12 0c0 4 1.5 5.5 2 6H4c.5-.5 2-2 2-6z" />
              <path d="M10 19a2 2 0 0 0 4 0" />
            </svg>
            <svg
              v-else-if="p.icon === 'leaf'"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M5 19C5 9 11 4 20 4c0 9-5 15-15 15z" />
              <path d="M5 19c3-4 6-7 10-9" />
            </svg>
            <svg
              v-else
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M12 20.5C12 20.5 4 15 4 9.5C4 6.4 6.3 4.5 8.7 4.5C10.1 4.5 11.3 5.2 12 6.3C12.7 5.2 13.9 4.5 15.3 4.5C17.7 4.5 20 6.4 20 9.5C20 15 12 20.5 12 20.5Z"
              />
            </svg>
          </span>
          <h3>{{ p.title }}</h3>
          <p>{{ p.desc }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.pillars {
  margin-top: clamp(48px, 7vw, 84px);
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}

.pillar {
  padding: 8px 40px;
}

.pillar + .pillar {
  border-left: 1px solid var(--hairline);
}

.chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 16px;
}

.chip svg {
  width: 24px;
  height: 24px;
}

.chip-coral {
  background: var(--coral-tint);
  color: var(--coral);
}

.chip-mint {
  background: var(--mint-tint);
  color: var(--mint);
}

.chip-rose {
  background: rgba(244, 63, 94, 0.1);
  color: var(--rose);
}

.pillar h3 {
  margin-top: 20px;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.pillar p {
  margin-top: 10px;
  font-size: 15px;
  color: var(--ink-2);
}

@media (max-width: 880px) {
  .pillars {
    grid-template-columns: 1fr;
    gap: 40px;
  }

  .pillar {
    padding: 0;
  }

  .pillar + .pillar {
    border-left: none;
    border-top: 1px solid var(--hairline);
    padding-top: 40px;
  }
}
</style>
