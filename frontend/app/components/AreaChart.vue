<script setup lang="ts">
import { useResizeObserver } from "@vueuse/core";

// A compact area chart for one sensor: the readings, the rule limits as dashed lines, the band between two
// limits shaded as "good", and a tooltip under the pointer. It draws itself in from the left.
const props = withDefaults(defineProps<{
  points: { t: number; v: number }[];
  unit: string;
  limits?: { value: number; label: string }[];
  height?: number;
  alarm?: boolean;
}>(), { limits: () => [], height: 120, alarm: false });

const wrap = ref<HTMLElement | null>(null);
const W = ref(360);
useResizeObserver(wrap, (e) => {
  const w = e[0]?.contentRect.width;
  if (w) W.value = Math.max(160, Math.round(w));
});
const PAD = { l: 30, r: 6, t: 8, b: 18 };
const H = computed(() => props.height);
const uid = useId();

const domain = computed(() => {
  const vs = [...props.points.map((p) => p.v), ...props.limits.map((l) => l.value)];
  let lo = Math.min(...vs);
  let hi = Math.max(...vs);
  if (!isFinite(lo)) return { lo: 0, hi: 1, t0: 0, t1: 1 };
  if (hi - lo < 1) { lo -= 1; hi += 1; }
  const pad = (hi - lo) * 0.12;
  const ts = props.points.map((p) => p.t);
  return { lo: lo - pad, hi: hi + pad, t0: Math.min(...ts), t1: Math.max(Math.max(...ts), Math.min(...ts) + 1) };
});
const x = (t: number) => PAD.l + ((t - domain.value.t0) / (domain.value.t1 - domain.value.t0)) * (W.value - PAD.l - PAD.r);
const y = (v: number) => PAD.t + (1 - (v - domain.value.lo) / (domain.value.hi - domain.value.lo)) * (H.value - PAD.t - PAD.b);

