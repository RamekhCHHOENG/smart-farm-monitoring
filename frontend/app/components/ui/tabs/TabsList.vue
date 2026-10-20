<script setup lang="ts">
import type { TabsListProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import { reactiveOmit } from "@vueuse/core";
import { TabsList } from "reka-ui";
import { cn } from "@/lib/utils";

const props = withDefaults(
  defineProps<
    TabsListProps & {
      variant?: "default" | "line";
      class?: HTMLAttributes["class"];
    }
  >(),
  {
    variant: "default",
  }
);

const delegatedProps = reactiveOmit(props, "class", "variant");
</script>

<template>
  <TabsList
    data-slot="tabs-list"
    :data-variant="variant"
    v-bind="delegatedProps"
    :class="
      cn(
        'group/tabs-list inline-flex w-fit items-center justify-center rounded-lg p-[3px] text-muted-foreground',
        variant === 'line' ? 'gap-1 bg-transparent rounded-none' : 'bg-muted',
        props.class
      )
    "
  >
    <slot />
  </TabsList>
</template>
