<script setup lang="ts">
// When one actuator ran over the last 24 hours: a strip with a block for every run, hours marked underneath.
const props = defineProps<{ runs: { start: number; end: number }[]; from: number; to: number; tone?: string }>();

const span = computed(() => props.to - props.from || 1);
const blocks = computed(() => props.runs
  .map((r) => ({ left: ((Math.max(r.start, props.from) - props.from) / span.value) * 100, right: ((Math.min(r.end, props.to) - props.from) / span.value) * 100, r }))
  .filter((b) => b.right > b.left)
  .map((b) => ({ ...b, width: Math.max(0.6, b.right - b.left) })));
const hours = computed(() => [0, 0.25, 0.5, 0.75, 1].map((f) => props.from + f * span.value));
const fmt = (t: number) => new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
</script>

<template>
  <div>
    <div class="relative h-3.5 overflow-hidden rounded-full bg-muted">
      <div v-for="(b, i) in blocks" :key="i" class="tl-block absolute inset-y-0 rounded-full" :class="tone ?? 'bg-primary'"
        :style="{ left: `${b.left}%`, width: `${b.width}%`, animationDelay: `${0.15 + i * 0.04}s` }"
        :title="`${fmt(b.r.start)} – ${fmt(b.r.end)} (${Math.max(1, Math.round((b.r.end - b.r.start) / 60000))} min)`" />
    </div>
    <div class="mt-1 flex justify-between text-[10px] text-muted-foreground">
      <span v-for="(h, i) in hours" :key="i">{{ i === 4 ? "now" : fmt(h) }}</span>
    </div>
  </div>
</template>

<style scoped>
.tl-block { transform-origin: left; animation: grow 0.5s var(--ease-out) both; }
@keyframes grow { from { transform: scaleX(0); opacity: 0; } to { transform: none; opacity: 1; } }
</style>
