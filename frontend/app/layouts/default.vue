<script setup lang="ts">
import { Bell, CalendarClock, ChartLine, KeyRound, LayoutDashboard, LogOut, Moon, Sprout, Sun, Workflow } from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { roleLabels } from "~/utils/types";

const { user, logout } = useAuth();
const openAlerts = useState<number>("openAlerts", () => 0);
const passwordOpen = ref(false);

const nav = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/zones", label: "Zones & devices", icon: Sprout },
  { to: "/rules", label: "Automation rules", icon: Workflow },
  { to: "/schedules", label: "Schedules", icon: CalendarClock },
  { to: "/alerts", label: "Alerts", icon: Bell },
  { to: "/history", label: "History", icon: ChartLine },
];

const dark = ref(false);
onMounted(() => {
  try {
    dark.value = localStorage.getItem("smartfarm.theme") === "dark";
  } catch {
    dark.value = false;
  }
  document.documentElement.classList.toggle("dark", dark.value);
});
function toggleTheme() {
  dark.value = !dark.value;
  document.documentElement.classList.toggle("dark", dark.value);
  try {
    localStorage.setItem("smartfarm.theme", dark.value ? "dark" : "light");
  } catch {
    // ignore blocked storage
  }
}
</script>

<template>
  <div class="min-h-screen md:grid md:grid-cols-[240px_1fr]">
    <aside class="border-b bg-card md:sticky md:top-0 md:h-screen md:border-r md:border-b-0">
      <div class="flex items-center gap-2 px-5 py-4">
        <div class="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Sprout class="size-4" />
        </div>
        <div class="leading-tight">
          <div class="text-sm font-semibold">Smart Farm</div>
          <div class="text-xs text-muted-foreground">Monitoring System</div>
        </div>
      </div>
      <nav class="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:overflow-visible">
        <NuxtLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="flex shrink-0 items-center gap-2.5 rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          active-class="!bg-accent !text-accent-foreground font-medium"
        >
          <component :is="item.icon" class="size-4" />
          {{ item.label }}
          <Badge v-if="item.to === '/alerts' && openAlerts > 0" variant="destructive" class="ml-auto">{{ openAlerts }}</Badge>
        </NuxtLink>
      </nav>
    </aside>

    <div class="min-w-0">
      <header class="flex items-center justify-end gap-2 border-b px-4 py-3 md:px-8">
        <div v-if="user" class="mr-auto text-sm md:mr-0">
          <span class="font-medium">{{ user.name }}</span>
          <span class="hidden text-muted-foreground sm:inline"> · {{ roleLabels[user.role] }}</span>
        </div>
        <Button variant="ghost" size="icon-sm" :aria-label="dark ? 'Light mode' : 'Dark mode'" @click="toggleTheme">
          <Sun v-if="dark" />
          <Moon v-else />
        </Button>
        <Button variant="ghost" size="icon-sm" aria-label="Change password" title="Change password" @click="passwordOpen = true">
          <KeyRound />
        </Button>
        <Button variant="outline" size="sm" @click="logout">
          <LogOut /> Log out
        </Button>
        <ChangePasswordDialog v-model:open="passwordOpen" />
      </header>
      <main class="mx-auto max-w-7xl px-4 py-6 md:px-8">
        <slot />
      </main>
    </div>
  </div>
</template>
