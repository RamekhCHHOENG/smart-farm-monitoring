<script setup lang="ts">
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-vue-next";
import type { Sensor } from "~/utils/types";
import type { SensorStats } from "~/utils/analytics";
import { sensorIcon } from "~/utils/deviceIcons";

// One sensor's last 24 hours: live value and its change in the last hour, the area chart with the rule limits,
// and the day's low / average / high / time in range. Opens the sensor's history.
withDefaults(defineProps<{
  sensor: Sensor;
  points: { t: number; v: number }[];
  limits: { value: number; label: string }[];
  stats: SensorStats | null;
  alarm?: boolean;
  chartHeight?: number;
}>(), { alarm: false, chartHeight: 110 });
</script>

<template>
  <NuxtLink :to="{ path: '/history', query: { zone: sensor.zoneId, sensor: sensor.id } }"
    class="lift flex flex-col gap-2 rounded-2xl border bg-card p-4" :class="{ 'opacity-60': sensor.status === 'FAULT' || sensor.status === 'MAINTENANCE' }"
    :title="t('Tap for history')">
    <div class="flex items-start gap-2.5">
      <span class="badge-disc size-9 shrink-0" :class="{ '!bg-destructive/15 !text-destructive': alarm }"><component :is="sensorIcon(sensor.type)" class="size-4" /></span>
      <div class="min-w-0 flex-1 leading-tight">
        <div class="truncate font-medium">{{ sensor.name }}</div>
        <div class="truncate text-xs text-muted-foreground"><span class="font-mono">#{{ sensor.id }}</span> · {{ sensor.measurementName ?? sensor.type }} · {{ timeAgo(sensor.lastSeenAt) }}</div>
      </div>
      <StatusBadge :status="sensor.status" />
    </div>
    <div class="flex flex-wrap items-end gap-x-4 gap-y-1">
      <div class="num text-4xl leading-none" :class="{ 'text-destructive': alarm, 'line-through decoration-1': sensor.status === 'FAULT' }">
        <template v-if="sensor.latest"><AnimatedNumber :value="sensor.latest.value" :decimals="1" :duration="700" /><span class="ml-1 font-sans text-sm text-muted-foreground">{{ sensor.unit }}</span></template>
        <span v-else class="font-sans text-base text-muted-foreground">{{ t("No data") }}</span>
      </div>
      <div v-if="stats?.delta != null" class="flex items-center gap-0.5 pb-0.5 text-xs text-muted-foreground" :title="t('Change in the last hour')">
        <component :is="Math.abs(stats.delta) < 0.05 ? Minus : stats.delta > 0 ? ArrowUpRight : ArrowDownRight" class="size-3.5" />
        {{ stats.delta > 0 ? "+" : "" }}{{ stats.delta.toFixed(1) }} {{ t("in 1 h") }}
      </div>
      <div v-if="limits.length" class="ml-auto pb-0.5 text-xs text-muted-foreground">{{ t("Rule") }} {{ limits.map((l) => l.label).join(", ") }}</div>
    </div>
    <AreaChart :points="points" :unit="sensor.unit" :limits="limits" :alarm="alarm" :height="chartHeight" />
    <dl v-if="stats" class="grid grid-cols-4 gap-1 border-t pt-2 text-center text-[11px] text-muted-foreground">
      <div><dt>{{ t("Low") }}</dt><dd class="num text-sm text-foreground">{{ stats.min.toFixed(1) }}</dd></div>
      <div><dt>{{ t("Average") }}</dt><dd class="num text-sm text-foreground">{{ stats.avg.toFixed(1) }}</dd></div>
      <div><dt>{{ t("High") }}</dt><dd class="num text-sm text-foreground">{{ stats.max.toFixed(1) }}</dd></div>
      <div :title="t('Share of the day inside the rule limits')"><dt>{{ t("In range") }}</dt>
        <dd class="num text-sm" :class="stats.inRange === null ? 'text-muted-foreground' : stats.inRange >= 90 ? 'text-primary' : stats.inRange >= 70 ? 'text-warning' : 'text-destructive'">
          {{ stats.inRange === null ? "–" : `${stats.inRange}%` }}</dd></div>
    </dl>
  </NuxtLink>
</template>
