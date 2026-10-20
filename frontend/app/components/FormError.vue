<script setup lang="ts">
import { Alert, AlertDescription } from "@/components/ui/alert";

defineProps<{ message: string }>();
</script>

<template>
  <Alert v-if="message" variant="destructive" role="alert">
    <AlertDescription>{{ message }}</AlertDescription>
  </Alert>
</template>
