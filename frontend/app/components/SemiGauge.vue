<script setup lang="ts">
// A half-circle score gauge (0-100) made of short segments that go green, gold, orange, red, with a needle.
// Used for the stress index: low is good.
const props = withDefaults(defineProps<{ value: number; label?: string; size?: number }>(), { label: "", size: 180 });
const N = 28;
const shown = ref(0);
onMounted(() => requestAnimationFrame(() => (shown.value = props.value)));
watch(() => props.value, (v) => (shown.value = v));
const segColor = (i: number) => {
  const f = i / (N - 1);
  return f < 0.3 ? "#6fa83a" : f < 0.55 ? "#e8b440" : f < 0.75 ? "#ea8a32" : "#dd5530";
};
const segs = Array.from({ length: N }, (_, i) => {
  const a = Math.PI + (i / (N - 1)) * Math.PI; // left to right over the top
  const r1 = 70; const r2 = 88;
  return { i, x1: 100 + r1 * Math.cos(a), y1: 100 + r1 * Math.sin(a), x2: 100 + r2 * Math.cos(a), y2: 100 + r2 * Math.sin(a) };
});
const lit = computed(() => Math.round((shown.value / 100) * (N - 1)));
const needle = computed(() => -90 + (shown.value / 100) * 180);
</script>

<template>
  <div class="flex flex-col items-center" :style="{ width: `${size}px` }">
    <svg viewBox="0 0 200 108" class="w-full overflow-visible" aria-hidden="true">
      <line v-for="s in segs" :key="s.i" :x1="s.x1" :y1="s.y1" :x2="s.x2" :y2="s.y2" stroke-width="5.5" stroke-linecap="round"
        :stroke="s.i <= lit ? segColor(s.i) : 'var(--muted)'" class="seg" :style="{ transitionDelay: `${s.i * 18}ms` }" />
      <g class="needle" :style="{ transform: `rotate(${needle}deg)` }">
        <line x1="100" y1="100" x2="100" y2="40" stroke="var(--foreground)" stroke-width="3" stroke-linecap="round" />
      </g>
      <circle cx="100" cy="100" r="7" fill="var(--foreground)" />
      <circle cx="100" cy="100" r="3" fill="var(--card)" />
    </svg>
    <div class="mt-1 text-center leading-none">
      <span class="num text-2xl"><AnimatedNumber :value="value" /></span>
      <div v-if="label" class="mt-0.5 text-[11px] text-muted-foreground">{{ label }}</div>
    </div>
  </div>
</template>

<style scoped>
.seg { transition: stroke 0.25s ease; }
.needle { transform-origin: 100px 100px; transition: transform 0.9s var(--ease-out); }
</style>
