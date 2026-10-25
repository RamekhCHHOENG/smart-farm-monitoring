<script setup lang="ts">
import { ArrowRight, Check, CircleCheck, CornerDownRight } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import type { Alert } from "~/utils/types";
import type { AlertParts } from "~/utils/alertParts";

// One alert laid out in parts: zone and time, the sensor and its value against the limit, then what the farm did.
defineProps<{ alert: Alert; parts: AlertParts; busy?: boolean; canAck?: boolean; flat?: boolean; view: string | Record<string, unknown>;
  /** the sensor's reading right now, and whether it is back inside the limit */
  now?: { value: number; unit: string; recovered: boolean } | null }>();
defineEmits<{ acknowledge: []; view: [] }>();
</script>

<template>
  <div class="flex flex-col gap-2 p-3.5" :class="flat ? '' : 'rounded-xl border bg-card'">
    <div class="flex items-center gap-2 text-xs text-muted-foreground">
      <span class="truncate">{{ parts.zone ?? t("Farm") }}</span>
      <span class="ml-auto shrink-0">{{ timeAgo(alert.lastRaisedAt) }}<template v-if="alert.repeatCount > 1"> · ×{{ alert.repeatCount }}</template></span>
    </div>
    <template v-if="parts.value">
      <div class="text-sm font-medium">{{ parts.subject }}</div>
      <div class="flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span class="num text-3xl leading-none" :class="now?.recovered ? 'text-muted-foreground line-through decoration-1' : 'text-destructive'">{{ parts.value }}</span>
        <span class="text-sm text-muted-foreground">{{ parts.limit }}</span>
        <span v-if="now" class="ml-auto inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs"
          :class="now.recovered ? 'bg-primary/12 text-primary' : 'bg-destructive/12 text-destructive'">
          <CircleCheck v-if="now.recovered" class="size-3.5" />
          {{ t("now") }} <b class="num font-medium">{{ now.value.toFixed(1) }}{{ now.unit }}</b><template v-if="now.recovered"> · {{ t("back in range") }}</template>
        </span>
      </div>
    </template>
    <div v-else class="text-sm font-medium">{{ parts.problem }}</div>
    <div v-if="parts.action" class="flex items-start gap-1.5 rounded-lg bg-muted px-2.5 py-1.5 text-xs">
      <CornerDownRight class="mt-px size-3.5 shrink-0 text-primary" />
      <span><b v-if="parts.actionLabel" class="font-medium">{{ parts.actionLabel }}:</b> {{ parts.action }}</span>
    </div>
    <div class="flex gap-2 pt-0.5">
      <Button v-if="canAck" size="sm" :disabled="busy" @click="$emit('acknowledge')"><Check /> {{ t("Acknowledge") }}</Button>
      <Button size="sm" variant="outline" as-child @click="$emit('view')"><NuxtLink :to="view">{{ t("View") }} <ArrowRight /></NuxtLink></Button>
    </div>
  </div>
</template>
