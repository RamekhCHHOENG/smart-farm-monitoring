<script setup lang="ts">
import { TransitionPresets, usePreferredReducedMotion, useTransition } from "@vueuse/core";

// A number that counts up when it first shows and rolls to each new value (readings refresh every 4 s).
// The real value is always the one in the page text for screen readers; only the shown digits move.
const props = withDefaults(defineProps<{ value: number | null | undefined; decimals?: number; duration?: number }>(),
  { decimals: 0, duration: 900 });

const reduced = usePreferredReducedMotion();
const source = ref(0);
const duration = computed(() => (reduced.value === "reduce" ? 0 : props.duration));
const shown = useTransition<number>(source, {
  duration,
  transition: TransitionPresets.easeOutCubic,
});
onMounted(() => {
  source.value = props.value ?? 0;
});
watch(() => props.value, (v) => {
  source.value = v ?? 0;
});
const text = computed(() => (props.value === null || props.value === undefined ? "–" : shown.value.toFixed(props.decimals)));
</script>

<template>
  <span class="tabular-nums" :aria-label="value === null || value === undefined ? undefined : value.toFixed(decimals)">{{ text }}</span>
</template>
