// The app's own "are you sure?" dialog, instead of the browser's confirm() pop-up.
// `await confirm({...})` resolves true when the person presses the confirm button, false otherwise.
// One <ConfirmDialog /> in the layout shows whichever question is pending.

export interface ConfirmRequest {
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** red confirm button for things that delete or cannot be undone */
  danger?: boolean;
}

interface Pending extends ConfirmRequest {
  resolve: (ok: boolean) => void;
}

export function useConfirm() {
  const pending = useState<Pending | null>("confirm.pending", () => null);

  function confirm(req: ConfirmRequest): Promise<boolean> {
    pending.value?.resolve(false); // a new question replaces an unanswered one
    return new Promise((resolve) => {
      pending.value = { ...req, resolve };
    });
  }

  function answer(ok: boolean) {
    const p = pending.value;
    pending.value = null;
    p?.resolve(ok);
  }

  return { pending, confirm, answer };
}
