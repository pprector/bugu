<script setup lang="ts">
import { ref, watch } from 'vue'
import type { GuguMood } from './gugu'

const props = withDefaults(defineProps<{ mood?: GuguMood }>(), { mood: 'happy' })
const emit = defineEmits<{ peck: [] }>()

const popping = ref(false)
watch(
  () => props.mood,
  () => {
    popping.value = false
    requestAnimationFrame(() => {
      popping.value = true
      window.setTimeout(() => (popping.value = false), 600)
    })
  },
)
</script>

<template>
  <svg
    class="gugu"
    :class="[`is-${mood}`, { 'is-pop': popping }]"
    viewBox="0 0 260 250"
    fill="none"
    role="img"
    aria-label="布谷鸟咕咕"
    @click="emit('peck')"
  >
    <defs>
      <linearGradient id="gugu-body" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#FF7243" />
        <stop offset="1" stop-color="#FA5E38" />
      </linearGradient>
    </defs>

    <!-- 开心：心心迸发 -->
    <g class="fx fx-happy">
      <g transform="translate(30 26) scale(0.6)">
        <path
          d="M12 21C12 21 3 14.5 3 8.5C3 5.2 5.4 3 8.2 3C9.8 3 11.3 3.9 12 5.2C12.7 3.9 14.2 3 15.8 3C18.6 3 21 5.2 21 8.5C21 14.5 12 21 12 21Z"
          fill="#F43F5E"
        />
      </g>
      <g transform="translate(212 18) scale(0.45)">
        <path
          d="M12 21C12 21 3 14.5 3 8.5C3 5.2 5.4 3 8.2 3C9.8 3 11.3 3.9 12 5.2C12.7 3.9 14.2 3 15.8 3C18.6 3 21 5.2 21 8.5C21 14.5 12 21 12 21Z"
          fill="#FF7243"
        />
      </g>
      <g transform="translate(232 74) scale(0.32)">
        <path
          d="M12 21C12 21 3 14.5 3 8.5C3 5.2 5.4 3 8.2 3C9.8 3 11.3 3.9 12 5.2C12.7 3.9 14.2 3 15.8 3C18.6 3 21 5.2 21 8.5C21 14.5 12 21 12 21Z"
          fill="#FA5E38"
        />
      </g>
    </g>

    <!-- 铃：声波与音符 -->
    <g class="fx fx-bell">
      <path
        d="M46 96 A 34 34 0 0 0 46 140"
        stroke="#FA5E38"
        stroke-width="5"
        stroke-linecap="round"
        opacity=".65"
      />
      <path
        d="M30 104 A 22 22 0 0 0 30 132"
        stroke="#FA5E38"
        stroke-width="5"
        stroke-linecap="round"
        opacity=".4"
      />
      <g fill="#10B981">
        <circle cx="198" cy="44" r="6" />
        <rect x="202" y="20" width="4" height="24" rx="2" />
        <path d="M206 20 q 11 2 11 11 q -4 -5 -11 -4 z" />
      </g>
      <g fill="#10B981" opacity=".6">
        <circle cx="228" cy="72" r="4.5" />
        <rect x="231" y="56" width="3" height="17" rx="1.5" />
        <path d="M234 56 q 8 1.5 8 8 q -3 -3.5 -8 -3 z" />
      </g>
    </g>

    <!-- 捎话：话泡 -->
    <g class="fx fx-note">
      <g>
        <rect x="26" y="22" width="66" height="42" rx="15" fill="#FFFFFF" stroke="#EFECE6" stroke-width="2" />
        <path d="M60 62 L52 78 L76 64 Z" fill="#FFFFFF" stroke="#EFECE6" stroke-width="2" />
        <circle cx="48" cy="43" r="4" fill="#9C9488" />
        <circle cx="61" cy="43" r="4" fill="#9C9488" />
        <circle cx="74" cy="43" r="4" fill="#9C9488" />
      </g>
    </g>

    <!-- 睡：Zzz -->
    <g class="fx fx-sleep" fill="#10B981" font-family="inherit" font-weight="800">
      <text x="216" y="62" font-size="26" transform="rotate(8 216 62)">Z</text>
      <text x="238" y="42" font-size="18" transform="rotate(8 238 42)">z</text>
      <text x="250" y="26" font-size="13" transform="rotate(8 250 26)">z</text>
    </g>

    <g class="breathe">
      <!-- 翘尾 -->
      <rect x="170" y="36" width="20" height="60" rx="10" fill="#F04E27" transform="rotate(-30 180 66)" />
      <rect x="192" y="50" width="18" height="54" rx="9" fill="#FA5E38" transform="rotate(-10 201 77)" />

      <!-- 头顶嫩芽 -->
      <path
        d="M130 60 C130 50 132 44 139 40"
        stroke="#10B981"
        stroke-width="5"
        stroke-linecap="round"
      />
      <circle cx="142" cy="37" r="8" fill="#10B981" />

      <!-- 爪子 -->
      <ellipse cx="108" cy="212" rx="10" ry="6" fill="#E8926B" />
      <ellipse cx="152" cy="212" rx="10" ry="6" fill="#E8926B" />

      <!-- 翅膀 -->
      <g class="wing wing-l">
        <ellipse cx="52" cy="148" rx="17" ry="28" fill="#F04E27" transform="rotate(14 52 148)" />
      </g>
      <g class="wing wing-r">
        <ellipse cx="208" cy="148" rx="17" ry="28" fill="#F04E27" transform="rotate(-14 208 148)" />
      </g>

      <!-- 身体与肚子 -->
      <ellipse cx="130" cy="140" rx="80" ry="74" fill="url(#gugu-body)" />
      <ellipse cx="130" cy="166" rx="50" ry="42" fill="#FFF6EE" />

      <!-- 腮红 -->
      <ellipse cx="86" cy="132" rx="11" ry="6.5" fill="#FF8A70" opacity=".55" />
      <ellipse cx="174" cy="132" rx="11" ry="6.5" fill="#FF8A70" opacity=".55" />

      <!-- 眼睛：睁眼 -->
      <g class="eyes-open">
        <circle cx="104" cy="112" r="7" fill="#1E1B18" />
        <circle cx="156" cy="112" r="7" fill="#1E1B18" />
        <circle cx="106.5" cy="109.5" r="2.3" fill="#FFFFFF" />
        <circle cx="158.5" cy="109.5" r="2.3" fill="#FFFFFF" />
      </g>
      <!-- 眼睛：闭眼 -->
      <g class="eyes-closed" stroke="#1E1B18" stroke-width="3.5" stroke-linecap="round">
        <path d="M96 112 Q 104 119 112 112" />
        <path d="M148 112 Q 156 119 164 112" />
      </g>

      <!-- 喙：闭合 -->
      <path class="beak-closed" d="M120 129 Q 130 125 140 129 L 130 142 Z" fill="#FFC966" />
      <!-- 喙：张开（唱歌） -->
      <g class="beak-open">
        <path d="M118 125 L 142 125 L 130 134 Z" fill="#FFC966" />
        <path d="M122 140 L 138 140 L 130 148 Z" fill="#FFC966" />
      </g>
    </g>
  </svg>
