<script setup lang="ts">
import { ArrowRight } from "lucide-vue-next";
import { Card } from "@/components/ui/card";
import type { DeviceLog, Rule, Zone } from "~/utils/types";

// A zone on the dashboard, short enough that several fit on one screen: a line of today's figures, one row per
// sensor (value, rule, 24 h trend) and per machine (Start/Stop). "Open zone" leads to everything about it.
const props = defineProps<{
  zone: Zone;
  rules: Rule[];
  readings: Record<number, { t: number; v: number }[]>;
  logs: DeviceLog[];
  criticalOpen: boolean;
}>();
const emit = defineEmits<{ changed: [] }>();

const f = useZoneFigures({ zone: () => props.zone, rules: () => props.rules, readings: () => props.readings,
  logs: () => props.logs, criticalOpen: () => props.criticalOpen });
const look = computed(() => zoneLook(props.zone));
const inRange = computed(() => {
  const vs = props.zone.sensors.map((s) => f.statsOf(s.id)?.inRange).filter((v): v is number => v !== null && v !== undefined);
  return vs.length ? Math.round(vs.reduce((a, b) => a + b, 0) / vs.length) : null;
});
const levelTone = (l?: string) => ({ Low: "bg-primary/12 text-primary", Medium: "bg-warning/18 text-foreground", High: "bg-destructive/12 text-destructive" })[l ?? "Low"];
</script>

<template>
  <Card :id="`zone-${zone.id}`" class="scroll-mt-20 gap-0 overflow-hidden py-0">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2 px-5 pt-4 pb-3">
      <span class="size-2.5 rounded-full" :class="look.dot" />
      <h3 class="text-lg">{{ zone.name }}</h3>
      <span class="rounded-full bg-muted px-2.5 py-0.5 text-xs" :class="look.text">{{ t(zone.type) }}</span>
      <span v-if="zone.lab" class="rounded-full bg-primary/12 px-2.5 py-0.5 text-xs text-primary">LAB</span>
      <span v-if="f.health.value" class="rounded-full px-2.5 py-0.5 text-xs" :class="levelTone(f.health.value.level)">
        {{ t("Stress") }} <span class="num">{{ f.health.value.stress }}</span></span>
      <NuxtLink :to="`/zone/${zone.id}`" class="ml-auto inline-flex h-8 items-center gap-1.5 rounded-full bg-ink px-3.5 text-sm text-ink-foreground transition-transform hover:bg-ink/85 active:scale-[0.97]">
        {{ t("Open zone") }} <ArrowRight class="size-4" />
      </NuxtLink>
    </div>
    <!-- today in one line -->
    <div class="grid grid-cols-4 border-y bg-muted/40 text-center">
      <div class="py-2"><div class="num text-lg leading-tight"><AnimatedNumber :value="f.kpis.value.online" /><span class="text-sm text-muted-foreground">/{{ f.kpis.value.sensors }}</span></div><div class="text-[11px] text-muted-foreground">{{ t("online") }}</div></div>
      <div class="border-l py-2"><div class="num text-lg leading-tight"><AnimatedNumber :value="inRange" /><span v-if="inRange !== null" class="text-sm text-muted-foreground">%</span></div><div class="text-[11px] text-muted-foreground">{{ t("in range, 24 h") }}</div></div>
      <div class="border-l py-2"><div class="num text-lg leading-tight"><AnimatedNumber :value="Math.round(f.kpis.value.runMin)" /><span class="ml-0.5 font-sans text-xs text-muted-foreground">min</span></div><div class="text-[11px] text-muted-foreground">{{ t("run today") }}</div></div>
      <div class="border-l py-2"><div class="num text-lg leading-tight"><AnimatedNumber :value="f.kpis.value.starts" /></div><div class="text-[11px] text-muted-foreground">{{ t("starts today") }}</div></div>
    </div>
    <div class="divide-y">
      <SensorTile v-for="s in zone.sensors" :key="s.id" compact :sensor="s" :rules="rules" :trend="(readings[s.id] ?? []).map((p) => p.v)" />
    </div>
    <p v-if="zone.sensors.length === 0" class="px-5 py-3 text-sm text-muted-foreground">{{ t("No sensors yet.") }}</p>
    <div v-if="f.actuators.value.length" class="divide-y border-t">
      <ActuatorControl v-for="a in f.actuators.value" :key="a.id" :actuator="a" @changed="emit('changed')" />
    </div>
  </Card>
</template>
