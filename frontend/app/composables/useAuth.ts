import type { ActiveFarm, Role, User } from "~/utils/types";

const TOKEN_KEY = "smartfarm.token";
const USER_KEY = "smartfarm.user";
const FARM_KEY = "smartfarm.activeFarm";

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // Storage can be blocked (private mode); the session then lasts until reload.
  }
}

/** Login state. The token is kept in localStorage so a page reload keeps you signed in. */
export function useAuth() {
  const token = useState<string | null>("auth.token", () => read(TOKEN_KEY));
  const user = useState<User | null>("auth.user", () => {
    const raw = read(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  });
  // Company staff work on one customer's farm at a time; every request then names it (X-Farm-Id, see useApi).
  const activeFarm = useState<ActiveFarm | null>("auth.activeFarm", () => {
    const raw = read(FARM_KEY);
    try {
      return raw ? (JSON.parse(raw) as ActiveFarm) : null;
    } catch {
      return null;
    }
  });
  const isCompany = computed(() => !!user.value?.companyRole);

  function setActiveFarm(farm: ActiveFarm | null) {
    activeFarm.value = farm;
    write(FARM_KEY, farm ? JSON.stringify(farm) : null);
  }

  /** Signs in from what the server returned (a login or an accepted invite link). */
  function signedIn(res: { token: string; user: User }) {
    token.value = res.token;
    user.value = res.user;
    write(TOKEN_KEY, res.token);
    write(USER_KEY, JSON.stringify(res.user));
    setActiveFarm(null);
  }

  async function login(username: string, password: string) {
    signedIn(await $fetch<{ token: string; user: User }>("/api/auth/login", { method: "POST", body: { username, password } }));
  }

  function clear() {
    token.value = null;
    user.value = null;
    write(TOKEN_KEY, null);
    write(USER_KEY, null);
    setActiveFarm(null);
  }

  async function logout() {
    if (token.value) {
      await $fetch("/api/auth/logout", {
        method: "POST",
        headers: { Authorization: `Bearer ${token.value}` },
      }).catch(() => undefined);
    }
    clear();
    await navigateTo("/login");
  }

  /** The role on the farm being worked on: a company admin works as its owner, company support as a technician. */
  const farmRole = computed<Role | null>(() => {
    if (!user.value) return null;
    if (!user.value.companyRole) return user.value.role;
    if (!activeFarm.value) return null;
    return user.value.companyRole === "ADMIN" ? "OWNER" : "TECHNICIAN";
  });

  /** Mirrors the backend AccessPolicy: the owner can do everything. */
  function can(...roles: Role[]) {
    const role = farmRole.value;
    return !!role && (role === "OWNER" || roles.includes(role));
  }

  return { token, user, login, signedIn, logout, clear, can, farmRole, isCompany, activeFarm, setActiveFarm };
}
