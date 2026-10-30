<script setup lang="ts">
const steps = [
  { num: '01', title: '设一条惦记', desc: '一句话，或用「咕咕快记」说给布谷听，10 秒记下。' },
  { num: '02', title: '布谷铃响起', desc: '「布谷～布谷～」两声，那只鸟准时叫你。' },
  { num: '03', title: '没回应，再唤一次', desc: '15 分钟后，咕咕轻轻再叫你一遍，不催不吼。' },
  { num: '04', title: '还静着？告诉 TA', desc: '30 分钟没动静，就让设惦记的人知道——TA 在等你。' },
  { num: '05', title: '24 小时，必有回音', desc: '无论结果如何，这份惦记不会石沉大海。' },
]
</script>

<template>
  <section id="how" class="section section-alt">
    <div class="container">
      <p class="eyebrow" v-reveal>它怎么叫你</p>
      <h2 class="section-title" v-reveal="80">一条惦记的旅程。</h2>

      <ol class="steps">
        <li v-for="(s, i) in steps" :key="s.num" class="step" v-reveal="i * 110">
          <span class="dot" aria-hidden="true"></span>
          <span class="num">{{ s.num }}</span>
          <h3>{{ s.title }}</h3>
          <p>{{ s.desc }}</p>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.steps {
  margin-top: clamp(48px, 7vw, 84px);
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 30px;
  list-style: none;
}

.steps::before {
  content: '';
  position: absolute;
  top: 7px;
  left: 8px;
  right: 8px;
  height: 2px;
  background: var(--track);
}

.step {
  position: relative;
  padding-top: 36px;
}

.dot {
  position: absolute;
  top: 0;
  left: 0;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--coral);
  box-shadow: 0 0 0 5px var(--coral-tint);
}

.num {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--coral);
  font-variant-numeric: tabular-nums;
}

.step h3 {
  margin-top: 6px;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.step p {
  margin-top: 8px;
  font-size: 14.5px;
  line-height: 1.65;
  color: var(--ink-2);
}

@media (max-width: 880px) {
  .steps {
    display: flex;
    flex-direction: column;
    gap: 42px;
  }

  .steps::before {
    top: 8px;
    bottom: 8px;
    left: 7px;
    right: auto;
    width: 2px;
    height: auto;
  }

  .step {
    padding-top: 0;
    padding-left: 42px;
  }

  .dot {
    top: 3px;
  }
}
</style>
