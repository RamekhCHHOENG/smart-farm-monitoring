<script setup lang="ts">
import type { Rule, Sensor } from "~/utils/types";
import { sensorIcons } from "~/utils/deviceIcons";

const props = defineProps<{ sensor: Sensor; rules?: Rule[] }>();

const icon = computed(() => sensorIcons[props.sensor.type]);

// A sensor a technician took out of service is not trusted: show it, but not as a live value.
const outOfService = computed(() => props.sensor.status === "FAULT" || props.sensor.status === "MAINTENANCE");
const dot = computed(() =>
  ({ ONLINE: "bg-primary", OFFLINE: "bg-destructive", FAULT: "bg-destructive", MAINTENANCE: "bg-warning" })[props.sensor.status],
);

/** Position of a value inside the sensor's valid range, for the gauge bar. */
function position(v: number) {
  const { minValid, maxValid } = props.sensor;
  return Math.min(100, Math.max(0, ((v - minValid) / (maxValid - minValid)) * 100));
}

const percent = computed(() => (props.sensor.latest ? position(props.sensor.latest.value) : 0));

// Active rules on this sensor, drawn as marks on the bar so staff see how close the value is.
const limits = computed(() => (props.rules ?? []).filter((r) => r.active && r.sensorId === props.sensor.id));
const breached = computed(() => {
  const v = props.sensor.latest?.value;
  return v !== undefined && limits.value.some((r) => (r.condition === "BELOW" ? v < r.threshold : v > r.threshold));
});
</script>

<template>
  <NuxtLink
    :to="{ path: '/history', query: { zone: sensor.zoneId, sensor: sensor.id } }"
    class="block rounded-lg border bg-background p-3 transition-colors hover:border-primary/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
    :class="{ 'border-destructive/50': breached && !outOfService, 'opacity-70': outOfService }"
    :title="t('Tap for history')"
  >
    <div class="flex items-start gap-2 text-xs text-muted-foreground">
      <component :is="icon" class="mt-px size-3.5 shrink-0" />
      <span class="line-clamp-2 leading-tight">{{ sensor.name }}</span>
      <span
        class="mt-0.5 ml-auto size-2 shrink-0 rounded-full"
        :class="dot"
        :title="t(sensor.status)"
      />
    </div>
    <div class="mt-1.5 text-2xl font-semibold tabular-nums" :class="{ 'text-destructive': breached && !outOfService, 'line-through decoration-1': sensor.status === 'FAULT' }">
      <template v-if="sensor.latest">{{ sensor.latest.value.toFixed(1) }}<span class="ml-0.5 text-sm font-normal text-muted-foreground">{{ sensor.unit }}</span></template>
      <span v-else class="text-base text-muted-foreground">{{ t("No data") }}</span>
    </div>
    <div class="relative mt-2 h-1.5 rounded-full bg-muted">
      <div class="h-full rounded-full transition-[width] duration-700" :class="breached && !outOfService ? 'bg-destructive' : 'bg-primary'" :style="{ width: `${percent}%` }" />
      <span
        v-for="r in limits" :key="r.id"
        class="absolute -top-1 h-3.5 w-0.5 rounded bg-foreground/70"
        :style="{ left: `calc(${position(r.threshold)}% - 1px)` }"
        :title="`${r.name}: ${r.condition === 'BELOW' ? '<' : '>'} ${r.threshold}${r.unit}`"
      />
    </div>
    <div class="mt-1.5 flex justify-between gap-2 text-[11px] text-muted-foreground">
      <span class="truncate">
        <template v-if="outOfService"><b class="font-semibold">{{ t(sensor.status) }}</b> · {{ t(sensor.status === "FAULT" ? "ignored" : "paused") }}</template>
        <template v-else-if="limits.length">{{ t("Rule") }} {{ limits.map((r) => `${r.condition === "BELOW" ? "<" : ">"} ${r.threshold}`).join(", ") }}</template>
        <template v-else>{{ t(sensor.simulated ? "Simulated" : "Device") }}</template>
        <template v-if="sensor.suspect"> · {{ t("suspect") }}</template>
      </span>
      <span class="shrink-0">{{ timeAgo(sensor.lastSeenAt) }}</span>
    </div>
  </NuxtLink>
</template>
