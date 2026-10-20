<script setup lang="ts">
import { Activity, Bell, Cpu, Sprout } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { Alert as FarmAlert, Farm } from "~/utils/types";

const api = useApi();
const farm = ref<Farm | null>(null);
const recentAlerts = ref<FarmAlert[]>([]);
// Connection problems clear themselves when the server answers again;
// errors from a button show next to that button (ActuatorControl).
const connectionError = ref("");
const openAlerts = useState<number>("openAlerts", () => 0);

async function load() {
  try {
    const [f, alerts] = await Promise.all([
      api<Farm>("/api/farm"),
      api<FarmAlert[]>("/api/alerts", { query: { status: "OPEN" } }),
    ]);
    farm.value = f;
    recentAlerts.value = alerts.slice(0, 5);
    openAlerts.value = f.openAlerts;
    connectionError.value = "";
  } catch (e) {
    connectionError.value = `${errorMessage(e)} Retrying every 4 seconds...`;
  }
}
usePolling(load, 4000);

const stats = computed(() => {
  const zones = farm.value?.zones ?? [];
  const sensors = zones.flatMap((z) => z.sensors);
  const actuators = zones.flatMap((z) => z.actuators);
  return [
    { label: "Zones", value: zones.length, icon: Sprout },
    { label: "Sensors online", value: `${sensors.filter((s) => s.online).length}/${sensors.length}`, icon: Activity },
    { label: "Actuators running", value: actuators.filter((a) => a.status === "RUNNING").length, icon: Cpu },
    { label: "Open alerts", value: farm.value?.openAlerts ?? 0, icon: Bell },
  ];
});
</script>

<template>
  <div>
    <PageHeader :title="farm?.name ?? 'Dashboard'" :description="farm ? `${farm.location} · live, refreshes every 4 seconds` : 'Loading…'" />

    <Alert v-if="connectionError" variant="destructive" class="mb-4">
      <AlertDescription>{{ connectionError }}</AlertDescription>
    </Alert>

    <div class="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
      <Card v-for="s in stats" :key="s.label" class="gap-1 py-4">
        <CardContent class="flex items-center gap-3 px-4">
          <div class="flex size-9 items-center justify-center rounded-lg bg-accent text-accent-foreground">
            <component :is="s.icon" class="size-4" />
          </div>
          <div>
            <div class="text-xl font-semibold tabular-nums">{{ s.value }}</div>
            <div class="text-xs text-muted-foreground">{{ s.label }}</div>
          </div>
        </CardContent>
      </Card>
    </div>

    <Card v-if="farm && farm.zones.length === 0" class="mb-6">
      <CardHeader>
        <CardTitle>Set up your farm</CardTitle>
        <CardDescription>
          No zones yet. The Farm Owner adds zones, then the Technician registers sensors and actuators in each zone.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button as-child><NuxtLink to="/zones">Go to Zones &amp; devices</NuxtLink></Button>
      </CardContent>
    </Card>

    <div class="grid gap-4 xl:grid-cols-[1fr_340px]">
      <div class="grid gap-4 lg:grid-cols-2">
        <Card v-for="zone in farm?.zones ?? []" :key="zone.id">
          <CardHeader>
            <CardTitle class="flex items-center gap-2">
              {{ zone.name }}
              <span class="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium text-secondary-foreground">{{ zone.type }}</span>
            </CardTitle>
            <CardDescription>{{ zone.detail }}</CardDescription>
          </CardHeader>
          <CardContent class="flex flex-col gap-3">
            <div class="grid grid-cols-2 gap-2">
              <SensorTile v-for="s in zone.sensors" :key="s.id" :sensor="s" />
            </div>
            <p v-if="zone.sensors.length === 0" class="text-sm text-muted-foreground">No sensors yet.</p>
            <ActuatorControl
              v-for="a in zone.actuators.filter((x) => x.status !== 'DECOMMISSIONED')"
              :key="a.id"
              :actuator="a"
              @changed="load"
            />
          </CardContent>
        </Card>
      </div>

      <Card class="h-fit">
        <CardHeader>
          <CardTitle>Open alerts</CardTitle>
          <CardDescription>Newest first</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-2">
          <p v-if="recentAlerts.length === 0" class="text-sm text-muted-foreground">All clear.</p>
          <div v-for="a in recentAlerts" :key="a.id" class="rounded-lg border p-3">
            <div class="mb-1 flex items-center gap-2">
              <StatusBadge :status="a.severity" />
              <span class="text-xs text-muted-foreground">{{ timeAgo(a.raisedAt) }}</span>
            </div>
            <p class="text-sm">{{ a.message }}</p>
          </div>
          <Button v-if="recentAlerts.length" as-child variant="outline" size="sm">
            <NuxtLink to="/alerts">View all alerts</NuxtLink>
          </Button>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
