<script setup lang="ts">
import type { Rule, Sensor } from "~/utils/types";
import { sensorIcon } from "~/utils/deviceIcons";

// One sensor as a gauge row: name, live value, the valid range with the rules' limits marked, and the last hour.
const props = withDefaults(defineProps<{ sensor: Sensor; rules?: Rule[]; trend?: number[]; compact?: boolean }>(), { compact: false });

const icon = computed(() => sensorIcon(props.sensor.type));

// A sensor a technician took out of service is not trusted: show it, but not as a live value.
const outOfService = computed(() => props.sensor.status === "FAULT" || props.sensor.status === "MAINTENANCE");

/** Position of a value inside the sensor's valid range, for the gauge bar. */
function position(v: number) {
  const { minValid, maxValid } = props.sensor;
  return Math.min(100, Math.max(0, ((v - minValid) / (maxValid - minValid)) * 100));
}

// the bar grows from empty when the row first shows, then follows each reading
const shown = ref(false);
onMounted(() => requestAnimationFrame(() => (shown.value = true)));
const percent = computed(() => (shown.value && props.sensor.latest ? position(props.sensor.latest.value) : 0));

// Active rules on this sensor, drawn as marks on the bar so staff see how close the value is.
const limits = computed(() => (props.rules ?? []).filter((r) => r.active && r.sensorId === props.sensor.id));
const breached = computed(() => {
  const v = props.sensor.latest?.value;
  return v !== undefined && limits.value.some((r) => (r.condition === "BELOW" ? v < r.threshold : v > r.threshold));
});
const alarm = computed(() => breached.value && !outOfService.value);
</script>

<template>
  <NuxtLink
    :to="{ path: '/history', query: { zone: sensor.zoneId, sensor: sensor.id } }"
    class="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1.5 px-5 py-3 transition-colors hover:bg-accent/40 focus-visible:bg-accent/40 focus-visible:outline-none"
    :class="[compact ? 'sm:grid-cols-[minmax(0,1fr)_auto_8rem]' : 'sm:grid-cols-[minmax(0,12rem)_6.5rem_minmax(0,1fr)_auto]', { 'opacity-60': outOfService }]"
    :title="t('Tap for history')"
  >
    <div class="flex min-w-0 items-center gap-2.5">
      <component :is="icon" class="size-4 shrink-0" :class="alarm ? 'text-destructive' : 'text-muted-foreground'" />
      <div class="min-w-0">
        <div class="truncate text-sm font-medium">{{ sensor.name }}</div>
        <div class="flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <span class="font-mono">#{{ sensor.id }}</span>
          <span>·</span>
          <span class="truncate">
            <template v-if="outOfService">{{ t(sensor.status) }} · {{ t(sensor.status === "FAULT" ? "ignored" : "paused") }}</template>
            <template v-else-if="sensor.status === 'OFFLINE'">{{ t("OFFLINE") }}</template>
            <template v-else-if="compact && limits.length">{{ t("Rule") }} {{ limits.map((r) => `${r.condition === "BELOW" ? "<" : ">"} ${r.threshold}`).join(", ") }}</template>
            <template v-else>{{ t(sensor.simulated ? "Simulated" : "Device") }}</template>
            <template v-if="sensor.suspect"> · {{ t("suspect") }}</template>
          </span>
        </div>
      </div>
    </div>

    <div class="num text-right text-2xl leading-none font-semibold sm:text-left"
      :class="{ 'text-destructive': alarm, 'line-through decoration-1': sensor.status === 'FAULT' }">
      <template v-if="sensor.latest"><AnimatedNumber :value="sensor.latest.value" :decimals="1" :duration="700" /><span class="ml-1 font-sans text-xs font-normal text-muted-foreground">{{ sensor.unit }}</span></template>
      <span v-else class="font-sans text-sm font-normal text-muted-foreground">{{ t("No data") }}</span>
    </div>

    <div v-if="!compact" class="col-span-2 min-w-0 sm:col-span-1">
      <div class="relative h-1.5 rounded-sm bg-muted">
        <div class="h-full rounded-sm transition-[width] duration-700" :class="alarm ? 'bg-destructive' : 'bg-primary'" :style="{ width: `${percent}%` }" />
        <span
          v-for="r in limits" :key="r.id"
          class="absolute -top-1 h-3.5 w-0.5 bg-foreground/70"
          :style="{ left: `calc(${position(r.threshold)}% - 1px)` }"
          :title="`${r.name}: ${r.condition === 'BELOW' ? '<' : '>'} ${r.threshold}${r.unit}`"
        />
      </div>
      <div class="mt-1 flex justify-between gap-2 font-mono text-[10px] text-muted-foreground">
        <span>{{ sensor.minValid }}</span>
        <span v-if="limits.length" class="truncate">{{ t("Rule") }} {{ limits.map((r) => `${r.condition === "BELOW" ? "<" : ">"} ${r.threshold}`).join(", ") }}</span>
        <span>{{ sensor.maxValid }}</span>
      </div>
    </div>

    <div class="hidden flex-col items-end gap-0.5 sm:flex">
      <Sparkline :class="{ 'w-32': compact }" :values="trend ?? []" :min="sensor.minValid" :max="sensor.maxValid" :alarm="alarm" />
      <span class="text-[10px] text-muted-foreground">{{ timeAgo(sensor.lastSeenAt) }}</span>
    </div>
  </NuxtLink>
</template>
