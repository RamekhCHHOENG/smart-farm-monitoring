<script setup lang="ts">
// A drawing of the physical device, moving with its live state: the soil darkens as it gets wetter, the
// thermometer rises, the fan spins, water flows through the pump's pipe, the heater glows...
// Used by the Farm Lab and the Devices page. Pure SVG + CSS, no image files.

const props = withDefaults(defineProps<{
  type: string;
  /** latest value of a sensor */
  value?: number | null;
  /** an actuator's relay is on */
  on?: boolean;
  /** an actuator runs but does nothing (clogged pipe, burnt element) */
  broken?: boolean;
  /** no signal: unplugged, offline */
  offline?: boolean;
  /** reported faulty or under repair */
  fault?: boolean;
  /** for a kind without its own drawing (CO2, pH, a custom actuator...): draw a sensor board or a switched device */
  kind?: "sensor" | "actuator";
}>(), { value: null, on: false, broken: false, offline: false, fault: false, kind: "sensor" });

const KNOWN = ["SOIL_MOISTURE", "TEMPERATURE", "FEED_LEVEL", "HUMIDITY", "LIGHT_LEVEL", "WATER_LEVEL",
  "WATER_PUMP", "FAN", "FEEDER", "HEATER", "MISTER", "LIGHT", "WATER_VALVE"];
const generic = computed(() => !KNOWN.includes(props.type));

const v = computed(() => props.value ?? 0);
const clamp01 = (x: number) => Math.min(1, Math.max(0, x));
const pct = computed(() => clamp01(v.value / 100));

/** Mixes two #rrggbb colors; t = 0 gives a, 1 gives b. */
function mix(a: string, b: string, t: number) {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  return `rgb(${pa.map((c, i) => Math.round(c + (pb[i]! - c) * clamp01(t))).join(",")})`;
}

// Sensors
const soilColor = computed(() => mix("#D9B47C", "#3E2A1A", pct.value));
const tempLevel = computed(() => clamp01(v.value / 50)); // 0-50 °C on the tube
const tempColor = computed(() => mix("#3B82F6", "#EF4444", tempLevel.value));
const needle = computed(() => -120 + pct.value * 240); // humidity dial, degrees
const lux = computed(() => clamp01(v.value / 30000));
const sunColor = computed(() => mix("#9CA3AF", "#FBBF24", lux.value));
</script>

