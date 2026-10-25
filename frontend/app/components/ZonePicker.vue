<script setup lang="ts">
import { CornerDownLeft, Search, X } from "lucide-vue-next";
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog";

// "Go to a zone": a search box over every zone, for farms with hundreds or thousands of them. Type part of a name,
// a crop or a kind; arrows move, Enter opens. Ctrl/⌘+K opens it from anywhere.
interface PickZone { id: number; name: string; type: string; detail?: string | null; cropName?: string | null; lab?: boolean }
const props = withDefaults(defineProps<{ zones: PickZone[]; currentId?: number | null }>(), { currentId: null });
const open = defineModel<boolean>("open", { default: false });

const query = ref("");
const active = ref(0);
const LIMIT = 50;
const found = computed(() => {
  // every word must appear somewhere (name, crop, detail or kind), in any order: "pepper 1" finds "Field 11 · Pepper"
  const words = query.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const all = props.zones.filter((z) => {
    const text = `${z.name} ${z.cropName ?? ""} ${z.detail ?? ""} ${zoneLook(z).kind}`.toLowerCase();
    return words.every((w) => text.includes(w));
  });
  return { list: all.slice(0, LIMIT), more: Math.max(0, all.length - LIMIT), total: all.length };
});
watch(query, () => (active.value = 0));
watch(open, (v) => { if (v) { query.value = ""; active.value = 0; } });

const router = useRouter();
function go(id: number) {
  open.value = false;
  router.push(`/zone/${id}`);
}
function onKey(e: KeyboardEvent) {
  const n = found.value.list.length;
  if (e.key === "ArrowDown") { active.value = (active.value + 1) % Math.max(1, n); e.preventDefault(); }
  if (e.key === "ArrowUp") { active.value = (active.value - 1 + n) % Math.max(1, n); e.preventDefault(); }
  if (e.key === "Enter" && found.value.list[active.value]) go(found.value.list[active.value]!.id);
}
watch(active, async () => {
  await nextTick();
  document.querySelector(`[data-zone-option="${active.value}"]`)?.scrollIntoView({ block: "nearest" });
});
function global(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") { e.preventDefault(); open.value = !open.value; }
}
onMounted(() => window.addEventListener("keydown", global));
onBeforeUnmount(() => window.removeEventListener("keydown", global));
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent class="gap-0 overflow-hidden p-0 sm:max-w-lg" :show-close-button="false">
      <DialogTitle class="sr-only">{{ t("Go to a zone") }}</DialogTitle>
      <div class="flex items-center gap-2 border-b px-4">
        <Search class="size-4 shrink-0 text-muted-foreground" />
        <input v-model="query" autofocus :placeholder="t('Find a zone: name, crop, poultry…')" :aria-label="t('Find a zone')"
          class="h-13 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-muted-foreground" @keydown="onKey">
        <button v-if="query" type="button" class="rounded-full p-1 text-muted-foreground hover:bg-muted" :aria-label="t('Clear')" @click="query = ''"><X class="size-4" /></button>
      </div>
      <ul class="max-h-[min(60vh,26rem)] overflow-y-auto p-2" role="listbox" :aria-label="t('Zones')">
        <li v-for="(z, i) in found.list" :key="z.id" role="option" :aria-selected="i === active" :data-zone-option="i"
          class="flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5"
          :class="i === active ? 'bg-accent text-accent-foreground' : ''" @mouseenter="active = i" @click="go(z.id)">
          <span class="size-2.5 shrink-0 rounded-full" :class="zoneLook(z).dot" />
          <div class="min-w-0 flex-1 leading-tight">
            <div class="truncate text-sm font-medium">{{ z.name }}<span v-if="z.id === currentId" class="ml-1.5 text-xs font-normal text-muted-foreground">({{ t("open now") }})</span></div>
            <div class="truncate text-xs text-muted-foreground">{{ t(zoneLook(z).kind) }}<template v-if="z.detail"> · {{ z.detail }}</template><template v-if="z.lab"> · LAB</template></div>
          </div>
          <CornerDownLeft v-if="i === active" class="size-4 shrink-0 opacity-60" />
        </li>
        <li v-if="!found.list.length" class="px-3 py-6 text-center text-sm text-muted-foreground">{{ t("No zone matches.") }}</li>
        <li v-if="found.more" class="px-3 py-2 text-center text-xs text-muted-foreground">+ {{ found.more }} {{ t("more: type more of the name") }}</li>
      </ul>
      <div class="flex justify-between border-t px-4 py-2 text-[11px] text-muted-foreground">
        <span>{{ found.total }} {{ t("zones") }}</span><span>↑↓ {{ t("move") }} · Enter {{ t("open") }} · Esc {{ t("close") }}</span>
      </div>
    </DialogContent>
  </Dialog>
</template>
