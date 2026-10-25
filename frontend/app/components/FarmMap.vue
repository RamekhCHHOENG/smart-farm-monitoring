<script setup lang="ts">
import { ArrowRight, Bird, Flower2, Sprout, X } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import type { Alert, Rule, Zone } from "~/utils/types";
import { zoneHealth } from "~/utils/farmHealth";
import { alertParts } from "~/utils/alertParts";
import { sensorIcon } from "~/utils/deviceIcons";

// The farm seen from above: each zone is a field, coloured by what it is and glowing orange where it is stressed.
// Picking one opens its card: the readings, how stressed it is, and its open critical alert with the actions.

const props = defineProps<{ zones: Zone[]; rules: Rule[]; critical: Alert[] }>();
const emit = defineEmits<{ changed: [] }>();
const { can } = useAuth();
const alerts = useCriticalAlerts();

const selectedId = ref<number | null>(null);
const health = (z: Zone) => zoneHealth(z, props.rules, props.critical.some((a) => a.zoneId === z.id));
// Start on the zone that needs attention most
watch(() => props.zones.map((z) => z.id).join(), () => {
  if (selectedId.value && props.zones.some((z) => z.id === selectedId.value)) return;
  const ranked = [...props.zones].sort((a, b) => health(b).stress - health(a).stress);
  selectedId.value = ranked[0]?.id ?? null;
}, { immediate: true });
const selected = computed(() => props.zones.find((z) => z.id === selectedId.value) ?? null);
// A big farm shows its nine most stressed zones here; the last tile opens the zone finder for the rest.
const MAX_TILES = 9;
const tiles = computed(() => (props.zones.length <= MAX_TILES ? props.zones
  : [...props.zones].sort((a, b) => health(b).stress - health(a).stress).slice(0, MAX_TILES - 1)));
const hidden = computed(() => props.zones.length - tiles.value.length);
const pickerOpen = useState("zonePicker.open", () => false);
const selHealth = computed(() => (selected.value ? health(selected.value) : null));
const selAlert = computed(() => props.critical.find((a) => a.zoneId === selectedId.value) ?? null);

function look(z: Zone) {
  if (/greenhouse/i.test(`${z.name} ${z.detail ?? ""}`)) return { field: "field-greenhouse", icon: Flower2, kind: "Greenhouse" };
  return z.type === "POULTRY" ? { field: "field-poultry", icon: Bird, kind: "Poultry house" } : { field: "field-crop", icon: Sprout, kind: "Crop bed" };
}
const levelText = (l: string) => ({ Low: "text-primary", Medium: "text-warning", High: "text-destructive" })[l];

