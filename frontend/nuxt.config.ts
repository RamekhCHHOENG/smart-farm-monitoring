import tailwindcss from "@tailwindcss/vite";

// The browser only talks to Nuxt; Nuxt forwards /api/** to the Spring Boot backend.
// This keeps one origin (no CORS) and keeps the backend address out of the browser.
const backendUrl = process.env.NUXT_BACKEND_URL || "http://localhost:8080";

export default defineNuxtConfig({
  compatibilityDate: "2026-10-01",
  devtools: { enabled: false },
  ssr: false,

  app: {
    head: {
      title: "Smart Farm Monitoring",
      meta: [{ name: "description", content: "Agri-IoT dashboard for crop and poultry zones" }],
      link: [{ rel: "icon", type: "image/svg+xml", href: "/favicon.svg" }],
    },
  },

  css: ["~/assets/css/tailwind.css"],

  vite: {
    plugins: [tailwindcss()],
  },

  // designkit components are imported explicitly from ~/components/ui
  components: [{ path: "~/components", pathPrefix: false, ignore: ["ui/**"] }],

  routeRules: {
    "/api/**": { proxy: `${backendUrl}/api/**` },
  },

  devServer: {
    port: 3000,
  },
});
