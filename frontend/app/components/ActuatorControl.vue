<script setup lang="ts">
import { Fan, Power, PowerOff, Utensils, Waves } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { Actuator } from "~/utils/types";

const props = defineProps<{ actuator: Actuator }>();
const emit = defineEmits<{ changed: []; error: [message: string] }>();

const api = useApi();
const { can } = useAuth();
const minutes = ref(Math.min(5, props.actuator.maxRunMinutes));
const busy = ref(false);

const icon = computed(() => ({ WATER_PUMP: Waves, FAN: Fan, FEEDER: Utensils })[props.actuator.type]);

async function run(path: "on" | "off") {
  busy.value = true;
  try {
    await api(`/api/actuators/${props.actuator.id}/${path}`, {
      method: "POST",
      body: path === "on" ? { durationMin: minutes.value } : undefined,
    });
    emit("changed");
  } catch (e) {
    emit("error", errorMessage(e));
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border bg-background p-3">
    <div class="flex min-w-[10rem] flex-1 items-center gap-3">
      <component
        :is="icon"
        class="size-4 shrink-0 text-muted-foreground"
        :class="{ 'animate-spin text-primary': actuator.status === 'RUNNING' && actuator.type === 'FAN' }"
      />
      <div class="min-w-0 flex-1">
        <div class="text-sm font-medium">{{ actuator.name }}</div>
        <div class="text-xs text-muted-foreground">
          <template v-if="actuator.status === 'RUNNING'">{{ minutesLeft(actuator.runningUntil) }}</template>
          <template v-else>max {{ actuator.maxRunMinutes }} min</template>
        </div>
      </div>
      <StatusBadge :status="actuator.status" />
    </div>
    <template v-if="can('WORKER')">
      <Button v-if="actuator.status === 'RUNNING'" size="sm" variant="outline" :disabled="busy" @click="run('off')">
        <PowerOff /> Stop
      </Button>
      <div v-else-if="actuator.status === 'IDLE'" class="flex items-center gap-1.5">
        <Input
          v-model.number="minutes"
          type="number"
          min="1"
          :max="actuator.maxRunMinutes"
          class="h-8 w-16"
          :aria-label="`Minutes to run ${actuator.name}`"
        />
        <Button size="sm" :disabled="busy" @click="run('on')"><Power /> Start</Button>
      </div>
    </template>
  </div>
</template>
