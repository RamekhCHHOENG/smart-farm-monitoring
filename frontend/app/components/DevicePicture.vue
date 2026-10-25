<script setup lang="ts">
// The best picture of a device: the photo uploaded for its catalog model, else the model's open-source Wokwi part
// drawing (real-looking electronics), else our animated drawing of the farm equipment, else a generic board/switch.

const props = withDefaults(defineProps<{
  modelId?: number | null;
  hasPicture?: boolean;
  wokwiPart?: string | null;
  artType?: string | null;
  /** live kind of the channel (measurement code or actuator kind), used by the animated drawing */
  type?: string;
  kind?: "sensor" | "actuator";
  value?: number | null;
  on?: boolean;
  broken?: boolean;
  offline?: boolean;
  fault?: boolean;
  /** drawing height in px */
  height?: number;
}>(), { modelId: null, hasPicture: false, wokwiPart: null, artType: null, type: "", kind: "sensor",
  value: null, on: false, broken: false, offline: false, fault: false, height: 140 });

const photoFailed = ref(false);
watch(() => props.modelId, () => { photoFailed.value = false; });

// Wokwi parts come in very different sizes: measure the drawing once it is defined and scale it to fit the box.
const wokwiTag = computed(() => (props.wokwiPart ? `wokwi-${props.wokwiPart}` : null));
const box = ref<HTMLElement | null>(null);
const part = ref<HTMLElement | null>(null);
const wokwiScale = ref(1);
async function fit() {
  const tag = wokwiTag.value;
  if (!tag || !box.value || typeof customElements === "undefined") return;
  await customElements.whenDefined(tag);
  await nextTick();
  const el = part.value;
  if (!el) return;
  const prev = el.style.transform;
  el.style.transform = "none";
  const { width, height } = el.getBoundingClientRect();
  el.style.transform = prev;
  if (!width || !height) return;
  const room = box.value.getBoundingClientRect();
  wokwiScale.value = Math.min((room.width * 0.9) / width, (room.height * 0.88) / height, 3);
}
onMounted(fit);
watch(() => [props.wokwiPart, props.height], () => nextTick(fit));
// A few parts show their state: the servo turns, the LED lights, the buzzer sounds
const wokwiProps = computed(() => {
  if (props.wokwiPart === "servo") return { angle: props.on ? 90 : 0 };
  if (props.wokwiPart === "led") return { value: props.on, color: "green" };
  if (props.wokwiPart === "buzzer") return { hasSignal: props.on };
  return {};
});
const artKind = computed(() => props.artType ?? props.type);
</script>

<template>
  <div ref="box" class="relative flex items-center justify-center overflow-hidden" :style="{ height: `${height}px` }"
    :class="{ 'opacity-60 grayscale': offline && (hasPicture || wokwiTag) }">
    <img v-if="hasPicture && modelId && !photoFailed" :src="`/api/catalog/models/${modelId}/picture`" alt=""
      class="max-h-full max-w-full rounded-md object-contain" @error="photoFailed = true">
    <component :is="wokwiTag" v-else-if="wokwiTag" ref="part" v-bind="wokwiProps" class="shrink-0"
      :style="{ transform: `scale(${wokwiScale})`, transformOrigin: 'center' }" />
    <DeviceArt v-else :type="artKind" :kind="kind" :value="value" :on="on" :broken="broken" :offline="offline" :fault="fault"
      class="h-full w-auto max-w-full" />
    <span v-if="fault && (hasPicture || wokwiTag)" class="absolute top-1 right-1 rounded bg-destructive px-1.5 py-0.5 text-[10px] font-semibold text-white">FAULT</span>
    <span v-if="on && (hasPicture || wokwiTag)" class="absolute top-1 left-1 rounded bg-primary px-1.5 py-0.5 text-[10px] font-semibold text-primary-foreground">ON</span>
  </div>
</template>
