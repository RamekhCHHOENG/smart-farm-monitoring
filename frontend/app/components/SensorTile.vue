<script setup lang="ts">
import { Droplets, Thermometer, Wheat } from "lucide-vue-next";
import type { Sensor } from "~/utils/types";

const props = defineProps<{ sensor: Sensor }>();

const icon = computed(() =>
  ({ SOIL_MOISTURE: Droplets, TEMPERATURE: Thermometer, FEED_LEVEL: Wheat })[props.sensor.type],
);

/** Position of the value inside the sensor's valid range, for the gauge bar. */
const percent = computed(() => {
  const v = props.sensor.latest?.value;
  if (v === undefined) return 0;
  const { minValid, maxValid } = props.sensor;
  return Math.min(100, Math.max(0, ((v - minValid) / (maxValid - minValid)) * 100));
});
</script>

<template>
  <div class="rounded-lg border bg-background p-3">
    <div class="flex items-center gap-2 text-xs text-muted-foreground">
      <component :is="icon" class="size-3.5" />
      <span class="truncate">{{ sensor.name }}</span>
      <span
        class="ml-auto size-2 shrink-0 rounded-full"
        :class="sensor.online ? 'bg-primary' : 'bg-destructive'"
        :title="sensor.online ? 'Online' : 'Offline'"
      />
    </div>
    <div class="mt-1.5 text-2xl font-semibold tabular-nums">
      <template v-if="sensor.latest">{{ sensor.latest.value.toFixed(1) }}<span class="ml-0.5 text-sm font-normal text-muted-foreground">{{ sensor.unit }}</span></template>
      <span v-else class="text-base text-muted-foreground">No data</span>
    </div>
    <div class="mt-2 h-1.5 overflow-hidden rounded-full bg-muted">
      <div class="h-full rounded-full bg-primary transition-[width] duration-700" :style="{ width: `${percent}%` }" />
    </div>
    <div class="mt-1.5 flex justify-between text-[11px] text-muted-foreground">
      <span>{{ sensor.simulated ? "Simulated" : "Device" }}<template v-if="sensor.suspect"> · suspect</template></span>
      <span>{{ timeAgo(sensor.lastSeenAt) }}</span>
    </div>
  </div>
</template>
