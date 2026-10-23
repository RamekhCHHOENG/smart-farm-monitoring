<script setup lang="ts">
import { Check, CheckCheck } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { Alert as FarmAlert } from "~/utils/types";

const api = useApi();
const { can } = useAuth();
const alerts = ref<FarmAlert[]>([]);
const filter = ref<"OPEN" | "ALL">("OPEN");
const error = ref("");
const openAlerts = useState<number>("openAlerts", () => 0);

async function load() {
  try {
    alerts.value = await api<FarmAlert[]>("/api/alerts", { query: filter.value === "OPEN" ? { status: "OPEN" } : {} });
    if (filter.value === "OPEN") openAlerts.value = alerts.value.length;
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 5000);
watch(filter, load);

const openCount = computed(() => alerts.value.filter((a) => a.status === "OPEN").length);

async function acknowledgeAll() {
  if (!window.confirm(`Acknowledge all ${openCount.value} open alerts? Do this after checking the farm.`)) return;
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

    <!-- Phones: stacked cards so the whole message is readable -->
    <div class="flex flex-col gap-3 md:hidden">
      <p v-if="!alerts.length" class="py-6 text-center text-sm text-muted-foreground">{{ t("No alerts.") }}</p>
      <Card v-for="a in alerts" :key="`m${a.id}`" class="gap-3 py-4">
        <CardContent class="flex flex-col gap-2 px-4">
          <div class="flex items-center gap-2">
            <StatusBadge :status="a.severity" />
            <StatusBadge :status="a.status" />
            <span v-if="a.repeatCount > 1" class="text-xs font-medium" :title="t('happened {n} times', { n: a.repeatCount })">×{{ a.repeatCount }}</span>
            <span class="ml-auto text-xs text-muted-foreground">{{ timeAgo(a.lastRaisedAt) }}</span>
          </div>
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
              <TableCell><StatusBadge :status="a.severity" /></TableCell>
              <TableCell class="max-w-md whitespace-normal">
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
