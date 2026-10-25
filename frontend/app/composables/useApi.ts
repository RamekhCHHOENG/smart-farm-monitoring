/** Error text a farm worker can act on: the server's own explanation, or what went wrong in plain words. */
export function errorMessage(e: unknown): string {
  const err = e as { data?: { detail?: string }; statusCode?: number; message?: string };
  if (err?.data?.detail) return err.data.detail;
  if (!err?.statusCode) return "Cannot reach the server. Check that the backend is running and your network is on.";
  if (err.statusCode >= 500) return "The server had a problem. Try again in a moment.";
  return err.message || "Something went wrong";
}

/** $fetch with the login token attached. A 401 sends the user back to the login page. */
export function useApi() {
  const { token, clear, isCompany, activeFarm, setActiveFarm } = useAuth();

  return <T>(url: string, options: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(url, {
      ...options,
      headers: {
        ...(options.headers as Record<string, string>),
        Authorization: `Bearer ${token.value ?? ""}`,
        // company staff name the customer's farm they are working on
        ...(isCompany.value && activeFarm.value ? { "X-Farm-Id": String(activeFarm.value.id) } : {}),
      },
      async onResponseError({ response }) {
        if (response.status === 401) {
          clear();
          await navigateTo({ path: "/login", query: { expired: "1" } });
        }
        // company staff who may no longer open this farm (owner switched support off, or unassigned): back to the farm list
        const detail = (response._data as { detail?: string } | undefined)?.detail ?? "";
        if (response.status === 403 && isCompany.value && activeFarm.value && /support access|not assigned/.test(detail)) {
          setActiveFarm(null);
          useState<string>("pageNotice", () => "").value = detail;
          await navigateTo("/company");
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
