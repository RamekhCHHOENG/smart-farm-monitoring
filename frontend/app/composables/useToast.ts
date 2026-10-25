// Short messages that confirm what just happened ("Zone deleted"), shown at the bottom of the screen wherever the
// person is on the page. Successes fade after 5 s; errors stay until closed.
export interface Toast { id: number; kind: "success" | "error"; text: string }

let next = 1;

export function useToast() {
  const toasts = useState<Toast[]>("toasts", () => []);
  function push(kind: Toast["kind"], text: string) {
    const id = next++;
    toasts.value = [...toasts.value.filter((x) => x.text !== text), { id, kind, text }].slice(-4);
    if (kind === "success") setTimeout(() => dismiss(id), 5000);
  }
  function dismiss(id: number) {
    toasts.value = toasts.value.filter((x) => x.id !== id);
  }
  return { toasts, success: (t: string) => push("success", t), error: (t: string) => push("error", t), dismiss };
}
