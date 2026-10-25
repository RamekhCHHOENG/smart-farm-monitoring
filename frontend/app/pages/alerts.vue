<script setup lang="ts">
import { BellRing, Check, CheckCheck, ShieldCheck, Siren } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Alert as FarmAlert, Farm } from "~/utils/types";
import { alertParts, alertTag } from "~/utils/alertParts";
import { dayStart } from "~/utils/analytics";

const api = useApi();
const { can } = useAuth();
const alerts = ref<FarmAlert[]>([]);
const filter = ref<"OPEN" | "ALL">("OPEN");
const error = ref("");
const openAlerts = useState<number>("openAlerts", () => 0);

// The list refreshes every 5 s; when the tab changes meanwhile, an older answer must not replace the newer one.
let latestRequest = 0;

async function load() {
  const request = ++latestRequest;
  const shown = filter.value;
  try {
    const result = await api<FarmAlert[]>("/api/alerts", { query: shown === "OPEN" ? { status: "OPEN" } : {} });
    if (request !== latestRequest) return;
    alerts.value = result;
    if (shown === "OPEN") openAlerts.value = alerts.value.length;
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 5000);
watch(filter, load);

const openCount = computed(() => alerts.value.filter((a) => a.status === "OPEN").length);

// Figures above the list come from every alert (not just the shown tab) and the farm's zone names.
const everything = ref<FarmAlert[]>([]);
const zoneNames = ref<Record<number, string>>({});
async function loadFigures() {
  try {
    const [all, farm] = await Promise.all([api<FarmAlert[]>("/api/alerts"), api<Farm>("/api/farm")]);
    everything.value = all;
    zoneNames.value = Object.fromEntries(farm.zones.map((z) => [z.id, z.name]));
  } catch {
    // the list above reports connection problems
  }
}
usePolling(loadFigures, 15000);
const figures = computed(() => {
  const today = dayStart(0);
  const yesterday = dayStart(1);
  const at = (iso: string | null) => (iso ? Date.parse(iso) : 0);
  const open = everything.value.filter((a) => a.status === "OPEN");
  return {
    open: open.length,
    critical: open.filter((a) => a.severity === "CRITICAL").length,
    today: everything.value.filter((a) => at(a.raisedAt) >= today).length,
    yesterday: everything.value.filter((a) => at(a.raisedAt) >= yesterday && at(a.raisedAt) < today).length,
    ackToday: everything.value.filter((a) => at(a.acknowledgedAt) >= today).length,
    ackYesterday: everything.value.filter((a) => at(a.acknowledgedAt) >= yesterday && at(a.acknowledgedAt) < today).length,
  };
});
const zoneOf = (a: FarmAlert) => alertParts(a).zone ?? (a.zoneId ? zoneNames.value[a.zoneId] : null) ?? "Farm";

async function acknowledgeAll() {
  if (!(await useConfirm().confirm({ title: `Acknowledge all ${openCount.value} open alerts?`, message: "Do this after checking the farm: they leave the open list for everyone.", confirmLabel: "Acknowledge all" }))) return;
  try {
    await api("/api/alerts/acknowledge-all", { method: "POST" });
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function acknowledge(a: FarmAlert) {
  try {
    await api(`/api/alerts/${a.id}/acknowledge`, { method: "POST" });
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}
</script>

<template>
  <div>
    <PageHeader :title="t('Alerts')" :description="t('Threshold breaches, device faults and offline sensors. Staff acknowledge an alert after checking the zone on site.')">
      <Button v-if="openCount > 0 && can('WORKER', 'TECHNICIAN')" variant="outline" @click="acknowledgeAll">
        <CheckCheck /> {{ t("Acknowledge all ({n})", { n: openCount }) }}
      </Button>
      <Tabs v-model="filter">
        <TabsList>
          <TabsTrigger value="OPEN">{{ t("Open") }}</TabsTrigger>
          <TabsTrigger value="ALL">{{ t("All") }}</TabsTrigger>
        </TabsList>
      </Tabs>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <div class="stagger mb-5 grid grid-cols-2 gap-4 xl:grid-cols-4">
      <StatCard hero :label="t('Open alerts')" :value="figures.open" :icon="BellRing" note="waiting to be acknowledged" />
      <StatCard :label="t('Critical open')" :value="figures.critical" :icon="Siren" :tone="figures.critical ? 'destructive' : ''" note="need someone on site" />
      <StatCard :label="t('Raised today')" :value="figures.today" :delta="figures.today - figures.yesterday" up-is-bad :icon="BellRing" />
      <StatCard :label="t('Acknowledged today')" :value="figures.ackToday" :delta="figures.ackToday - figures.ackYesterday" :icon="ShieldCheck" />
    </div>

    <!-- Phones: stacked cards so the whole message is readable -->
    <div class="flex flex-col gap-3 md:hidden">
      <p v-if="!alerts.length" class="py-6 text-center text-sm text-muted-foreground">{{ t("No alerts.") }}</p>
      <Card v-for="a in alerts" :key="`m${a.id}`" class="gap-3 py-4">
        <CardContent class="flex flex-col gap-2 px-4">
          <div class="flex items-center gap-2">
            <span class="rounded-full px-2.5 py-0.5 text-[11px] font-medium" :class="alertTag(a).tone">{{ alertTag(a).label }}</span>
            <StatusBadge :status="a.severity" />
            <StatusBadge :status="a.status" />
            <span v-if="a.repeatCount > 1" class="text-xs font-medium" :title="t('happened {n} times', { n: a.repeatCount })">×{{ a.repeatCount }}</span>
            <span class="ml-auto text-xs text-muted-foreground">{{ timeAgo(a.lastRaisedAt) }}</span>
          </div>
          <p class="text-xs text-muted-foreground">{{ zoneOf(a) }}</p>
          <p class="text-sm">{{ a.message }}</p>
          <p v-if="a.acknowledgedBy" class="text-xs text-muted-foreground">{{ t("Acknowledged by {name}", { name: a.acknowledgedBy }) }}</p>
          <Button v-if="a.status === 'OPEN' && can('WORKER', 'TECHNICIAN')" size="sm" variant="outline" class="self-start" @click="acknowledge(a)">
            <Check /> {{ t("Acknowledge") }}
          </Button>
        </CardContent>
      </Card>
    </div>

    <Card class="hidden md:flex">
      <CardContent class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{{ t("Severity") }}</TableHead>
              <TableHead>{{ t("Message") }}</TableHead>
              <TableHead>{{ t("Raised") }}</TableHead>
              <TableHead>{{ t("Status") }}</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="a in alerts" :key="a.id">
              <TableCell>
                <div class="flex flex-col items-start gap-1.5">
                  <StatusBadge :status="a.severity" />
                  <span class="rounded-full px-2.5 py-0.5 text-[11px] font-medium" :class="alertTag(a).tone">{{ alertTag(a).label }}</span>
                </div>
              </TableCell>
              <TableCell class="max-w-md whitespace-normal">
                <div class="text-xs text-muted-foreground">{{ zoneOf(a) }}</div>
                {{ a.message }}
                <div v-if="a.repeatCount > 1" class="mt-1 text-xs font-medium text-muted-foreground">{{ t("happened {n} times", { n: a.repeatCount }) }} · {{ timeAgo(a.lastRaisedAt) }}</div>
              </TableCell>
              <TableCell class="whitespace-nowrap text-muted-foreground">{{ formatTime(a.raisedAt) }}</TableCell>
              <TableCell>
                <StatusBadge :status="a.status" />
                <div v-if="a.acknowledgedBy" class="mt-1 text-xs text-muted-foreground">{{ t("Acknowledged by {name}", { name: a.acknowledgedBy }) }}</div>
              </TableCell>
              <TableCell class="text-right">
                <Button v-if="a.status === 'OPEN' && can('WORKER', 'TECHNICIAN')" size="sm" variant="outline" @click="acknowledge(a)">
                  <Check /> {{ t("Acknowledge") }}
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="!alerts.length">
              <TableCell colspan="5" class="text-center text-muted-foreground">{{ t("No alerts.") }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
