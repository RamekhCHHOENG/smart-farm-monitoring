<script setup lang="ts">
import { useResizeObserver } from "@vueuse/core";

/** Small dependency-free SVG line chart for sensor history, with optional threshold lines. */
const props = defineProps<{
  points: { t: number; v: number }[];
  unit: string;
  thresholds?: { value: number; label: string }[];
}>();

// Drawn at the container's real width, so labels keep their size on a phone.
const wrap = ref<HTMLElement | null>(null);
const W = ref(720);
const H = 260;
useResizeObserver(wrap, (entries) => {
  const width = entries[0]?.contentRect.width;
  if (width) W.value = Math.max(300, Math.round(width));
});
const PAD = { l: 44, r: 12, t: 12, b: 28 };

const hover = ref<number | null>(null);

const domain = computed(() => {
  const values = [...props.points.map((p) => p.v), ...(props.thresholds ?? []).map((t) => t.value)];
  let lo = Math.min(...values);
  let hi = Math.max(...values);
  if (!isFinite(lo)) return { lo: 0, hi: 1, t0: 0, t1: 1 };
  if (hi - lo < 1) { lo -= 1; hi += 1; }
  const pad = (hi - lo) * 0.1;
  const times = props.points.map((p) => p.t);
  return { lo: lo - pad, hi: hi + pad, t0: Math.min(...times), t1: Math.max(...times, Math.min(...times) + 1) };
});

const x = (t: number) => PAD.l + ((t - domain.value.t0) / (domain.value.t1 - domain.value.t0)) * (W.value - PAD.l - PAD.r);
const y = (v: number) => PAD.t + (1 - (v - domain.value.lo) / (domain.value.hi - domain.value.lo)) * (H - PAD.t - PAD.b);

const path = computed(() => props.points.map((p, i) => `${i ? "L" : "M"}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join(""));
// the soft fill under the line
const area = computed(() => {
  const pts = props.points;
  if (pts.length < 2) return "";
  const base = (H - PAD.b).toFixed(1);
  return `${path.value}L${x(pts[pts.length - 1]!.t).toFixed(1)},${base}L${x(pts[0]!.t).toFixed(1)},${base}Z`;
});
// A new range or sensor draws the line again from the left; a resize or a hover does not.
const drawKey = computed(() => `${props.points.length}:${props.points[0]?.t ?? 0}:${props.points[props.points.length - 1]?.t ?? 0}`);

const yTicks = computed(() => {
  const { lo, hi } = domain.value;
  return Array.from({ length: 5 }, (_, i) => lo + ((hi - lo) * i) / 4);
});

const xTicks = computed(() => {
  const { t0, t1 } = domain.value;
  const count = W.value < 520 ? 3 : 4;
  return Array.from({ length: count }, (_, i) => t0 + ((t1 - t0) * i) / (count - 1));
});

function onMove(e: MouseEvent) {
  const svg = e.currentTarget as SVGSVGElement;
  const rect = svg.getBoundingClientRect();
  const px = ((e.clientX - rect.left) / rect.width) * W.value;
  let best = 0;
  props.points.forEach((p, i) => {
    if (Math.abs(x(p.t) - px) < Math.abs(x(props.points[best]!.t) - px)) best = i;
  });
  hover.value = props.points.length ? best : null;
}

const hovered = computed(() => (hover.value === null ? null : props.points[hover.value]));
</script>

<template>
  <div ref="wrap" class="relative">
    <svg :viewBox="`0 0 ${W} ${H}`" class="h-auto w-full" role="img" :aria-label="`Sensor history in ${unit}`"
      @mousemove="onMove" @mouseleave="hover = null">
      <g class="text-muted-foreground" font-size="11">
        <g v-for="v in yTicks" :key="`y${v}`">
          <line :x1="PAD.l" :x2="W - PAD.r" :y1="y(v)" :y2="y(v)" stroke="var(--border)" />
          <text :x="PAD.l - 6" :y="y(v) + 4" text-anchor="end" fill="currentColor">{{ v.toFixed(0) }}</text>
        </g>
        <text v-for="(t, i) in xTicks" :key="`x${t}`" :x="x(t)" :y="H - 8"
          :text-anchor="i === 0 ? 'start' : i === xTicks.length - 1 ? 'end' : 'middle'" fill="currentColor">
          {{ new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) }}
        </text>
      </g>
      <g v-for="th in thresholds ?? []" :key="th.label">
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="y(th.value)" :y2="y(th.value)" stroke="var(--destructive)" stroke-dasharray="5 4" />
        <text :x="W - PAD.r - 4" :y="y(th.value) - 5" text-anchor="end" font-size="11" fill="var(--destructive)">{{ th.label }}</text>
      </g>
      <defs>
        <linearGradient id="chart-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="var(--primary)" stop-opacity="0.22" />
          <stop offset="1" stop-color="var(--primary)" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path :key="`a${drawKey}`" :d="area" fill="url(#chart-fill)" class="fade-in" style="animation-delay: 0.35s" />
      <path :key="`l${drawKey}`" :d="path" pathLength="1" fill="none" stroke="var(--primary)" stroke-width="2.25"
        stroke-linejoin="round" stroke-linecap="round" class="draw" />
      <g v-if="hovered" class="chart-hover" :style="{ transform: `translateX(${x(hovered.t)}px)` }">
        <line x1="0" x2="0" :y1="PAD.t" :y2="H - PAD.b" stroke="var(--muted-foreground)" stroke-opacity="0.4" />
        <circle cx="0" :cy="y(hovered.v)" r="4.5" fill="var(--primary)" stroke="var(--card)" stroke-width="2" class="chart-dot" />
      </g>
    </svg>
    <div v-if="hovered" class="pointer-events-none absolute top-2 left-14 rounded-lg border bg-popover px-2.5 py-1 text-xs shadow-soft">
      <span class="font-semibold tabular-nums">{{ hovered.v.toFixed(1) }}{{ unit }}</span>
      <span class="ml-2 text-muted-foreground">{{ new Date(hovered.t).toLocaleString() }}</span>
    </div>
  </div>
</template>

<style scoped>
/* the hover marker glides to the nearest reading instead of jumping */
.chart-hover { transition: transform 0.12s ease-out; }
.chart-dot { transition: cy 0.12s ease-out; }
</style>
