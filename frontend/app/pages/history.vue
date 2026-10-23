<script setup lang="ts">
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Download } from "lucide-vue-next";
import type { DeviceLog, Farm, Reading, Rule } from "~/utils/types";

const api = useApi();
const route = useRoute();
const { token } = useAuth();
const farm = ref<Farm | null>(null);
const rules = ref<Rule[]>([]);
const zoneId = ref<number>(0);
const sensorId = ref<number>(0);
const from = ref(toLocalInput(new Date(Date.now() - 60 * 60 * 1000)));
const to = ref(toLocalInput(new Date(Date.now() + 60 * 1000)));
const readings = ref<Reading[]>([]);
const logs = ref<DeviceLog[]>([]);
const error = ref("");

const zone = computed(() => farm.value?.zones.find((z) => z.id === zoneId.value));
const sensor = computed(() => zone.value?.sensors.find((s) => s.id === sensorId.value));
const points = computed(() => readings.value.map((r) => ({ t: new Date(r.recordedAt).getTime(), v: r.value })));
const thresholds = computed(() => rules.value
  .filter((r) => r.sensorId === sensorId.value && r.active)
  .map((r) => ({ value: r.threshold, label: `${r.condition === "ABOVE" ? ">" : "<"} ${r.threshold}${r.unit}` })));
const stats = computed(() => {
  const v = readings.value.map((r) => r.value);
  if (!v.length) return null;
  return { min: Math.min(...v), max: Math.max(...v), avg: v.reduce((a, b) => a + b, 0) / v.length, count: v.length };
});

onMounted(async () => {
  try {
    [farm.value, rules.value] = await Promise.all([api<Farm>("/api/farm"), api<Rule[]>("/api/rules")]);
    // Opened from a sensor tile on the dashboard: show that sensor straight away
    const wantedZone = Number(route.query.zone);
    zoneId.value = farm.value.zones.some((z) => z.id === wantedZone) ? wantedZone : farm.value.zones[0]?.id ?? 0;
  } catch (e) {
    error.value = errorMessage(e);
  }
});

watch(zoneId, () => {
  const wantedSensor = Number(route.query.sensor);
  sensorId.value = zone.value?.sensors.some((s) => s.id === wantedSensor) ? wantedSensor : zone.value?.sensors[0]?.id ?? 0;
});

const PRESETS = [
  { label: "Last hour", hours: 1 },
  { label: "24 hours", hours: 24 },
  { label: "7 days", hours: 24 * 7 },
  { label: "30 days", hours: 24 * 30 },
];
function preset(hours: number) {
  from.value = toLocalInput(new Date(Date.now() - hours * 3600 * 1000));
  to.value = toLocalInput(new Date(Date.now() + 60 * 1000));
  load();
}

