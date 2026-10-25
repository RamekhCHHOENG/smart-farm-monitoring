<script setup lang="ts">
// A tiny trend line of the last hour, drawn inside the sensor's valid range so a flat line means "steady", not "zero".
const props = withDefaults(defineProps<{ values: number[]; min: number; max: number; alarm?: boolean }>(), { alarm: false });

const W = 96;
const H = 24;
const points = computed(() => {
  const v = props.values;
  if (v.length < 2) return "";
  const span = props.max - props.min || 1;
  return v.map((x, i) => {
    const px = (i / (v.length - 1)) * W;
    const py = H - 2 - Math.min(1, Math.max(0, (x - props.min) / span)) * (H - 4);
    return `${px.toFixed(1)},${py.toFixed(1)}`;
  }).join(" ");
});
</script>

<template>
  <svg :viewBox="`0 0 ${W} ${H}`" :width="W" :height="H" class="shrink-0 overflow-visible" aria-hidden="true">
    <polyline v-if="points" :points="points" pathLength="1" fill="none" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" class="draw"
      :class="alarm ? 'stroke-destructive' : 'stroke-primary'" />
    <line v-else x1="0" :y1="H / 2" :x2="W" :y2="H / 2" class="stroke-border" stroke-dasharray="2 3" />
  </svg>
</template>
