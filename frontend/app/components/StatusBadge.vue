<script setup lang="ts">
const props = defineProps<{ status: string }>();

// Colour follows meaning: olive = working, grey = resting, ochre = needs attention, terracotta = broken.
// A soft tinted pill with a dot; RUNNING pulses so moving equipment stands out.
const tone = computed(() => {
  switch (props.status) {
    case "RUNNING":
    case "ONLINE":
    case "ACKNOWLEDGED":
    case "ACTIVE":
      return { dot: "bg-primary", text: "text-primary bg-primary/12" };
    case "IDLE":
    case "INFO":
    case "PAUSED":
      return { dot: "bg-muted-foreground/60", text: "text-muted-foreground bg-muted" };
    case "WARNING":
    case "MAINTENANCE":
    case "OFFLINE":
    case "OPEN":
      return { dot: "bg-warning", text: "text-foreground bg-warning/18" };
    case "FAULT":
    case "CRITICAL":
      return { dot: "bg-destructive", text: "text-destructive bg-destructive/12" };
    default:
      return { dot: "bg-muted-foreground/40", text: "text-muted-foreground bg-muted" };
  }
});
</script>

<template>
  <span class="inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-medium tracking-wide whitespace-nowrap uppercase" :class="tone.text">
    <span class="relative flex size-2">
      <span v-if="status === 'RUNNING'" class="absolute inline-flex size-full animate-ping rounded-full opacity-60" :class="tone.dot" />
      <span class="relative inline-flex size-2 rounded-full" :class="tone.dot" />
    </span>
    {{ t(status) }}
  </span>
</template>
