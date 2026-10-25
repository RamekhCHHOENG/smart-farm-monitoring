// The zones this person opened last (newest first), remembered in this browser. The sidebar lists these instead of
// every zone, which would not work for a farm with hundreds or thousands of zones.
const KEY = "smartfarm.recentZones";
const MAX = 5;

export function useRecentZones() {
  const ids = useState<number[]>("recentZones", () => {
    try {
      const v = JSON.parse(localStorage.getItem(KEY) ?? "[]");
      return Array.isArray(v) ? v.filter((x) => typeof x === "number").slice(0, MAX) : [];
    } catch {
      return [];
    }
  });
  function visit(id: number) {
    ids.value = [id, ...ids.value.filter((x) => x !== id)].slice(0, MAX);
    try {
      localStorage.setItem(KEY, JSON.stringify(ids.value));
    } catch {
      // blocked storage: the list just lasts until reload
    }
  }
  return { ids, visit };
}