const downloading = ref(false);
/** Every reading in the range as a spreadsheet file (the chart shows at most 500 points). */
async function downloadCsv() {
  if (!sensor.value) return;
  downloading.value = true;
  error.value = "";
  try {
    const query = new URLSearchParams({ from: localInputToIso(from.value), to: localInputToIso(to.value) });
    const res = await fetch(`/api/sensors/${sensor.value.id}/readings.csv?${query}`, {
      headers: { Authorization: `Bearer ${token.value ?? ""}` },
    });
    if (!res.ok) throw { statusCode: res.status, message: `Download failed (${res.status})` };
    const url = URL.createObjectURL(await res.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = `${sensor.value.name.replace(/[^\p{L}\p{N}]+/gu, "-")}-${from.value.slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    downloading.value = false;
  }
}
watch([sensorId, zoneId], load);

// Answers can arrive out of order when someone switches zone or sensor quickly;
// only the answer to the latest request may fill the chart.
let latestRequest = 0;

async function load() {
  if (!zoneId.value) return;
  const request = ++latestRequest;
  error.value = "";
  const query = { from: localInputToIso(from.value), to: localInputToIso(to.value) };
  try {
    const [r, l] = await Promise.all([
      sensorId.value ? api<Reading[]>(`/api/sensors/${sensorId.value}/readings`, { query }) : Promise.resolve([]),
      api<DeviceLog[]>(`/api/zones/${zoneId.value}/logs`, { query }),
    ]);
    if (request !== latestRequest) return;
    readings.value = r;
    logs.value = l;
  } catch (e) {
    if (request === latestRequest) error.value = errorMessage(e);
  }
}
</script>

<template>
  <div>
    <PageHeader :title="t('History & reports')" :description="t('Reading history and device activity per zone for a selected date range.')">
      <Button variant="outline" :disabled="!sensor || downloading" @click="downloadCsv"><Download /> {{ t("Download CSV") }}</Button>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <Card class="mb-4">
      <CardContent class="flex flex-col gap-3">
        <div class="flex flex-wrap gap-1.5">
          <Button v-for="p in PRESETS" :key="p.hours" type="button" size="sm" variant="outline" @click="preset(p.hours)">{{ t(p.label) }}</Button>
        </div>
        <form class="grid gap-3 sm:grid-cols-2 lg:grid-cols-5 lg:items-end" @submit.prevent="load">
          <div class="flex flex-col gap-1.5">
            <Label for="hzone">{{ t("Zone") }}</Label>
            <NativeSelect id="hzone" v-model.number="zoneId" class="w-full">
              <NativeSelectOption v-for="z in farm?.zones ?? []" :key="z.id" :value="z.id">{{ z.name }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="hsensor">{{ t("Sensor") }}</Label>
            <NativeSelect id="hsensor" v-model.number="sensorId" class="w-full">
              <NativeSelectOption v-for="s in zone?.sensors ?? []" :key="s.id" :value="s.id">{{ s.name }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="hfrom">{{ t("From") }}</Label>
            <Input id="hfrom" v-model="from" type="datetime-local" />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="hto">{{ t("To") }}</Label>
            <Input id="hto" v-model="to" type="datetime-local" />
          </div>
          <Button type="submit">{{ t("Show") }}</Button>
        </form>
      </CardContent>
    </Card>

    <div class="grid gap-4 xl:grid-cols-[1fr_380px]">
      <Card>
        <CardHeader>
          <CardTitle>{{ sensor?.name ?? t("Sensor") }}</CardTitle>
          <CardDescription v-if="stats">
            {{ t("{n} readings", { n: stats.count }) }} · min {{ stats.min.toFixed(1) }} · avg {{ stats.avg.toFixed(1) }} · max {{ stats.max.toFixed(1) }} {{ sensor?.unit }}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <LineChart v-if="points.length > 1" :points="points" :unit="sensor?.unit ?? ''" :thresholds="thresholds" />
          <p v-else class="py-12 text-center text-sm text-muted-foreground">{{ t("Not enough readings in this range.") }}</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{{ t("Device activity") }}</CardTitle>
          <CardDescription>{{ t("{n} actions in this zone", { n: logs.length }) }}</CardDescription>
        </CardHeader>
        <CardContent class="max-h-[420px] overflow-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{{ t("When") }}</TableHead>
                <TableHead>{{ t("Action") }}</TableHead>
                <TableHead>{{ t("By") }}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="l in logs" :key="l.id">
                <TableCell class="whitespace-nowrap text-xs text-muted-foreground">{{ formatTime(l.at) }}</TableCell>
                <TableCell class="whitespace-normal">
                  <div class="text-sm">{{ l.actuatorName }} {{ l.action.toLowerCase().replace("_", " ") }}</div>
                  <div v-if="l.detail" class="text-xs text-muted-foreground">{{ l.detail }}</div>
                </TableCell>
                <TableCell class="text-xs">{{ l.userName ?? l.source.toLowerCase() }}</TableCell>
              </TableRow>
              <TableRow v-if="!logs.length">
                <TableCell colspan="3" class="text-center text-muted-foreground">{{ t("No activity.") }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
