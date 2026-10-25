<script setup lang="ts">
import type { Component } from "vue";
import { ArrowDownRight, ArrowUpRight } from "lucide-vue-next";

// A figure card: icon disc, label, big number, an optional trend chip against yesterday, and a round ↗ button that
// opens the page behind the figure. `hero` paints it solid olive so the most important figure stands out.
const props = withDefaults(defineProps<{
  label: string;
  value: number | null;
  decimals?: number;
  unit?: string;
  /** "/10" after the number */
  of?: string;
  icon?: Component;
  to?: string;
  /** change against yesterday, in the figure's own unit */
  delta?: number | null;
  /** true when going up is bad (alerts, run time), so an increase is coloured as a warning */
  upIsBad?: boolean;
  note?: string;
  hero?: boolean;
  tone?: "" | "warning" | "destructive";
}>(), { decimals: 0, unit: "", of: "", delta: null, upIsBad: false, note: "", hero: false, tone: "" });

const trend = computed(() => {
  if (props.delta === null || props.delta === undefined || props.value === null) return null;
  const before = props.value - props.delta;
  const pct = before ? Math.round((props.delta / Math.abs(before)) * 100) : null;
  const bad = props.delta === 0 ? null : (props.delta > 0) === props.upIsBad;
  return { up: props.delta > 0, flat: props.delta === 0, pct, bad };
});
</script>

<template>
  <div class="relative flex flex-col gap-3 rounded-[1.4rem] border p-4 shadow-soft"
    :class="hero ? 'border-transparent bg-primary text-primary-foreground' : 'bg-card'">
    <div class="flex items-start gap-2.5">
      <span v-if="icon" class="flex size-9 shrink-0 items-center justify-center rounded-full"
        :class="hero ? 'bg-white/18 text-primary-foreground' : 'badge-disc'"><component :is="icon" class="size-4" /></span>
      <span class="min-w-0 flex-1 pt-1 text-sm leading-tight" :class="hero ? 'text-primary-foreground/85' : 'text-muted-foreground'">{{ label }}</span>
      <NuxtLink v-if="to" :to="to" :aria-label="`Open ${label}`"
        class="flex size-8 shrink-0 items-center justify-center rounded-full border transition-colors"
        :class="hero ? 'border-white/25 hover:bg-white/15' : 'bg-card hover:bg-accent'">
        <ArrowUpRight class="size-4" />
      </NuxtLink>
    </div>
    <div class="flex flex-wrap items-end gap-x-2 gap-y-1">
      <span class="num text-[2.1rem] leading-none"
        :class="{ 'text-warning': !hero && tone === 'warning', 'text-destructive': !hero && tone === 'destructive' }">
        <AnimatedNumber :value="value" :decimals="decimals" /><span class="text-lg opacity-60">{{ of }}</span><span v-if="unit" class="ml-0.5 font-sans text-sm opacity-70">{{ unit }}</span>
      </span>
      <span v-if="trend && !trend.flat" class="mb-0.5 inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 text-[11px] font-medium"
        :class="hero ? 'bg-white/20' : trend.bad ? 'bg-destructive/12 text-destructive' : 'bg-primary/12 text-primary'"
        :title="`${trend.up ? '+' : ''}${delta!.toFixed(decimals)}${unit} vs yesterday`">
        <component :is="trend.up ? ArrowUpRight : ArrowDownRight" class="size-3" />{{ trend.pct !== null ? `${Math.abs(trend.pct)}%` : Math.abs(delta!).toFixed(decimals) }}
      </span>
    </div>
    <p v-if="note || trend" class="text-xs" :class="hero ? 'text-primary-foreground/75' : 'text-muted-foreground'">
      {{ note ? t(note) : (trend?.flat ? t("same as yesterday") : `${t("vs")} ${(value! - delta!).toFixed(decimals)}${unit && unit !== "%" ? " " : ""}${unit} ${t("yesterday")}`) }}
    </p>
  </div>
</template>
