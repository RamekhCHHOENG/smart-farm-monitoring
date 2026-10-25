<script setup lang="ts">
// A ring that fills to a percentage (olive when good, gold when so-so, terracotta when poor), with the number inside.
const props = withDefaults(defineProps<{ value: number | null; size?: number }>(), { size: 132 });
const R = 52;
const C = 2 * Math.PI * R;
const shown = ref(0);
onMounted(() => requestAnimationFrame(() => (shown.value = props.value ?? 0)));
watch(() => props.value, (v) => (shown.value = v ?? 0));
const color = computed(() => (props.value === null ? "var(--muted-foreground)" : props.value >= 90 ? "var(--primary)" : props.value >= 70 ? "var(--warning)" : "var(--destructive)"));
</script>

<template>
  <div class="relative" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg viewBox="0 0 120 120" class="size-full -rotate-90" aria-hidden="true">
      <circle cx="60" cy="60" :r="R" fill="none" stroke="var(--muted)" stroke-width="11" />
      <circle cx="60" cy="60" :r="R" fill="none" :stroke="color" stroke-width="11" stroke-linecap="round"
        :stroke-dasharray="C" :stroke-dashoffset="C * (1 - shown / 100)" class="ring" />
    </svg>
    <div class="absolute inset-0 flex flex-col items-center justify-center">
      <span class="num text-3xl"><AnimatedNumber :value="value" /><span v-if="value !== null" class="text-base">%</span></span>
      <span class="text-[11px] text-muted-foreground">in range</span>
    </div>
  </div>
</template>

<style scoped>
.ring { transition: stroke-dashoffset 1.1s var(--ease-out), stroke 0.3s; }
</style>
