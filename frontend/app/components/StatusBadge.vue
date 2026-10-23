<script setup lang="ts">
import { Badge } from "@/components/ui/badge";

const props = defineProps<{ status: string }>();

// Colour follows meaning: green = working, amber = needs attention, red = broken.
const tone = computed(() => {
  switch (props.status) {
    case "RUNNING":
    case "ONLINE":
    case "ACKNOWLEDGED":
    case "ACTIVE":
      return "bg-primary/15 text-primary";
    case "IDLE":
    case "INFO":
    case "PAUSED":
      return "bg-secondary text-secondary-foreground";
    case "WARNING":
    case "MAINTENANCE":
    case "OFFLINE":
    case "OPEN":
      return "bg-warning/20 text-foreground";
    case "FAULT":
    case "CRITICAL":
      return "bg-destructive/15 text-destructive";
    default:
      return "bg-muted text-muted-foreground";
  }
});
</script>

<template>
  <Badge :class="tone">{{ t(status) }}</Badge>
</template>