// Dense data (a day of readings) is averaged into ~72 time slots: the line is the average of each slot and a
// faint band shows its lowest to highest reading, so the chart reads as a trend instead of a scribble.
const slots = computed(() => {
  const p = props.points;
  if (p.length <= 90) return null;
  const n = 72;
  const { t0, t1 } = domain.value;
  const size = (t1 - t0) / n;
  const out: { t: number; v: number; lo: number; hi: number }[] = [];
  for (let i = 0; i < n; i++) {
    const inSlot = p.filter((q) => q.t >= t0 + i * size && (i === n - 1 ? q.t <= t1 : q.t < t0 + (i + 1) * size));
    if (!inSlot.length) continue;
    const vs = inSlot.map((q) => q.v);
    out.push({ t: t0 + (i + 0.5) * size, v: vs.reduce((a, b) => a + b, 0) / vs.length, lo: Math.min(...vs), hi: Math.max(...vs) });
  }
  return out;
});
const series = computed(() => slots.value ?? props.points);
const envelope = computed(() => {
  const s = slots.value;
  if (!s || s.length < 2) return "";
  const top = s.map((q, i) => `${i ? "L" : "M"}${x(q.t).toFixed(1)},${y(q.hi).toFixed(1)}`).join("");
  const bottom = [...s].reverse().map((q) => `L${x(q.t).toFixed(1)},${y(q.lo).toFixed(1)}`).join("");
  return `${top}${bottom}Z`;
});
const line = computed(() => series.value.map((p, i) => `${i ? "L" : "M"}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join(""));
const area = computed(() => {
  const p = series.value;
  if (p.length < 2) return "";
  const base = (H.value - PAD.b).toFixed(1);
  return `${line.value}L${x(p[p.length - 1]!.t).toFixed(1)},${base}L${x(p[0]!.t).toFixed(1)},${base}Z`;
});
// with a lower and an upper limit, the space between them is the good band
const band = computed(() => {
  if (props.limits.length < 2) return null;
  const vs = props.limits.map((l) => l.value);
  return { top: y(Math.max(...vs)), bottom: y(Math.min(...vs)) };
});
const ticks = computed(() => {
  const { lo, hi } = domain.value;
  return [lo + (hi - lo) * 0.15, (lo + hi) / 2, hi - (hi - lo) * 0.15];
});
const timeTicks = computed(() => {
  const { t0, t1 } = domain.value;
  return [t0, (t0 + t1) / 2, t1];
});
const fmtTime = (t: number) => new Date(t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
const drawKey = computed(() => `${props.points.length}:${props.points[0]?.t ?? 0}`);

const hover = ref<number | null>(null);
function onMove(e: MouseEvent) {
  const r = (e.currentTarget as SVGSVGElement).getBoundingClientRect();
  const px = ((e.clientX - r.left) / r.width) * W.value;
  let best = 0;
  series.value.forEach((p, i) => {
    if (Math.abs(x(p.t) - px) < Math.abs(x(series.value[best]!.t) - px)) best = i;
  });
  hover.value = series.value.length ? best : null;
}
const hovered = computed(() => (hover.value === null ? null : (series.value[hover.value] as { t: number; v: number; lo?: number; hi?: number }) ?? null));
const stroke = computed(() => (props.alarm ? "var(--destructive)" : "var(--primary)"));
</script>

<template>
  <div ref="wrap" class="relative">
    <svg v-if="points.length > 1" :viewBox="`0 0 ${W} ${H}`" class="block h-auto w-full" role="img"
      :aria-label="`Last 24 hours in ${unit}`" @mousemove="onMove" @mouseleave="hover = null">
      <defs>
        <linearGradient :id="`f${uid}`" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" :stop-color="stroke" stop-opacity="0.26" />
          <stop offset="1" :stop-color="stroke" stop-opacity="0" />
        </linearGradient>
      </defs>
      <rect v-if="band" :x="PAD.l" :width="W - PAD.l - PAD.r" :y="band.top" :height="band.bottom - band.top" fill="var(--primary)" opacity="0.06" />
      <g font-size="9.5" fill="var(--muted-foreground)">
        <g v-for="v in ticks" :key="`y${v}`">
          <line :x1="PAD.l" :x2="W - PAD.r" :y1="y(v)" :y2="y(v)" stroke="var(--border)" stroke-dasharray="2 4" />
          <text :x="PAD.l - 5" :y="y(v) + 3" text-anchor="end">{{ v.toFixed(0) }}</text>
        </g>
        <text v-for="(tt, i) in timeTicks" :key="`t${i}`" :x="x(tt)" :y="H - 4" :text-anchor="i === 0 ? 'start' : i === 2 ? 'end' : 'middle'">{{ fmtTime(tt) }}</text>
      </g>
      <g v-for="l in limits" :key="l.label">
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="y(l.value)" :y2="y(l.value)" stroke="var(--destructive)" stroke-opacity="0.7" stroke-dasharray="4 3" />
        <text :x="W - PAD.r - 2" :y="y(l.value) - 3" text-anchor="end" font-size="9.5" fill="var(--destructive)">{{ l.label }}</text>
      </g>
      <path :key="`a${drawKey}`" :d="area" :fill="`url(#f${uid})`" class="fade-in" style="animation-delay: 0.3s" />
      <path v-if="envelope" :key="`e${drawKey}`" :d="envelope" :fill="stroke" fill-opacity="0.13" class="fade-in" style="animation-delay: 0.45s" />
      <path :key="`l${drawKey}`" :d="line" pathLength="1" fill="none" :stroke="stroke" stroke-width="1.75"
        stroke-linejoin="round" stroke-linecap="round" class="draw" />
      <g v-if="hovered" class="hover-mark" :style="{ transform: `translateX(${x(hovered.t)}px)` }">
        <line x1="0" x2="0" :y1="PAD.t" :y2="H - PAD.b" stroke="var(--muted-foreground)" stroke-opacity="0.35" />
        <circle cx="0" :cy="y(hovered.v)" r="3.5" :fill="stroke" stroke="var(--card)" stroke-width="1.5" />
      </g>
    </svg>
    <div v-else class="flex items-center justify-center text-xs text-muted-foreground" :style="{ height: `${height}px` }">
      Not enough readings yet
    </div>
    <div v-if="hovered" class="pointer-events-none absolute top-0 right-1 rounded-lg border bg-popover px-2 py-0.5 text-[11px] shadow-soft">
      <b class="num font-medium">{{ hovered.v.toFixed(1) }}{{ unit }}</b>
      <span v-if="hovered.lo !== undefined" class="text-muted-foreground"> ({{ hovered.lo.toFixed(1) }}–{{ hovered.hi!.toFixed(1) }})</span>
      <span class="text-muted-foreground"> {{ fmtTime(hovered.t) }}</span>
    </div>
  </div>
</template>

<style scoped>
.hover-mark { transition: transform 0.1s ease-out; }
</style>
