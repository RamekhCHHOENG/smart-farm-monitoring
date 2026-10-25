<script setup lang="ts">
// Stacked bars, one per day (or any label): each bar split into coloured parts, a legend underneath, values on hover.
const props = withDefaults(defineProps<{
  labels: string[];
  series: { name: string; color: string; values: number[] }[];
  unit?: string;
  height?: number;
  /** the bar shown in full colour (the others are softer); defaults to the last one, today */
  highlight?: number | null;
}>(), { unit: "", height: 140, highlight: undefined });
const focusBar = computed(() => (props.highlight === undefined ? props.labels.length - 1 : props.highlight));

const totals = computed(() => props.labels.map((_, i) => props.series.reduce((s, x) => s + (x.values[i] ?? 0), 0)));
const max = computed(() => Math.max(1, ...totals.value));
const hover = ref<number | null>(null);
// the tooltip sits over its bar, but lines up with the chart's edge for the first and last bars so it stays inside
function tipPlace(i: number) {
  if (i <= 1) return "left-0";
  if (i >= props.labels.length - 2) return "right-0";
  return "left-1/2 -translate-x-1/2";
}
const fmt = (v: number) => (v >= 10 ? Math.round(v).toString() : v.toFixed(1).replace(/\.0$/, ""));
</script>

<template>
  <div>
    <div class="relative flex items-end gap-1.5 border-b" :style="{ height: `${height}px` }">
      <div class="pointer-events-none absolute inset-x-0 top-0 border-t border-dashed" />
      <span class="pointer-events-none absolute -top-2 left-0 bg-card pr-1 text-[10px] text-muted-foreground">{{ fmt(max) }}{{ unit }}</span>
      <button v-for="(label, i) in labels" :key="label" type="button" class="group relative flex h-full flex-1 flex-col justify-end"
        :aria-label="`${label}: ${fmt(totals[i]!)}${unit}`" @mouseenter="hover = i" @mouseleave="hover = null" @focus="hover = i" @blur="hover = null">
        <div class="bar flex w-full flex-col-reverse overflow-hidden rounded-t-md" :style="{ height: `${(totals[i]! / max) * 100}%`, animationDelay: `${i * 0.05}s` }">
          <div v-for="s in series" :key="s.name" :style="{ flexGrow: s.values[i] ?? 0, background: s.color }" class="min-h-0 transition-opacity"
            :class="hover !== null ? (hover === i ? '' : 'opacity-40') : (focusBar === null || focusBar === i ? '' : 'opacity-45')" />
        </div>
        <div v-if="hover === i" class="absolute bottom-full z-10 mb-1 rounded-lg border bg-popover px-2 py-1 text-left text-[11px] whitespace-nowrap shadow-soft" :class="tipPlace(i)">
          <div class="font-medium">{{ label }} · {{ fmt(totals[i]!) }}{{ unit }}</div>
          <div v-for="s in series.filter((x) => (x.values[i] ?? 0) > 0)" :key="s.name" class="flex items-center gap-1.5 text-muted-foreground">
            <span class="size-2 shrink-0 rounded-full" :style="{ background: s.color }" /> <span class="flex-1">{{ s.name }}</span>
            <span class="num ml-3 font-medium text-foreground">{{ fmt(s.values[i]!) }}{{ unit }}</span>
          </div>
        </div>
      </button>
    </div>
    <div class="mt-1 flex gap-1.5">
      <span v-for="label in labels" :key="label" class="flex-1 text-center text-[10px] text-muted-foreground">{{ label }}</span>
    </div>
    <div class="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
      <span v-for="s in series" :key="s.name" class="flex items-center gap-1.5"><span class="size-2 rounded-full" :style="{ background: s.color }" />{{ s.name }}</span>
    </div>
  </div>
</template>

<style scoped>
.bar { transform-origin: bottom; animation: rise-bar 0.55s var(--ease-out) both; transition: height 0.4s var(--ease-out); }
@keyframes rise-bar { from { transform: scaleY(0); } to { transform: none; } }
</style>
