<script setup lang="ts">
import { Check } from "lucide-vue-next";
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
    <PageHeader title="Alerts" description="Threshold breaches, device faults and offline sensors. Staff acknowledge an alert after checking the zone on site.">
      <Tabs v-model="filter">
        <TabsList>
          <TabsTrigger value="OPEN">Open</TabsTrigger>
          <TabsTrigger value="ALL">All</TabsTrigger>
        </TabsList>
      </Tabs>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <Card>
      <CardContent class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Severity</TableHead>
              <TableHead>Message</TableHead>
              <TableHead>Raised</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="a in alerts" :key="a.id">
              <TableCell><StatusBadge :status="a.severity" /></TableCell>
              <TableCell class="max-w-md whitespace-normal">{{ a.message }}</TableCell>
              <TableCell class="whitespace-nowrap text-muted-foreground">{{ formatTime(a.raisedAt) }}</TableCell>
              <TableCell>
                <StatusBadge :status="a.status" />
                <div v-if="a.acknowledgedBy" class="mt-1 text-xs text-muted-foreground">by {{ a.acknowledgedBy }}</div>
              </TableCell>
              <TableCell class="text-right">
                <Button v-if="a.status === 'OPEN' && can('WORKER', 'TECHNICIAN')" size="sm" variant="outline" @click="acknowledge(a)">
                  <Check /> Acknowledge
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="!alerts.length">
              <TableCell colspan="5" class="text-center text-muted-foreground">No alerts.</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
