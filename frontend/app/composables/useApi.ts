/** Error text from a Spring ProblemDetail response, or a generic fallback. */
export function errorMessage(e: unknown): string {
  const err = e as { data?: { detail?: string }; message?: string };
  return err?.data?.detail || err?.message || "Something went wrong";
}

/** $fetch with the login token attached. A 401 sends the user back to the login page. */
export function useApi() {
  const { token, clear } = useAuth();

  return <T>(url: string, options: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(url, {
      ...options,
      headers: { ...(options.headers as Record<string, string>), Authorization: `Bearer ${token.value ?? ""}` },
      async onResponseError({ response }) {
        if (response.status === 401) {
          clear();
          await navigateTo("/login");
        }
      },
    }) as Promise<T>;
}

/** Re-runs a loader every few seconds while the page is open (live dashboard). */
export function usePolling(load: () => Promise<void>, intervalMs = 4000) {
  let timer: ReturnType<typeof setInterval> | undefined;
  onMounted(() => {
    load();
    timer = setInterval(load, intervalMs);
  });
  onBeforeUnmount(() => clearInterval(timer));
}
