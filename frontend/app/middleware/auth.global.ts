import type { Role } from "~/utils/types";

// Signed-out people go to the login page (the invite page is open to anyone holding a link). Company staff need a
// farm chosen before farm pages; company pages are for company staff only. Pages for some roles only
// (definePageMeta({ role })) send everyone else away with a short note saying why, instead of showing buttons
// the server would refuse.
export default defineNuxtRouteMiddleware((to) => {
  const { token, can, isCompany, activeFarm } = useAuth();
  const notice = useState<string>("pageNotice", () => "");
  if (to.path.startsWith("/invite/")) {
    return;
  }
  if (!token.value && to.path !== "/login") {
    return navigateTo("/login");
  }
  if (token.value && to.path === "/login") {
    return navigateTo(isCompany.value ? "/company" : "/");
  }
  const companyPage = to.path === "/company" || to.path.startsWith("/company/");
  if (companyPage && !isCompany.value) {
    notice.value = "That page is for Smart Farm company staff.";
    return navigateTo("/");
  }
  if (!companyPage && isCompany.value && !activeFarm.value) {
    return navigateTo("/company"); // choose a customer's farm first
  }
  if (companyPage && to.meta.companyAdmin && useAuth().user.value?.companyRole !== "ADMIN") {
    notice.value = "That page is for a company admin.";
    return navigateTo("/company");
  }
  const role = to.meta.role as Role | undefined;
  if (role && token.value && !can(role)) {
    notice.value = role === "OWNER"
      ? "That page is for the Farm Owner." : "That page is for the Technician and the Farm Owner.";
    return navigateTo(isCompany.value && !activeFarm.value ? "/company" : "/");
  }
});
