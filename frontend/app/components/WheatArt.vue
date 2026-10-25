<script setup lang="ts">
// A bunch of wheat ears, drawn (no photo), swaying gently. Decoration only.
// every stalk grows from one point at the bottom, fanning out like a bunch held in a hand
const ears = [
  { x: 110, y: 168, tilt: -30, len: 0.95 },
  { x: 110, y: 168, tilt: -14, len: 1.08 },
  { x: 110, y: 168, tilt: 0, len: 1.15 },
  { x: 110, y: 168, tilt: 14, len: 1.05 },
  { x: 110, y: 168, tilt: 28, len: 0.9 },
];
const grains = Array.from({ length: 7 }, (_, i) => i);
</script>

<template>
  <svg viewBox="0 0 220 170" aria-hidden="true" class="wheat">
    <defs>
      <linearGradient id="grain" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#f6d58a" />
        <stop offset="1" stop-color="#c8891f" />
      </linearGradient>
    </defs>
    <g v-for="(e, i) in ears" :key="i" class="ear" :style="{ animationDelay: `${i * 0.4}s`, transformOrigin: `${e.x}px ${e.y}px` }"
      :transform="`rotate(${e.tilt} ${e.x} ${e.y})`">
      <path :d="`M${e.x} ${e.y} L${e.x} ${e.y - 70 * e.len}`" stroke="#d9a646" stroke-width="2.2" stroke-linecap="round" />
      <g v-for="g in grains" :key="g">
        <ellipse :cx="e.x - 6" :cy="e.y - 70 * e.len - g * 10" rx="4.5" ry="8" fill="url(#grain)" :transform="`rotate(-28 ${e.x - 6} ${e.y - 70 * e.len - g * 10})`" />
        <ellipse :cx="e.x + 6" :cy="e.y - 70 * e.len - g * 10 - 4" rx="4.5" ry="8" fill="url(#grain)" :transform="`rotate(28 ${e.x + 6} ${e.y - 70 * e.len - g * 10 - 4})`" />
        <path :d="`M${e.x + 9} ${e.y - 70 * e.len - g * 10 - 10} l9 -12`" stroke="#e6bd6a" stroke-width="0.8" />
      </g>
      <ellipse :cx="e.x" :cy="e.y - 70 * e.len - 74" rx="4" ry="7.5" fill="url(#grain)" />
    </g>
  </svg>
</template>

<style scoped>
.ear {
  animation: sway 5s ease-in-out infinite alternate;
}
@keyframes sway {
  from { rotate: -2deg; }
  to { rotate: 2deg; }
}
@media (prefers-reduced-motion: reduce) {
  .ear { animation: none; }
}
</style>