// The pump/fan a worker would start for this zone's worst reading: an idle actuator whose effect fixes it.
const fix = computed(() => {
  const w = selHealth.value?.worst;
  if (!selected.value || !w?.breached || !w.rule) return null;
  const want = w.rule.condition === "BELOW" ? 1 : -1;
  return selected.value.actuators.find((a) => a.status === "IDLE" && a.effects?.some((e) => e.measurementCode === w.sensor.type && e.direction === want)) ?? null;
});
const busy = ref(false);
const message = ref("");
async function startFix() {
  if (!fix.value) return;
  busy.value = true;
  message.value = "";
  try {
    await useApi()(`/api/actuators/${fix.value.id}/on`, { method: "POST", body: { durationMin: Math.min(5, fix.value.maxRunMinutes) } });
    emit("changed");
  } catch (e) {
    message.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function acknowledge(a: Alert) {
  try {
    await alerts.acknowledge(a.id);
  } catch (e) {
    message.value = errorMessage(e);
  }
}
</script>

<template>
  <div class="grid-cols-1 grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
    <div class="stagger grid auto-rows-[9.5rem] grid-cols-2 gap-3 sm:grid-cols-3">
      <button v-for="(z, i) in tiles" :key="z.id" type="button"
        class="field group relative overflow-hidden rounded-2xl text-left text-white transition-transform hover:-translate-y-0.5 focus-visible:outline-none"
        :class="[look(z).field, { 'sm:row-span-2': i === 0 && tiles.length >= 5, 'ring-[3px] ring-ink ring-offset-2 ring-offset-card': z.id === selectedId }]"
        :aria-pressed="z.id === selectedId" @click="selectedId = z.id">
        <div v-if="health(z).stress >= 30" class="field-heat" :style="{ opacity: Math.min(1, health(z).stress / 100) }" />
        <div v-if="health(z).level === 'High'" class="absolute inset-2 rounded-xl border-2 border-dashed border-yellow-200/90" />
        <div class="relative flex h-full flex-col items-center justify-center gap-1 p-3 text-center drop-shadow-[0_1px_2px_rgb(0_0_0/45%)]">
          <component :is="look(z).icon" class="size-6" />
          <div class="font-medium leading-tight">{{ z.name }}</div>
          <div class="text-xs text-white/85">{{ z.cropName ?? (z.birdCount ? `${z.birdCount} birds` : t(look(z).kind)) }}</div>
        </div>
        <span class="absolute top-2.5 right-2.5 rounded-full bg-black/35 px-2 py-0.5 text-[11px] backdrop-blur">{{ health(z).level }}</span>
      </button>
      <button v-if="hidden > 0" type="button" class="flex flex-col items-center justify-center gap-1 rounded-2xl border-2 border-dashed bg-card/60 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
        @click="pickerOpen = true">
        <span class="num text-3xl text-foreground">+{{ hidden }}</span>
        <span class="text-sm">{{ t("more zones") }}</span>
        <span class="text-xs">{{ t("find a zone") }}</span>
      </button>
    </div>

    <Transition name="swap" mode="out-in">
    <div v-if="selected && selHealth" :key="selected.id" class="flex flex-col gap-3 rounded-2xl border bg-card p-4 shadow-soft dark:bg-popover">
      <div class="flex items-start gap-2">
        <div class="min-w-0">
          <div class="text-lg leading-tight font-medium">{{ selected.name }}<span class="text-muted-foreground"> · {{ selected.cropName ?? t(look(selected).kind) }}</span></div>
          <div class="text-xs text-muted-foreground">{{ selected.sensors.length }} sensors · {{ selected.actuators.length }} actuators</div>
        </div>
        <Button size="icon-sm" variant="ghost" class="ml-auto" :aria-label="t('Close')" @click="selectedId = null"><X /></Button>
      </div>

      <div class="flex flex-col items-center">
        <SemiGauge :value="selHealth.stress" :size="170" />
        <div class="mt-1 text-xs"><span class="text-muted-foreground">{{ t("Stress index") }}</span> · <span :class="levelText(selHealth.level)">{{ selHealth.level }}</span></div>
        <p class="mt-1 text-center text-xs text-muted-foreground">
          <template v-if="selHealth.worst?.breached">{{ selHealth.worst.sensor.name }} is past its rule limit.</template>
          <template v-else-if="selHealth.stress >= 30">{{ selHealth.worst?.sensor.name }} is getting close to a limit.</template>
          <template v-else>Every reading is comfortably inside its limits.</template>
        </p>
      </div>

      <div class="grid grid-cols-2 gap-x-3 gap-y-2.5">
        <div v-for="s in selected.sensors.slice(0, 6)" :key="s.id" class="flex items-center gap-2.5">
          <span class="badge-disc size-8 shrink-0"><component :is="sensorIcon(s.type)" class="size-4" /></span>
          <div class="min-w-0 leading-tight">
            <div class="num text-[15px]" :class="{ 'text-destructive': health(selected).breached.some((b) => b.sensor.id === s.id) }">
              {{ s.latest ? s.latest.value.toFixed(1) : "–" }}<span class="ml-0.5 font-sans text-[11px] text-muted-foreground">{{ s.unit }}</span>
            </div>
            <div class="truncate text-[11px] text-muted-foreground">{{ s.measurementName ?? s.name }}</div>
          </div>
        </div>
      </div>

      <div v-if="selAlert" class="rounded-xl border border-destructive/25 bg-destructive/8 p-3 text-sm">
        <div class="text-xs text-destructive">{{ t("Critical alert") }} · {{ timeAgo(selAlert.lastRaisedAt) }}</div>
        <div class="mt-0.5">{{ alertParts(selAlert).problem }}</div>
        <div v-if="alertParts(selAlert).action" class="mt-1 text-xs text-muted-foreground">↳ {{ alertParts(selAlert).action }}</div>
      </div>

      <div class="mt-auto flex flex-wrap gap-2">
        <Button v-if="selAlert && can('WORKER', 'TECHNICIAN')" size="sm" variant="outline" @click="acknowledge(selAlert)">{{ t("Acknowledge") }}</Button>
        <Button v-if="fix && can('WORKER')" size="sm" :disabled="busy" @click="startFix">{{ t("Start") }} {{ fix.name }}</Button>
        <Button size="sm" variant="ghost" as-child><NuxtLink :to="`/zone/${selected.id}`">{{ t("Open zone") }} <ArrowRight /></NuxtLink></Button>
      </div>
      <p v-if="message" role="alert" class="text-sm text-destructive">{{ message }}</p>
    </div>
    <div v-else class="hidden items-center justify-center rounded-2xl border border-dashed p-6 text-sm text-muted-foreground lg:flex">
      Pick a zone on the map.
    </div>
    </Transition>
  </div>
</template>
