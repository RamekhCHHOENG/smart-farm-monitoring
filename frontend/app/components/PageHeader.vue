<script setup lang="ts">
defineProps<{ title: string; description?: string }>();
</script>

<template>
  <div class="mb-7 flex flex-wrap items-end justify-between gap-3">
    <div class="max-w-3xl">
      <h1 class="text-[2.1rem] leading-tight font-normal tracking-tight">{{ title }}</h1>
      <p v-if="description" class="mt-1.5 text-sm text-muted-foreground">{{ description }}</p>
    </div>
    <div class="flex flex-wrap gap-2">
      <slot />
    </div>
  </div>
</template>
