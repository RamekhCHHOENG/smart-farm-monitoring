<script setup lang="ts">
import { BellRing, X } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import type { Alert } from "~/utils/types";
import { alertNow, alertParts } from "~/utils/alertParts";

// Critical alerts without a banner: a pill in the top bar that opens the list, and a card in the corner
// when a new one arrives. Each alert is laid out in parts: where, what, how far off, and what the farm did.

const props = withDefaults(defineProps<{ zoneNames: Record<number, string>;
  zones?: { id: number; sensors: { name: string; unit: string; latest: { value: number } | null }[] }[] }>(), { zones: () => [] });
const alerts = useCriticalAlerts();
const { can } = useAuth();
const open = ref(false);
const busy = ref<number | null>(null);
const error = ref("");
const root = ref<HTMLElement | null>(null);

// Still-bad alerts first; the ones whose reading is already back in range go to the end of the list.
const nowOf = (a: Alert) => alertNow(a, props.zones);
const list = computed(() => [...alerts.critical.value].sort((a, b) => Number(!!nowOf(a)?.recovered) - Number(!!nowOf(b)?.recovered)));
function parts(a: Alert) {
  const p = alertParts(a);
  return { ...p, zone: p.zone ?? (a.zoneId ? props.zoneNames[a.zoneId] ?? null : null) };
}
const viewLink = (a: Alert) => (a.zoneId ? { path: `/zone/${a.zoneId}` } : { path: "/alerts" });

async function acknowledge(a: Alert) {
  busy.value = a.id;
  error.value = "";
  try {
    await alerts.acknowledge(a.id);
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = null;
  }
}

// The corner card hides itself after 20 s; the alert stays in the pill until someone acknowledges it.
let hideTimer: ReturnType<typeof setTimeout> | undefined;
watch(alerts.latest, (a) => {
  clearTimeout(hideTimer);
  if (a) hideTimer = setTimeout(() => alerts.dismiss(), 20000);
});

function onDocClick(e: MouseEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false;
}
function onKey(e: KeyboardEvent) {
  if (e.key === "Escape") open.value = false;
}
onMounted(() => {
  document.addEventListener("click", onDocClick);
  document.addEventListener("keydown", onKey);
});
onBeforeUnmount(() => {
  document.removeEventListener("click", onDocClick);
  document.removeEventListener("keydown", onKey);
  clearTimeout(hideTimer);
});
watch(() => useRoute().fullPath, () => (open.value = false));
</script>

<template>
  <div ref="root" class="relative">
    <button v-if="list.length" type="button"
      class="round-btn w-auto max-w-[22rem] gap-2 pr-3.5 pl-2 text-sm"
      :aria-expanded="open" aria-haspopup="dialog" @click="open = !open">
      <span class="relative flex size-5 items-center justify-center rounded-full bg-destructive text-destructive-foreground">
        <span class="absolute inset-0 animate-ping rounded-full bg-destructive opacity-40" />
        <BellRing class="relative size-3" />
      </span>
      <span class="font-medium whitespace-nowrap text-destructive">{{ list.length }} {{ t("critical") }}</span>
      <span v-if="list.every((a) => nowOf(a)?.recovered)" class="hidden truncate text-primary lg:inline">· {{ t("all back in range") }}</span>
      <span v-else class="hidden truncate text-muted-foreground lg:inline">· {{ parts(list[0]!).subject ?? parts(list[0]!).problem }}
        <template v-if="parts(list[0]!).value"> {{ parts(list[0]!).value }}</template></span>
    </button>

    <Transition name="pop">
    <div v-if="open" role="dialog" :aria-label="t('Critical alerts')"
      class="absolute right-0 z-50 mt-2 flex origin-top-right max-h-[70vh] w-[min(26rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-2xl border bg-popover shadow-soft">
      <div class="flex items-center justify-between border-b px-4 py-3">
        <div class="font-medium">{{ t("Critical alerts") }}</div>
        <NuxtLink to="/alerts" class="text-xs text-muted-foreground hover:text-foreground">{{ t("All alerts") }} →</NuxtLink>
      </div>
      <div class="flex flex-col gap-2 overflow-y-auto p-2">
        <TransitionGroup name="pop">
        <AlertCard v-for="a in list" :key="a.id" :alert="a" :parts="parts(a)" :busy="busy === a.id"
          :can-ack="can('WORKER', 'TECHNICIAN')" :view="viewLink(a)" :now="nowOf(a)" @acknowledge="acknowledge(a)" />
        </TransitionGroup>
        <p v-if="error" role="alert" class="px-2 pb-1 text-sm text-destructive">{{ error }}</p>
      </div>
    </div>
    </Transition>

    <Teleport to="body">
      <Transition enter-from-class="translate-y-4 opacity-0" leave-to-class="translate-y-4 opacity-0"
        enter-active-class="transition duration-300" leave-active-class="transition duration-200">
        <div v-if="alerts.latest.value" role="alert"
          class="fixed right-4 bottom-24 z-50 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-destructive/30 bg-popover shadow-soft md:bottom-6">
          <div class="flex items-center gap-2 bg-destructive/10 px-4 py-2.5 text-sm font-medium text-destructive">
            <BellRing class="size-4" /> {{ t("New critical alert") }}
            <Button size="icon-sm" variant="ghost" class="ml-auto text-foreground" :aria-label="t('Dismiss')" @click="alerts.dismiss()"><X /></Button>
          </div>
          <AlertCard :alert="alerts.latest.value" :parts="parts(alerts.latest.value)" :busy="busy === alerts.latest.value.id" flat
            :can-ack="can('WORKER', 'TECHNICIAN')" :view="viewLink(alerts.latest.value)" :now="nowOf(alerts.latest.value)"
            @acknowledge="acknowledge(alerts.latest.value!)" @view="alerts.dismiss()" />
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
