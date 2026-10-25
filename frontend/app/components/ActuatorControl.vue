<script setup lang="ts">
import { Minus, Plus, Power, PowerOff } from "lucide-vue-next";
import { actuatorIcon } from "~/utils/deviceIcons";
import { Button } from "@/components/ui/button";
import type { Actuator } from "~/utils/types";

// `flat`: the parent row draws the running highlight itself (a machine row with its timeline underneath)
const props = withDefaults(defineProps<{ actuator: Actuator; flat?: boolean }>(), { flat: false });
const emit = defineEmits<{ changed: [] }>();

const api = useApi();
const { can } = useAuth();
const minutes = ref(Math.min(5, props.actuator.maxRunMinutes));
const busy = ref(false);
// Shown right under the controls, where the person just clicked
const message = ref("");

const icon = computed(() => actuatorIcon(props.actuator.type));

async function run(path: "on" | "off") {
  if (path === "on") {
    const m = Number(minutes.value);
    if (!Number.isInteger(m) || m < 1 || m > props.actuator.maxRunMinutes) {
      message.value = t("Enter whole minutes from 1 to {n}.", { n: props.actuator.maxRunMinutes });
      return;
    }
  }
  if (busy.value) return; // a double click sends one request
  message.value = "";
  busy.value = true;
  try {
    await api(`/api/actuators/${props.actuator.id}/${path}`, {
      method: "POST",
      body: path === "on" ? { durationMin: minutes.value } : undefined,
    });
    emit("changed");
  } catch (e) {
    message.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-x-3 gap-y-2 px-5 py-2.5" :class="{ 'bg-primary/6': actuator.status === 'RUNNING' && !flat }">
    <div class="flex min-w-[11rem] flex-1 items-center gap-3">
      <span class="flex size-9 shrink-0 items-center justify-center rounded-full transition-colors"
        :class="actuator.status === 'RUNNING' ? 'bg-primary text-primary-foreground' : 'badge-disc'">
        <component :is="icon" class="size-4"
          :class="{ 'animate-spin [animation-duration:2.5s]': actuator.status === 'RUNNING' && actuator.type === 'FAN', 'animate-pulse': actuator.status === 'RUNNING' && actuator.type !== 'FAN' }" />
      </span>
      <div class="min-w-0 flex-1 leading-tight">
        <div class="truncate text-sm font-medium">{{ actuator.name }} <span class="font-mono text-[11px] font-normal text-muted-foreground">#{{ actuator.id }}</span></div>
        <div class="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
          <StatusBadge :status="actuator.status" class="!px-1.5 !py-0 !text-[10px]" />
          <span class="num truncate">
            <template v-if="actuator.status === 'RUNNING'">{{ minutesLeft(actuator.runningUntil) }}<template v-if="actuator.startedByRuleId"> · {{ t("by a rule") }}</template></template>
            <template v-else>{{ t("max {n} min", { n: actuator.maxRunMinutes }) }}</template>
          </span>
        </div>
      </div>
    </div>
    <template v-if="can('WORKER')">
      <Button v-if="actuator.status === 'RUNNING'" size="sm" variant="outline" class="h-9 text-destructive sm:h-8" :disabled="busy" @click="run('off')">
        <PowerOff /> {{ t("Stop") }}
      </Button>
      <div v-else-if="actuator.status === 'IDLE'" class="flex items-center rounded-full border bg-card p-0.5 shadow-soft">
        <button type="button" class="flex size-9 items-center justify-center rounded-full text-muted-foreground hover:bg-muted disabled:opacity-40 sm:size-7"
          :aria-label="t('Fewer minutes')" :disabled="minutes <= 1" @click="minutes = Math.max(1, Number(minutes) - 1)"><Minus class="size-3.5" /></button>
        <input v-model.number="minutes" type="number" min="1" :max="actuator.maxRunMinutes"
          class="num h-9 w-8 bg-transparent text-center text-sm outline-none sm:h-7 [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
          :aria-label="`Minutes to run ${actuator.name}`">
        <span class="pr-1 text-xs text-muted-foreground">{{ t("min") }}</span>
        <button type="button" class="flex size-9 items-center justify-center rounded-full text-muted-foreground hover:bg-muted disabled:opacity-40 sm:size-7"
          :aria-label="t('More minutes')" :disabled="minutes >= actuator.maxRunMinutes" @click="minutes = Math.min(actuator.maxRunMinutes, Number(minutes) + 1)"><Plus class="size-3.5" /></button>
        <Button size="sm" class="ml-1 h-9 sm:h-7" :disabled="busy" @click="run('on')"><Power /> {{ t("Start") }}</Button>
      </div>
    </template>
    <p v-if="message" role="alert" class="w-full text-sm text-destructive">{{ message }}</p>
  </div>
</template>
