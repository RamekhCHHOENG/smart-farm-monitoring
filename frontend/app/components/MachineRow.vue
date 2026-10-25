<script setup lang="ts">
import type { Actuator } from "~/utils/types";
import type { Run } from "~/utils/analytics";
import { minutes } from "~/utils/analytics";

// A machine: its Start/Stop controls, then a 24-hour strip of when it ran, with the number of runs and minutes.
defineProps<{ actuator: Actuator; runs: Run[]; from: number; to: number; color: string }>();
const emit = defineEmits<{ changed: [] }>();
</script>

<template>
  <div class="transition-colors" :class="{ 'bg-primary/6': actuator.status === 'RUNNING' }">
    <ActuatorControl flat :actuator="actuator" @changed="emit('changed')" />
    <div class="flex items-center gap-4 px-5 pb-3 pl-[4.25rem]">
      <RunTimeline class="min-w-0 flex-1" :runs="runs" :from="from" :to="to" tone="" :style="{ '--tone': color }" />
      <div class="w-28 shrink-0 text-right text-xs text-muted-foreground">
        <span class="num text-sm text-foreground">{{ runs.length }}</span> {{ t("runs") }} ·
        <span class="num text-sm text-foreground">{{ Math.round(minutes(runs)) }}</span> min
      </div>
    </div>
  </div>
</template>

<style scoped>
/* timeline blocks take the machine's own colour */
:deep(.tl-block) { background: var(--tone, var(--primary)); }
</style>
