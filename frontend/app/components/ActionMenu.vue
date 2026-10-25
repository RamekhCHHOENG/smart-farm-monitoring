<script lang="ts">
// a plain counter: every menu on the page gets its own id (useId() can repeat inside lists)
let seq = 0;
const nextMenuId = () => `menu-${++seq}`;
</script>

<script setup lang="ts">
import { Ellipsis } from "lucide-vue-next";
import type { MenuAction } from "~/utils/menu";

// A "⋯" button that opens a short list of actions, so rows and cards show one button instead of five.
// The list is drawn on top of the page (not inside the row), so a table's scroll area cannot cut it off.
const props = defineProps<{ actions: MenuAction[]; label: string }>();

// only one menu is open at a time across the page: opening one closes the others
const openId = useState<string | null>("actionMenu.open", () => null);
const myId = nextMenuId();
const open = computed({
  get: () => openId.value === myId,
  set: (v: boolean) => { if (v) openId.value = myId; else if (openId.value === myId) openId.value = null; },
});
const button = ref<HTMLElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const pos = ref({ top: 0, left: 0, up: false });

// The list stays next to its button: it follows when the page scrolls and closes only when the button leaves the screen.
function place() {
  if (!open.value || !button.value) return;
  const r = button.value.getBoundingClientRect();
  if (r.bottom < 0 || r.top > window.innerHeight) { open.value = false; return; }
  const up = r.bottom + 260 > window.innerHeight && r.top > 260;
  pos.value = { top: up ? r.top - 6 : r.bottom + 6, left: Math.max(8, r.right - 208), up };
}
async function toggle() {
  open.value = !open.value;
  if (!open.value || !button.value) return;
  place();
  await nextTick();
  menu.value?.querySelector<HTMLElement>("[role=menuitem]")?.focus({ preventScroll: true });
}
function pick(a: MenuAction) {
  open.value = false;
  a.run();
}
function onKey(e: KeyboardEvent) {
  if (!open.value) return;
  if (e.key === "Escape") { open.value = false; button.value?.focus(); }
  if (e.key === "ArrowDown" || e.key === "ArrowUp") {
    const items = [...(menu.value?.querySelectorAll<HTMLElement>("[role=menuitem]") ?? [])];
    const i = items.indexOf(document.activeElement as HTMLElement);
    items[(i + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length]?.focus();
    e.preventDefault();
  }
}
function onDoc(e: Event) {
  const t = e.target as Node;
  if (open.value && !menu.value?.contains(t) && !button.value?.contains(t)) open.value = false;
}
onMounted(() => {
  document.addEventListener("mousedown", onDoc);
  document.addEventListener("keydown", onKey);
  window.addEventListener("scroll", place, { passive: true, capture: true });
  window.addEventListener("resize", place);
});
onBeforeUnmount(() => {
  document.removeEventListener("mousedown", onDoc);
  document.removeEventListener("keydown", onKey);
  window.removeEventListener("scroll", place, { capture: true });
  window.removeEventListener("resize", place);
});
</script>

<template>
  <button v-if="props.actions.length" ref="button" type="button" :aria-label="label" aria-haspopup="menu" :aria-expanded="open"
    class="inline-flex size-8 items-center justify-center rounded-full border bg-card text-muted-foreground transition-colors hover:bg-accent hover:text-foreground active:scale-95"
    @click="toggle">
    <Ellipsis class="size-4" />
  </button>
  <Teleport to="body">
    <Transition name="pop">
      <div v-if="open" ref="menu" role="menu" :aria-label="label"
        class="fixed z-[60] w-52 rounded-2xl border bg-popover p-1.5 text-sm shadow-soft"
        :class="pos.up ? 'origin-bottom-right -translate-y-full' : 'origin-top-right'"
        :style="{ top: `${pos.top}px`, left: `${pos.left}px` }">
        <template v-for="a in props.actions" :key="a.label">
          <div v-if="a.separated" class="my-1 h-px bg-border" />
          <button type="button" role="menuitem"
            class="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left outline-none transition-colors hover:bg-accent focus-visible:bg-accent"
            :class="a.danger ? 'text-destructive' : ''" @click="pick(a)">
            <component :is="a.icon" v-if="a.icon" class="size-4 shrink-0 opacity-80" />{{ a.label }}
          </button>
        </template>
      </div>
    </Transition>
  </Teleport>
</template>