<template>
  <svg viewBox="0 0 200 150" class="device-art" :class="{ off: offline, fault }" role="img"
    :aria-label="`${type.toLowerCase().replace('_', ' ')}${on ? ', running' : ''}${offline ? ', no signal' : ''}`">
    <!-- ground shadow -->
    <ellipse cx="100" cy="140" rx="70" ry="6" fill="#000" opacity="0.08" />

    <!-- ===== Sensors ===== -->
    <g v-if="type === 'SOIL_MOISTURE'">
      <path d="M50 70 L150 70 L138 136 L62 136 Z" fill="#C2693F" />
      <rect x="44" y="62" width="112" height="14" rx="4" fill="#D97B4C" />
      <path d="M54 76 L146 76 L144 88 L56 88 Z" :fill="soilColor" />
      <g v-if="pct > 0.55" fill="#7CC4F0">
        <ellipse cx="76" cy="81" rx="3" ry="1.6" /><ellipse cx="118" cy="83" rx="4" ry="1.8" /><ellipse cx="97" cy="80" rx="2.5" ry="1.3" />
      </g>
      <!-- seedling, drooping when dry -->
      <path d="M78 76 C78 60 78 52 78 46" stroke="#4E8B3A" stroke-width="3" fill="none" />
      <path :d="pct > 0.3 ? 'M78 52 C66 44 60 46 58 50 C64 54 72 54 78 52' : 'M78 54 C70 58 64 62 62 66 C68 64 74 60 78 54'" fill="#6DB34F" />
      <path :d="pct > 0.3 ? 'M78 48 C88 38 96 40 98 44 C92 48 84 50 78 48' : 'M78 50 C86 56 92 60 94 64 C88 62 82 58 78 50'" fill="#6DB34F" />
      <!-- capacitive probe -->
      <rect x="112" y="40" width="22" height="16" rx="3" fill="#1F3A2B" />
      <circle cx="129" cy="45" r="2.2" :fill="offline ? '#6B7280' : '#4ADE80'" />
      <path d="M116 56 L116 100 L120 106 L124 100 L124 56 Z" fill="#2D5A3D" />
      <path d="M126 56 L126 98 L129 103 L132 98 L132 56 Z" fill="#2D5A3D" opacity="0.85" />
      <path d="M123 40 C123 28 140 26 150 22" stroke="#111" stroke-width="2" fill="none" />
    </g>

    <g v-else-if="type === 'TEMPERATURE'">
      <rect x="60" y="22" width="80" height="112" rx="10" fill="#F4F7F5" stroke="#CBD5CF" stroke-width="2" />
      <rect x="92" y="32" width="16" height="78" rx="8" fill="#E5E7EB" />
      <rect x="95" :y="32 + 75 * (1 - tempLevel)" width="10" :height="6 + 75 * tempLevel" rx="5" :fill="tempColor" class="liquid" />
      <circle cx="100" cy="116" r="12" :fill="tempColor" />
      <g stroke="#94A3B8" stroke-width="1.5">
        <line v-for="i in 6" :key="i" x1="74" :x2="i % 2 ? 84 : 80" :y1="34 + (i - 1) * 15" :y2="34 + (i - 1) * 15" />
      </g>
      <g fill="#64748B" font-size="8" font-family="sans-serif">
        <text x="114" y="37">50</text><text x="114" y="72">25</text><text x="114" y="107">0</text>
      </g>
    </g>

    <g v-else-if="type === 'FEED_LEVEL'">
      <path d="M38 80 L162 80 L150 128 L50 128 Z" fill="#A16207" />
      <path d="M44 84 L156 84 L146 124 L54 124 Z" fill="#3F2A12" />
      <path :d="`M${54 + (1 - pct) * 10} ${124 - 36 * pct} Q100 ${118 - 46 * pct} ${146 - (1 - pct) * 10} ${124 - 36 * pct} L146 124 L54 124 Z`" fill="#E8B84B" />
      <g fill="#C98E1E" v-if="pct > 0.1">
        <circle v-for="i in 9" :key="i" :cx="62 + i * 8" :cy="122 - 30 * pct + (i % 3) * 3" r="1.8" />
      </g>
      <rect x="34" y="74" width="132" height="8" rx="3" fill="#B45309" />
      <rect x="40" y="128" width="8" height="10" fill="#78350F" /><rect x="152" y="128" width="8" height="10" fill="#78350F" />
    </g>

    <g v-else-if="type === 'HUMIDITY'">
      <circle cx="100" cy="78" r="56" fill="#F4F7F5" stroke="#CBD5CF" stroke-width="3" />
      <path d="M58 106 A48 48 0 1 1 142 106" fill="none" stroke="#E5E7EB" stroke-width="10" stroke-linecap="round" />
      <path d="M58 106 A48 48 0 0 1 64 50" fill="none" stroke="#F59E0B" stroke-width="10" stroke-linecap="round" />
      <path d="M136 50 A48 48 0 0 1 142 106" fill="none" stroke="#38BDF8" stroke-width="10" stroke-linecap="round" />
      <g :transform="`rotate(${needle} 100 78)`" class="needle">
        <path d="M100 36 L104 78 L96 78 Z" fill="#1F2937" />
      </g>
      <circle cx="100" cy="78" r="7" fill="#1F2937" />
      <path d="M100 104 C94 112 94 118 100 120 C106 118 106 112 100 104 Z" fill="#38BDF8" />
    </g>

    <g v-else-if="type === 'LIGHT_LEVEL'">
      <g :opacity="0.25 + 0.75 * lux">
        <line v-for="i in 8" :key="i" x1="100" y1="58" x2="100" y2="22" stroke="#FBBF24" stroke-width="4" stroke-linecap="round"
          :transform="`rotate(${i * 45} 100 58) translate(0 ${-4 * lux})`" />
      </g>
      <circle cx="100" cy="58" r="20" :fill="sunColor" />
      <rect x="70" y="96" width="60" height="38" rx="6" fill="#1F3A2B" />
      <circle cx="100" cy="104" r="6" fill="#D1FAE5" opacity="0.9" />
      <rect x="80" y="116" width="40" height="10" rx="2" fill="#0F1A14" />
      <rect x="82" y="118" :width="36 * lux" height="6" rx="1" fill="#4ADE80" />
    </g>

    <g v-else-if="type === 'WATER_LEVEL'">
      <ellipse cx="100" cy="30" rx="44" ry="10" fill="#94A3B8" />
      <rect x="56" y="30" width="88" height="100" fill="#CBD5E1" />
      <clipPath id="tankClip"><rect x="58" y="32" width="84" height="96" /></clipPath>
      <g clip-path="url(#tankClip)">
        <rect x="58" :y="128 - 94 * pct" width="84" :height="94 * pct + 2" fill="#3B82F6" opacity="0.85" class="liquid" />
        <path :d="`M40 ${128 - 94 * pct} q10 -4 20 0 t20 0 t20 0 t20 0 t20 0 t20 0 v8 h-120 z`" fill="#60A5FA" class="wave" />
      </g>
      <ellipse cx="100" cy="130" rx="44" ry="10" fill="#94A3B8" />
      <g stroke="#475569" stroke-width="1.5"><line x1="146" y1="50" x2="152" y2="50" /><line x1="146" y1="80" x2="152" y2="80" /><line x1="146" y1="110" x2="152" y2="110" /></g>
    </g>

    <!-- ===== Actuators ===== -->
    <g v-else-if="type === 'WATER_PUMP'">
      <rect x="24" y="74" width="54" height="40" rx="8" fill="#2563EB" :class="{ shake: on }" />
      <rect x="30" y="80" width="10" height="28" rx="2" fill="#1D4ED8" />
      <circle cx="96" cy="94" r="24" fill="#3B82F6" :class="{ shake: on }" />
      <circle cx="96" cy="94" r="9" fill="#1E3A8A" />
      <path d="M96 70 L96 46 L170 46 L170 120" fill="none" stroke="#64748B" stroke-width="12" stroke-linejoin="round" />
      <path v-if="on" d="M96 70 L96 46 L170 46 L170 120" fill="none" :stroke="broken ? '#94A3B8' : '#7DD3FC'" stroke-width="5"
        stroke-dasharray="8 8" :class="{ flow: !broken }" stroke-linejoin="round" />
      <g v-if="on && !broken" fill="#7DD3FC" class="drip"><ellipse cx="170" cy="128" rx="3" ry="4" /><ellipse cx="170" cy="138" rx="2.5" ry="3.5" /></g>
      <g v-if="broken"><circle cx="134" cy="46" r="10" fill="#DC2626" /><path d="M129 41 L139 51 M139 41 L129 51" stroke="#fff" stroke-width="3" /></g>
      <rect x="20" y="114" width="100" height="8" rx="2" fill="#475569" />
    </g>

    <g v-else-if="type === 'FAN'">
      <rect x="44" y="18" width="112" height="112" rx="14" fill="#E5E7EB" stroke="#9CA3AF" stroke-width="3" />
      <circle cx="100" cy="74" r="46" fill="#F9FAFB" stroke="#D1D5DB" stroke-width="2" />
      <g :class="{ spin: on && !broken }" style="transform-origin: 100px 74px">
        <path v-for="i in 4" :key="i" d="M100 74 C92 50 108 34 116 40 C120 52 110 64 100 74 Z" fill="#0EA5E9" :transform="`rotate(${i * 90} 100 74)`" />
      </g>
      <circle cx="100" cy="74" r="7" fill="#334155" />
      <g v-if="on && !broken" stroke="#7DD3FC" stroke-width="3" stroke-linecap="round" fill="none" class="wind">
        <path d="M162 50 q10 -4 22 0" /><path d="M162 74 q12 -4 26 0" /><path d="M162 98 q10 -4 22 0" />
      </g>
    </g>

    <g v-else-if="type === 'FEEDER'">
      <path d="M62 14 L138 14 L114 66 L86 66 Z" fill="#D97706" />
      <path d="M68 20 L132 20 L111 60 L89 60 Z" fill="#E8B84B" />
      <rect x="92" y="66" width="16" height="14" fill="#92400E" />
      <g v-if="on && !broken" fill="#E8B84B" class="fall">
        <circle cx="96" cy="86" r="2" /><circle cx="103" cy="92" r="2" /><circle cx="99" cy="100" r="2" /><circle cx="105" cy="84" r="2" />
      </g>
      <path d="M50 106 L150 106 L142 132 L58 132 Z" fill="#A16207" />
      <path d="M58 110 L142 110 L136 128 L64 128 Z" fill="#E8B84B" />
    </g>

    <g v-else-if="type === 'HEATER'">
      <rect x="40" y="40" width="120" height="84" rx="10" fill="#4B5563" />
      <rect v-for="i in 6" :key="i" :x="48 + (i - 1) * 18" y="50" width="12" height="64" rx="5"
        :fill="on && !broken ? '#F97316' : '#9CA3AF'" :class="{ glow: on && !broken }" />
      <g v-if="on && !broken" stroke="#FB923C" stroke-width="3" fill="none" stroke-linecap="round" class="heat">
        <path d="M70 34 q-6 -8 0 -16 q6 -8 0 -16" /><path d="M100 34 q-6 -8 0 -16 q6 -8 0 -16" /><path d="M130 34 q-6 -8 0 -16 q6 -8 0 -16" />
      </g>
      <rect x="48" y="124" width="10" height="10" fill="#374151" /><rect x="142" y="124" width="10" height="10" fill="#374151" />
      <circle cx="150" cy="48" r="3" :fill="on ? '#F87171' : '#6B7280'" />
    </g>

    <g v-else-if="type === 'MISTER'">
      <rect x="20" y="30" width="160" height="10" rx="5" fill="#64748B" />
      <g v-for="x in [52, 100, 148]" :key="x">
        <rect :x="x - 5" y="40" width="10" height="14" rx="2" fill="#334155" />
        <g v-if="on && !broken" fill="#BAE6FD" class="mist">
          <circle :cx="x" cy="66" r="5" /><circle :cx="x - 10" cy="80" r="6" /><circle :cx="x + 10" cy="82" r="6" />
          <circle :cx="x - 4" cy="98" r="7" /><circle :cx="x + 6" cy="110" r="5" />
        </g>
      </g>
      <path d="M30 138 L170 138" stroke="#4E8B3A" stroke-width="4" />
      <path d="M60 138 C60 124 66 118 70 116 M100 138 C100 120 104 114 110 112 M140 138 C140 126 134 120 130 118" stroke="#6DB34F" stroke-width="3" fill="none" />
    </g>

    <g v-else-if="type === 'LIGHT'">
      <line x1="100" y1="0" x2="100" y2="22" stroke="#334155" stroke-width="3" />
      <path d="M78 22 L122 22 L130 40 L70 40 Z" fill="#334155" />
      <circle v-if="on && !broken" cx="100" cy="70" r="52" fill="#FDE68A" opacity="0.35" class="glow" />
      <path d="M84 40 C70 54 72 82 86 94 L114 94 C128 82 130 54 116 40 Z" :fill="on && !broken ? '#FDE047' : '#E5E7EB'" stroke="#CBD5E1" stroke-width="2" />
      <path d="M92 60 Q100 72 108 60" stroke="#B45309" stroke-width="2" fill="none" />
      <rect x="86" y="94" width="28" height="14" rx="3" fill="#94A3B8" />
      <g v-if="on && !broken" stroke="#FBBF24" stroke-width="3" stroke-linecap="round">
        <line x1="40" y1="70" x2="56" y2="70" /><line x1="144" y1="70" x2="160" y2="70" /><line x1="56" y1="112" x2="66" y2="102" /><line x1="144" y1="112" x2="134" y2="102" />
      </g>
    </g>

    <g v-else-if="type === 'WATER_VALVE'">
      <path d="M10 96 L190 96" stroke="#64748B" stroke-width="16" />
      <path v-if="on && !broken" d="M10 96 L190 96" stroke="#7DD3FC" stroke-width="6" stroke-dasharray="8 8" class="flow" />
      <rect x="82" y="78" width="36" height="36" rx="6" fill="#B91C1C" />
      <rect x="96" y="54" width="8" height="26" fill="#475569" />
      <g :class="{ turn: on }" style="transform-origin: 100px 46px">
        <circle cx="100" cy="46" r="20" fill="none" stroke="#DC2626" stroke-width="6" />
        <path d="M100 26 L100 66 M80 46 L120 46" stroke="#DC2626" stroke-width="4" />
      </g>
      <g v-if="broken"><circle cx="150" cy="96" r="10" fill="#DC2626" /><path d="M145 91 L155 101 M155 91 L145 101" stroke="#fff" stroke-width="3" /></g>
    </g>

    <!-- ===== Any other kind (catalog measurements like CO2 or pH, custom actuators) ===== -->
    <g v-if="generic && kind === 'sensor'">
      <rect x="54" y="36" width="92" height="76" rx="8" fill="#1F6F43" />
      <rect x="62" y="44" width="76" height="40" rx="4" fill="#0F1A14" />
      <text x="100" y="70" text-anchor="middle" font-family="monospace" font-size="16" fill="#4ADE80">{{ value === null ? "--" : Math.round(v * 10) / 10 }}</text>
      <rect x="70" y="92" width="22" height="12" rx="2" fill="#111827" />
      <circle cx="126" cy="98" r="4" :fill="offline ? '#6B7280' : '#4ADE80'" />
      <g fill="#C0A062"><rect x="64" y="112" width="4" height="16" /><rect x="76" y="112" width="4" height="16" /><rect x="88" y="112" width="4" height="16" /></g>
    </g>
    <g v-else-if="generic">
      <rect x="44" y="40" width="112" height="84" rx="12" fill="#475569" />
      <rect x="62" y="62" width="40" height="40" rx="20" :fill="on && !broken ? '#22C55E' : '#94A3B8'" :class="{ glow: on && !broken }" />
      <rect x="116" y="58" width="24" height="48" rx="12" fill="#1F2937" />
      <circle cx="128" :cy="on ? 70 : 94" r="9" fill="#F1F5F9" class="liquid" />
      <path d="M100 124 L100 142" stroke="#64748B" stroke-width="6" />
    </g>

    <!-- unplugged: a cut cable sign -->
    <g v-if="offline">
      <circle cx="176" cy="22" r="14" fill="#6B7280" />
      <path d="M169 22 h5 M179 22 h5 M174 17 l5 10" stroke="#fff" stroke-width="2.5" stroke-linecap="round" />
    </g>
    <g v-else-if="fault">
      <path d="M176 8 L191 34 L161 34 Z" fill="#DC2626" />
      <rect x="174.5" y="16" width="3" height="10" fill="#fff" /><rect x="174.5" y="28" width="3" height="3" fill="#fff" />
    </g>
  </svg>
