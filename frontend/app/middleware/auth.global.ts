export default defineNuxtRouteMiddleware((to) => {
  const { token } = useAuth();
  if (!token.value && to.path !== "/login") {
    return navigateTo("/login");
  }
  if (token.value && to.path === "/login") {
    return navigateTo("/");
  }
});
