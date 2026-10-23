// Khmer / English for the screens farm staff use every day (dashboard, alerts, history, menus).
// Setup forms for the owner and technician stay in English, as do messages written by the server.
// Khmer wording should be checked by a native speaker before real use.

import { ref } from "vue";

export type Lang = "en" | "km";

const LANG_KEY = "smartfarm.lang";

function initialLang(): Lang {
  try {
    return localStorage.getItem(LANG_KEY) === "km" ? "km" : "en";
  } catch {
    return "en";
  }
}

export const lang = ref<Lang>(initialLang());

export function setLang(value: Lang) {
  lang.value = value;
  document.documentElement.lang = value;
  try {
    localStorage.setItem(LANG_KEY, value);
  } catch {
    // ignore blocked storage
  }
}

const km: Record<string, string> = {
  // Menu and header
  "Dashboard": "ផ្ទាំងគ្រប់គ្រង",
  "Home": "ទំព័រដើម",
  "Zones & devices": "តំបន់ និងឧបករណ៍",
  "Zones": "តំបន់",
  "Automation rules": "ច្បាប់ស្វ័យប្រវត្តិ",
  "Schedules": "កាលវិភាគ",
  "Alerts": "ការជូនដំណឹង",
  "History": "ប្រវត្តិ",
  "Users": "អ្នកប្រើប្រាស់",
  "More": "ផ្សេងទៀត",
  "Menu": "ម៉ឺនុយ",
  "Rule": "ច្បាប់",
  "Log out": "ចាកចេញ",
  "Change password": "ប្តូរពាក្យសម្ងាត់",
  "Dark mode": "ពន្លឺងងឹត",
  "Light mode": "ពន្លឺភ្លឺ",
  "Language": "ភាសា",
  "Alert sound": "សំឡេងជូនដំណឹង",
  "Sound and pop-up for CRITICAL alerts": "សំឡេង និងផ្ទាំងលោតសម្រាប់ការជូនដំណឹងធ្ងន់ធ្ងរ",
  "On": "បើក",
  "Off": "បិទ",
  "Farm Owner": "ម្ចាស់កសិដ្ឋាន",
  "Farm Worker": "កម្មករកសិដ្ឋាន",
  "Technician": "អ្នកបច្ចេកទេស",
  // Login
  "Sign in": "ចូល",
  "Signing in…": "កំពុងចូល…",
  "Username": "ឈ្មោះអ្នកប្រើ",
  "Password": "ពាក្យសម្ងាត់",
  "Sign in to monitor your zones": "ចូលដើម្បីតាមដានតំបន់របស់អ្នក",
  "Your session ended (for example, the server restarted). Please sign in again.":
    "វគ្គរបស់អ្នកបានបញ្ចប់ (ឧ. ម៉ាស៊ីនមេចាប់ផ្តើមឡើងវិញ)។ សូមចូលម្តងទៀត។",
  // Dashboard
  "live, refreshes every 4 seconds": "ផ្ទាល់ ធ្វើបច្ចុប្បន្នភាពរៀងរាល់ ៤ វិនាទី",
  "Sensors online": "ឧបករណ៍ចាប់សញ្ញាដំណើរការ",
  "Actuators running": "ម៉ាស៊ីនកំពុងដំណើរការ",
  "Open alerts": "ការជូនដំណឹងមិនទាន់ដោះស្រាយ",
  "Newest first": "ថ្មីបំផុតមុន",
  "All clear.": "គ្មានបញ្ហា។",
  "View all alerts": "មើលការជូនដំណឹងទាំងអស់",
  "No sensors yet.": "មិនទាន់មានឧបករណ៍ចាប់សញ្ញា។",
  "No data": "គ្មានទិន្នន័យ",
  "Simulated": "ក្លែងធ្វើ",
  "Device": "ឧបករណ៍",
  "suspect": "គួរសង្ស័យ",
  "Online": "ដំណើរការ",
  "Offline": "ដាច់",
  "Tap for history": "ចុចដើម្បីមើលប្រវត្តិ",
  "Start": "ចាប់ផ្តើម",
  "Stop": "បញ្ឈប់",
  "min": "នាទី",
  "max {n} min": "អតិបរមា {n} នាទី",
  "{t} left": "នៅសល់ {t}",
  "Enter whole minutes from 1 to {n}.": "សូមបញ្ចូលចំនួននាទីពី ១ ដល់ {n}។",
  "Retrying every 4 seconds...": "កំពុងព្យាយាមម្តងទៀតរៀងរាល់ ៤ វិនាទី...",
  // Alerts
  "Threshold breaches, device faults and offline sensors. Staff acknowledge an alert after checking the zone on site.":
    "តម្លៃលើស/ក្រោមកម្រិត ឧបករណ៍ខូច និងឧបករណ៍ចាប់សញ្ញាដាច់។ បុគ្គលិកទទួលស្គាល់ការជូនដំណឹង បន្ទាប់ពីពិនិត្យនៅកន្លែងរួច។",
  "Open": "មិនទាន់ដោះស្រាយ",
  "All": "ទាំងអស់",
  "Acknowledge": "ទទួលស្គាល់",
  "Acknowledge all ({n})": "ទទួលស្គាល់ទាំងអស់ ({n})",
  "Acknowledged by {name}": "បានទទួលស្គាល់ដោយ {name}",
  "No alerts.": "គ្មានការជូនដំណឹង។",
  "Severity": "កម្រិត",
  "Message": "សារ",
  "Raised": "ពេលកើតឡើង",
  "Status": "ស្ថានភាព",
  "happened {n} times": "កើតឡើង {n} ដង",
  "New critical alert": "ការជូនដំណឹងធ្ងន់ធ្ងរថ្មី",
  "Dismiss": "បិទ",
  // History
  "History & reports": "ប្រវត្តិ និងរបាយការណ៍",
  "Reading history and device activity per zone for a selected date range.":
    "ប្រវត្តិតម្លៃ និងសកម្មភាពឧបករណ៍ក្នុងតំបន់នីមួយៗ តាមចន្លោះពេលដែលបានជ្រើស។",
  "Zone": "តំបន់",
  "Sensor": "ឧបករណ៍ចាប់សញ្ញា",
  "From": "ពី",
  "To": "ដល់",
  "Show": "បង្ហាញ",
  "Last hour": "១ ម៉ោងចុងក្រោយ",
  "24 hours": "២៤ ម៉ោង",
  "7 days": "៧ ថ្ងៃ",
  "30 days": "៣០ ថ្ងៃ",
  "Download CSV": "ទាញយក CSV",
  "{n} readings": "{n} តម្លៃ",
  "Not enough readings in this range.": "មិនមានតម្លៃគ្រប់គ្រាន់ក្នុងចន្លោះពេលនេះ។",
  "Device activity": "សកម្មភាពឧបករណ៍",
  "{n} actions in this zone": "{n} សកម្មភាពក្នុងតំបន់នេះ",
  "When": "ពេល",
  "Action": "សកម្មភាព",
  "By": "ដោយ",
  "No activity.": "គ្មានសកម្មភាព។",
  // Statuses
  "IDLE": "ទំនេរ",
  "RUNNING": "កំពុងដំណើរការ",
  "FAULT": "ខូច",
  "OFFLINE": "ដាច់",
  "MAINTENANCE": "កំពុងជួសជុល",
  "DECOMMISSIONED": "ឈប់ប្រើ",
  "OPEN": "មិនទាន់ដោះស្រាយ",
  "ACKNOWLEDGED": "បានទទួលស្គាល់",
  "CRITICAL": "ធ្ងន់ធ្ងរ",
  "WARNING": "ព្រមាន",
  "INFO": "ព័ត៌មាន",
  "ACTIVE": "សកម្ម",
  "PAUSED": "ផ្អាក",
  "CROP": "ដំណាំ",
  "POULTRY": "បសុបក្សី",
  // Time
  "never": "មិនដែល",
  "{n}s ago": "{n} វិនាទីមុន",
  "{n} min ago": "{n} នាទីមុន",
  "{n} h ago": "{n} ម៉ោងមុន",
};

/** Translates a phrase; {name} placeholders are filled from vars. Unknown phrases stay in English. */
export function t(phrase: string, vars: Record<string, string | number> = {}): string {
  const text = lang.value === "km" ? km[phrase] ?? phrase : phrase;
  return text.replace(/\{(\w+)\}/g, (_, k: string) => String(vars[k] ?? `{${k}}`));
}