</template>

<style scoped>
.gugu {
  width: 100%;
  height: auto;
  cursor: pointer;
  overflow: visible;
  transform-origin: 50% 90%;
  user-select: none;
}

.gugu.is-pop {
  animation: gugu-pop 0.55s var(--ease-spring);
}

@keyframes gugu-pop {
  0% {
    transform: scale(0.9);
  }
  60% {
    transform: scale(1.06);
  }
  100% {
    transform: scale(1);
  }
}

.breathe {
  transform-box: fill-box;
  transform-origin: 50% 100%;
  animation: gugu-breathe 3.6s ease-in-out infinite alternate;
}

@keyframes gugu-breathe {
  from {
    transform: scale(1);
  }
  to {
    transform: scale(1.045);
  }
}

/* 情绪切换 */
.eyes-open,
.eyes-closed,
.beak-open,
.beak-closed,
.fx {
  transition: opacity 0.28s ease;
}

.eyes-closed,
.beak-open,
.fx {
  opacity: 0;
}

.is-sleep .eyes-open {
  opacity: 0;
}

.is-sleep .eyes-closed {
  opacity: 1;
}

.is-happy .beak-closed,
.is-bell .beak-closed {
  opacity: 0;
}

.is-happy .beak-open,
.is-bell .beak-open {
  opacity: 1;
}

.is-happy .fx-happy,
.is-bell .fx-bell,
.is-note .fx-note,
.is-sleep .fx-sleep {
  opacity: 1;
}

/* 特效轻轻浮动 */
.fx {
  animation: fx-float 3.2s ease-in-out infinite alternate;
  transform-box: fill-box;
  transform-origin: center;
}

@keyframes fx-float {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(-6px);
  }
}

/* 开心时翅膀轻抬 */
.wing {
  transition: transform 0.3s var(--ease-out);
  transform-box: fill-box;
  transform-origin: center;
}

.is-happy .wing-l {
  transform: rotate(-9deg) translate(-2px, -5px);
}

.is-happy .wing-r {
  transform: rotate(9deg) translate(2px, -5px);
}
</style>