</template>

<style scoped>
.device-art { width: 100%; height: auto; display: block; }
.device-art.off { filter: grayscale(1); opacity: 0.55; }
.liquid, .needle { transition: all 0.8s ease; }
.spin { animation: spin 0.6s linear infinite; }
.turn { animation: spin 3s linear infinite; }
.flow { animation: flow 0.6s linear infinite; }
.shake { animation: shake 0.15s linear infinite; }
.drip { animation: drip 0.8s ease-in infinite; }
.fall { animation: fall 0.7s linear infinite; }
.mist { animation: mist 1.4s ease-out infinite; transform-box: fill-box; transform-origin: top; }
.heat, .wind { animation: rise 1.2s ease-in-out infinite; }
.glow { animation: glow 1.6s ease-in-out infinite; }
.wave { animation: wave 2s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes flow { to { stroke-dashoffset: -16; } }
@keyframes shake { 0%, 100% { transform: translate(0, 0); } 50% { transform: translate(0.8px, -0.6px); } }
@keyframes drip { from { transform: translateY(-6px); opacity: 1; } to { transform: translateY(6px); opacity: 0; } }
@keyframes fall { from { transform: translateY(-8px); opacity: 1; } to { transform: translateY(16px); opacity: 0.2; } }
@keyframes mist { from { opacity: 0.9; transform: scale(0.6); } to { opacity: 0; transform: scale(1.3); } }
@keyframes rise { 0% { transform: translateY(4px); opacity: 0.3; } 50% { opacity: 1; } 100% { transform: translateY(-6px); opacity: 0.2; } }
@keyframes glow { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }
@keyframes wave { to { transform: translateX(-40px); } }
@media (prefers-reduced-motion: reduce) {
  .spin, .turn, .flow, .shake, .drip, .fall, .mist, .heat, .wind, .glow, .wave { animation: none; }
}
</style>
