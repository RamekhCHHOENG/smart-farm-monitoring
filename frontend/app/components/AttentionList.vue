<script setup lang="ts">
import { ArrowUpRight, TriangleAlert } from "lucide-vue-next";
import type { Zone } from "~/utils/types";
import { actuatorIcon, sensorIcon } from "~/utils/deviceIcons";

// Every sensor and actuator that is not working normally, with how long it has been that way.
const props = defineProps<{ zones: Zone[] }>();

const items = computed(() => props.zones.flatMap((z) => [
  ...z.sensors.filter((s) => s.status !== "ONLINE").map((s) => ({
    key: `s${s.id}`, id: s.id, name: s.name, zone: z.name, kind: "Sensor", icon: sensorIcon(s.type), status: s.status,
    why: s.status === "OFFLINE" ? `No signal since ${s.lastSeenAt ? clock(s.lastSeenAt) : "it was added"}`
      : s.status === "FAULT" ? "Fault reported: its readings are ignored"
      : `Under repair since ${s.repairStartedAt ? clock(s.repairStartedAt) : "today"}`,
  })),
  ...z.actuators.filter((a) => a.status !== "IDLE" && a.status !== "RUNNING" && a.status !== "DECOMMISSIONED").map((a) => ({
    key: `a${a.id}`, id: a.id, name: a.name, zone: z.name, kind: "Actuator", icon: actuatorIcon(a.type), status: a.status,
    why: a.status === "FAULT" ? "Fault reported: it will not start" : a.status === "OFFLINE" ? "Offline: it does not answer" : "Being repaired",
  })),
]));
function clock(iso: string) {
  const d = new Date(iso);
  const sameDay = d.toDateString() === new Date().toDateString();
  return sameDay ? d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : d.toLocaleDateString([], { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}
</script>

<template>
  <section class="flex flex-col rounded-[1.75rem] border bg-card p-5 shadow-soft">
    <div class="flex items-center gap-2">
      <h3 class="font-medium">{{ t("Devices needing attention") }}</h3>
      <span class="num rounded-full bg-muted px-2 text-xs leading-5">{{ items.length }}</span>
      <NuxtLink to="/devices?status=ATTENTION" :aria-label="t('Devices')" class="ml-auto flex size-8 items-center justify-center rounded-full border hover:bg-accent"><ArrowUpRight class="size-4" /></NuxtLink>
    </div>
    <ul class="mt-3 flex flex-col divide-y">
      <li v-for="d in items.slice(0, 6)" :key="d.key" class="rise flex items-center gap-3 py-2.5">
        <span class="badge-disc size-9 shrink-0"><component :is="d.icon" class="size-4" /></span>
        <div class="min-w-0 flex-1 leading-tight">
          <div class="truncate text-sm font-medium">{{ d.name }}</div>
          <div class="truncate text-xs text-muted-foreground"><span class="font-mono">#{{ d.id }}</span> · {{ d.kind }} · {{ d.zone }}</div>
          <div class="mt-0.5 flex items-center gap-1 truncate text-xs text-warning"><TriangleAlert class="size-3 shrink-0" /> {{ d.why }}</div>
        </div>
        <StatusBadge :status="d.status" />
      </li>
      <li v-if="!items.length" class="py-3 text-sm text-primary">{{ t("Every device is working.") }}</li>
    </ul>
  </section>
</template>
