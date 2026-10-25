<script setup lang="ts">
import { CircleAlert, CircleCheck, X } from "lucide-vue-next";

// Where useToast() messages appear: bottom-left on a computer (the critical-alert card uses bottom-right),
// above the tab bar on a phone.
const { toasts, dismiss } = useToast();
</script>

<template>
  <div class="pointer-events-none fixed inset-x-4 bottom-24 z-[70] flex flex-col items-center gap-2 md:right-auto md:bottom-6 md:left-[calc(248px+2rem)] md:items-start"
    aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="x in toasts" :key="x.id" :role="x.kind === 'error' ? 'alert' : 'status'"
        class="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl border bg-popover px-4 py-3 text-sm shadow-soft"
        :class="x.kind === 'error' ? 'border-destructive/30' : ''">
        <component :is="x.kind === 'error' ? CircleAlert : CircleCheck" class="mt-px size-5 shrink-0"
          :class="x.kind === 'error' ? 'text-destructive' : 'text-primary'" />
        <span class="flex-1">{{ x.text }}</span>
        <button type="button" class="hit -mr-1 rounded-full p-0.5 text-muted-foreground hover:text-foreground" :aria-label="t('Dismiss')" @click="dismiss(x.id)">
          <X class="size-4" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active { transition: opacity 0.25s var(--ease-out), transform 0.25s var(--ease-out); }
.toast-leave-active { transition: opacity 0.18s ease; }
.toast-enter-from { opacity: 0; transform: translateY(12px) scale(0.98); }
.toast-leave-to { opacity: 0; }
</style>
