<script setup lang="ts">
const features = [
  { title: '互相惦记', desc: '你惦记 TA，TA 也惦记你，回执看得见。' },
  { title: '指尖轻碰', desc: '一个小动作，告诉对方「我在想你」。' },
  { title: '长辈模式', desc: '帮爸妈调大字、调柔提醒，替他们管好布谷。' },
  { title: '安静陪伴', desc: '什么时候响、怎么响，都听你的。' },
]
</script>

<template>
  <section id="companion" class="section">
    <div class="container duo">
      <div class="copy">
        <p class="eyebrow" v-reveal>有人惦记你</p>
        <h2 class="section-title" v-reveal="80">为重要的人，<br />设一条惦记。</h2>
        <p class="section-lead" v-reveal="160">
          和家人、恋人绑定为互伴，布谷就成了你们之间那只传话的鸟——你放心忙你的，惦记交给它。
        </p>

        <ul class="features">
          <li v-for="(f, i) in features" :key="f.title" v-reveal="200 + i * 90">
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </li>
        </ul>
      </div>

      <div class="chat-wrap" v-reveal="180">
        <div class="chat" aria-label="互伴惦记演示">
          <div class="chat-head">
            <span class="chat-dot" aria-hidden="true"></span>
            <span>布谷 · 互伴</span>
            <span class="chat-sub">妈 ↔ 你</span>
          </div>

          <div class="msg them" v-reveal="320">
            <span class="avatar avatar-them" aria-hidden="true">妈</span>
            <div>
              <p class="meta">妈 · 每天 08:30</p>
              <p class="bubble">记得吃早饭，别总点外卖</p>
            </div>
          </div>

          <div class="msg me" v-reveal="500">
            <span class="avatar avatar-me" aria-hidden="true">我</span>
            <div>
              <p class="meta">你 · 刚刚</p>
              <p class="bubble">好啦好啦，这就去</p>
            </div>
          </div>

          <p class="echo" v-reveal="680">
            <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path
                d="M12 20.5C12 20.5 4 15 4 9.5C4 6.4 6.3 4.5 8.7 4.5C10.1 4.5 11.3 5.2 12 6.3C12.7 5.2 13.9 4.5 15.3 4.5C17.7 4.5 20 6.4 20 9.5C20 15 12 20.5 12 20.5Z"
              />
            </svg>
            这份惦记，有回音了
          </p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.duo {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: clamp(40px, 6vw, 88px);
  align-items: center;
}

.features {
  margin-top: 36px;
  list-style: none;
}

.features li {
  display: grid;
  grid-template-columns: 96px 1fr;
  align-items: baseline;
  gap: 16px;
  padding-block: 14px;
}

.features li + li {
  border-top: 1px solid var(--hairline);
}

.features h3 {
  font-size: 15.5px;
  font-weight: 800;
  white-space: nowrap;
}

.features p {
  font-size: 14.5px;
  color: var(--ink-2);
}

/* 聊天演示 */
.chat-wrap {
  max-width: 440px;
  width: 100%;
  justify-self: center;
}

.chat {
  background: var(--card);
  border: 1px solid var(--hairline);
  border-radius: var(--radius-lg);
  padding: 26px 26px 22px;
  box-shadow: 0 30px 60px -34px rgba(30, 27, 24, 0.25);
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--ink-2);
  padding-bottom: 16px;
  border-bottom: 1px solid var(--hairline);
}

.chat-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--mint);
}

.chat-sub {
  margin-left: auto;
  font-weight: 600;
  color: var(--ink-3);
}

.msg {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.msg.me {
  flex-direction: row-reverse;
}

.avatar {
  flex: none;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 14px;
  font-weight: 800;
}

.avatar-them {
  background: var(--warm-fill);
  color: var(--coral-deep);
}

.avatar-me {
  background: var(--mint-tint);
  color: var(--mint);
}

.meta {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-bottom: 5px;
}

.msg.me .meta {
  text-align: right;
}

.bubble {
  background: var(--warm-fill);
  border-radius: 16px;
  padding: 10px 15px;
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.55;
}

.msg.them .bubble {
  border-top-left-radius: 5px;
}

.msg.me .bubble {
  background: var(--coral-tint);
  color: var(--coral-deep);
  border-top-right-radius: 5px;
}

.echo {
  margin-top: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 700;
  color: var(--coral);
}

.echo svg {
  width: 15px;
  height: 15px;
  animation: echo-beat 1.6s ease-in-out infinite;
}

@keyframes echo-beat {
  0%,
  100% {
    transform: scale(1);
  }
  30% {
    transform: scale(1.18);
  }
}

@media (max-width: 880px) {
  .duo {
    grid-template-columns: 1fr;
  }

  .chat-wrap {
    max-width: 420px;
  }
}
</style>
