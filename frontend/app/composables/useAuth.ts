import type { Role, User } from "~/utils/types";

const TOKEN_KEY = "smartfarm.token";
const USER_KEY = "smartfarm.user";

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

  async function login(username: string, password: string) {
    const res = await $fetch<{ token: string; user: User }>("/api/auth/login", {
      method: "POST",
      body: { username, password },
    });
    token.value = res.token;
    user.value = res.user;
    write(TOKEN_KEY, res.token);
    write(USER_KEY, JSON.stringify(res.user));
  }

  function clear() {
    token.value = null;
    user.value = null;
    write(TOKEN_KEY, null);
    write(USER_KEY, null);
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

  /** Mirrors the backend AccessPolicy: the owner can do everything. */
  function can(...roles: Role[]) {
    return !!user.value && (user.value.role === "OWNER" || roles.includes(user.value.role));
  }

  return { token, user, login, logout, clear, can };
}
