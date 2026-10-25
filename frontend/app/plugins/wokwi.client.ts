// Registers the open-source Wokwi part drawings (MIT, github.com/wokwi/wokwi-elements) as web components,
// so a catalog model with a Wokwi part shows the real-looking part: <wokwi-dht22>, <wokwi-hc-sr04>, <wokwi-servo>...
// Loaded only in the browser; the module is fetched the first time a page needs it.
export default defineNuxtPlugin(() => {
  import("@wokwi/elements");
});
