<script setup lang="ts">
import { ArrowUpRight } from "lucide-vue-next";
import type { Alert, Zone } from "~/utils/types";
import { alertNow, alertParts, alertTag } from "~/utils/alertParts";

// The open alerts as a short list, each with a coloured tag saying what kind of problem it is (Low, High, Fault…).
const props = withDefaults(defineProps<{ alerts: Alert[]; zones: Zone[]; limit?: number }>(), { limit: 5 });

const open = computed(() => props.alerts.filter((a) => a.status === "OPEN")
  .sort((a, b) => (a.severity === b.severity ? b.lastRaisedAt.localeCompare(a.lastRaisedAt) : a.severity === "CRITICAL" ? -1 : b.severity === "CRITICAL" ? 1 : 0)));
const zoneName = (id: number | null) => props.zones.find((z) => z.id === id)?.name ?? null;

const tag = alertTag;
const nowOf = (a: Alert) => alertNow(a, props.zones);
</script>

<template>
  <section class="flex flex-col rounded-[1.75rem] border bg-card p-5 shadow-soft">
    <div class="flex items-center gap-2">
      <h3 class="font-medium">{{ t("Alerts summary") }}</h3>
      <span class="num rounded-full bg-muted px-2 text-xs leading-5">{{ open.length }}</span>
      <NuxtLink to="/alerts" :aria-label="t('Alerts')" class="ml-auto flex size-8 items-center justify-center rounded-full border hover:bg-accent"><ArrowUpRight class="size-4" /></NuxtLink>
    </div>
    <ul class="mt-3 flex flex-col gap-2">
      <li v-for="a in open.slice(0, limit)" :key="a.id" class="rise flex items-start gap-3 rounded-2xl bg-muted/60 px-3.5 py-2.5">
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium">{{ alertParts(a).subject ?? alertParts(a).problem }}
            <span v-if="alertParts(a).value" class="num font-normal text-muted-foreground"> · {{ alertParts(a).value }}</span>
            <span v-if="nowOf(a)" class="num text-xs font-normal" :class="nowOf(a)!.recovered ? 'text-primary' : 'text-destructive'"> → now {{ nowOf(a)!.value.toFixed(1) }}{{ nowOf(a)!.unit }}<template v-if="nowOf(a)!.recovered"> ✓</template></span></div>
          <div class="truncate text-xs text-muted-foreground">{{ alertParts(a).zone ?? zoneName(a.zoneId) ?? t("Farm") }} · {{ timeAgo(a.lastRaisedAt) }}<template v-if="a.repeatCount > 1"> · ×{{ a.repeatCount }}</template></div>
        </div>
        <span class="shrink-0 rounded-full px-2.5 py-0.5 text-[11px] font-medium" :class="tag(a).tone">{{ tag(a).label }}</span>
      </li>
      <li v-if="!open.length" class="rounded-2xl bg-primary/8 px-3.5 py-3 text-sm text-primary">{{ t("No open alerts. All quiet.") }}</li>
    </ul>
    <NuxtLink v-if="open.length > limit" to="/alerts" class="mt-2 text-center text-xs text-muted-foreground hover:text-foreground">
      + {{ open.length - limit }} {{ t("more") }}
    </NuxtLink>
  </section>
</template>
