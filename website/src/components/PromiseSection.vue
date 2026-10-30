<template>
  <section id="promise" class="band">
    <span class="feather feather-a" aria-hidden="true"></span>
    <span class="feather feather-b" aria-hidden="true"></span>

    <div class="container band-inner">
      <p class="band-kicker" v-reveal>咕咕向你保证</p>
      <h2 class="band-title" v-reveal="90">不鸽保证</h2>
      <p class="band-line" v-reveal="180">提醒、闹钟、看板，永久免费。<br />装扮与云备份，才需要付费。</p>
      <p class="band-sub" v-reveal="270">
        漏了不责备，误了不吼叫——布谷只负责，再叫你一次。
      </p>
    </div>
  </section>
</template>

<style scoped>
.band {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, var(--coral-light), var(--coral));
  color: #fff;
}

.band-inner {
  padding-block: clamp(88px, 11vw, 140px);
  text-align: center;
}

.band-kicker {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.22em;
  opacity: 0.85;
}

.band-title {
  margin-top: 14px;
  font-size: clamp(44px, 7vw, 76px);
  font-weight: 800;
  letter-spacing: 0.06em;
  line-height: 1.1;
}

.band-line {
  margin-top: 26px;
  font-size: clamp(17px, 2.2vw, 21px);
  font-weight: 700;
  line-height: 1.8;
}

.band-sub {
  margin-top: 18px;
  font-size: 14.5px;
  opacity: 0.82;
}

/* 翘尾羽毛装饰，呼应咕咕尾羽 */
.feather {
  position: absolute;
  background: rgba(255, 255, 255, 0.09);
  border-radius: 999px;
  pointer-events: none;
}

.feather-a {
  width: 180px;
  height: 56px;
  left: -50px;
  top: 10%;
  transform: rotate(-32deg);
}

.feather-b {
  width: 240px;
  height: 70px;
  right: -70px;
  bottom: 8%;
  transform: rotate(-18deg);
}
</style>
