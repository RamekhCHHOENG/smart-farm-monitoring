<script setup lang="ts">
import { TriangleAlert, CircleHelp } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";

// Shows the question asked through useConfirm(). Escape, the X or Cancel answer "no".
const { pending, answer } = useConfirm();
const open = computed({
  get: () => !!pending.value,
  set: (v: boolean) => { if (!v) answer(false); },
});
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent role="alertdialog" class="sm:max-w-md">
      <DialogHeader class="items-center text-center sm:items-center sm:text-center">
        <span class="mb-1 flex size-12 items-center justify-center rounded-full"
          :class="pending?.danger ? 'bg-destructive/12 text-destructive' : 'badge-disc'">
          <component :is="pending?.danger ? TriangleAlert : CircleHelp" class="size-6" />
        </span>
        <DialogTitle class="text-xl">{{ pending?.title }}</DialogTitle>
        <DialogDescription v-if="pending?.message" class="text-balance">{{ pending.message }}</DialogDescription>
      </DialogHeader>
      <DialogFooter class="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-2">
        <Button variant="outline" @click="answer(false)">{{ pending?.cancelLabel ?? t("Cancel") }}</Button>
        <Button :variant="pending?.danger ? 'destructive' : 'default'" data-confirm @click="answer(true)">
          {{ pending?.confirmLabel ?? t("Confirm") }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
