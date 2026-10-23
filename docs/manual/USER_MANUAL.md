# Smart Farm Monitoring System - User Manual

For Farm Owners, Farm Workers and Technicians. Screens show the demo farm.

## 1. About this manual

This manual explains how to use the Smart Farm Monitoring System day to day. It is written for the three kinds of people who use the app. You only need the chapters for your role.

| Role | What you do | Chapters |
|---|---|---|
| Farm Owner | Sets up the farm and zones, decides the automation rules and schedules, manages staff accounts, reads reports. Can also do everything the others can. | All |
| Farm Worker | Watches the dashboard, starts and stops pumps, fans and feeders, and acknowledges alerts after checking the zone. | 2, 3, 7, 8, 9 |
| Technician | Registers sensors and actuators, reports faults and repairs devices. | 2, 4, 7, 9 |

Words such as **zone**, **sensor**, **actuator** and **threshold** are explained in the Glossary at the end.

## 2. Getting started

### 2.1 Sign in

1. Open the app in a web browser (on the farm computer this is usually http://localhost:3000).
2. Type the username the Farm Owner gave you (on the demo farm: **owner**, **worker** or **technician**). Capital letters and spaces before or after the name do not matter.
3. Type your password and press **Enter** or click **Sign in**.

![The sign-in page](screens/01-sign-in.png)
*Figure 1. The sign-in page*

If the username or password is wrong, the page says so. For safety it does not tell you which of the two was wrong. After **5 wrong passwords** the username is locked for 15 minutes, which stops strangers from guessing. If you forgot your password, ask the Farm Owner to reset it; that also unlocks the account at once.

![Wrong username or password](screens/02-sign-in-wrong-password.png)
*Figure 2. Wrong username or password*

If you were signed in and the server restarted, or you were away for more than 12 hours, you are asked to sign in again. Nothing you saved is lost.

![Your session ended: sign in again](screens/03-session-ended.png)
*Figure 3. Your session ended: sign in again*

### 2.2 Change your password

Do this the first time you sign in with the password the server generated.

1. Click the **key icon** at the top right.
2. Type your current password, then the new password twice (at least 8 characters).
3. Click **Change password**. Use the new password from the next sign-in.

![Change password](screens/15-change-password-dialog.png)
*Figure 4. Change password*

### 2.3 Moving around

- The menu on the left opens Dashboard, Zones & devices, Automation rules, Schedules, Alerts, History and (for the owner) Users. On a phone the menu is at the bottom of the screen (chapter 9).
- The red number next to **Alerts** is how many alerts are still open.
- The **language button** (next to the bell) switches between Khmer and English (section 2.4).
- The **bell icon** turns the alert sound on or off (section 3.3).
- The **moon icon** switches to dark mode, which is easier on the eyes at night. The app remembers your choice.
- **Log out** signs you out on this device.

![Dark mode](screens/16-dashboard-dark-mode.png)
*Figure 5. Dark mode*

### 2.4 Khmer or English

Click the **language button** at the top right (it shows the word Khmer in Khmer script; it is also on the sign-in page) to show the menus, dashboard, alerts and history in Khmer. Click it again (it then shows **EN**) to go back to English. Each phone or computer remembers its own choice.

![The dashboard in Khmer](screens/27-dashboard-khmer.png)
*Figure 6. The dashboard in Khmer*

> **Note:** Names you type (farm, zones, devices) and alert messages from the server stay as they were written. The setup forms for owners and technicians are in English.

## 3. Dashboard

The dashboard is the home page. It refreshes by itself every 4 seconds, so you can leave it open on a screen in the farm office.

![Dashboard of the Farm Owner](screens/04-dashboard-owner.png)
*Figure 7. Dashboard of the Farm Owner*

### 3.1 Reading the dashboard

| Part | What it tells you |
|---|---|
| Top cards | Number of zones, how many sensors are online, how many actuators are running, and how many alerts are open. |
| Zone card | One card per zone with its sensors and actuators. |
| Sensor tile | The latest value and unit. The bar shows where the value sits in the sensor's range; the small black mark is the rule's threshold, written underneath (for example "Rule < 30"). The value and bar turn **red** when a rule's threshold is crossed. Green dot = online, red dot = no reading for 2 minutes; "5s ago" tells how fresh the value is. **Click a tile** to open that sensor's history. |
| Actuator row | Pump, fan or feeder with its status: IDLE (off), RUNNING (shows time left), FAULT, OFFLINE or MAINTENANCE. |
| Open alerts | The newest open alerts. "×3" means the same problem happened 3 times. Click View all alerts for the full list. |

The four cards at the top are also links: click **Open alerts** to go to the alert list.

### 3.2 Start or stop a pump, fan or feeder (Worker, Owner)

1. Find the actuator in its zone card.
2. Type how many minutes it should run in the small box. Each device has a safety limit, for example 10 minutes for a pump.
3. Click **Start**. The status changes to RUNNING with the time left.
4. To stop it early, click **Stop**. If you do nothing, it switches off by itself when the time is up.

![Dashboard of the Farm Worker](screens/17-dashboard-worker.png)
*Figure 8. Dashboard of the Farm Worker*

If the minutes are empty or above the safety limit, a red message appears right under the button and nothing starts.

![The minutes must be within the safety limit](screens/18-worker-wrong-minutes-message.png)
*Figure 9. The minutes must be within the safety limit*

> **Note:** A device in FAULT, OFFLINE or MAINTENANCE cannot be started. Ask the technician.

### 3.3 Critical alerts: banner and sound

When something CRITICAL happens (for example a pump reports a fault, or a rule wanted to start a fan that could not start), a **red banner** appears at the top of every page, also right after you sign in. Click **Alerts** to go to the list, or **×** to hide the banner.

![A critical alert shows on every page](screens/28-critical-banner.png)
*Figure 10. A critical alert shows on every page*

To also hear a **beep** and get a **pop-up** from the browser, click the **bell icon** at the top (on a phone: More → Alert sound) and allow notifications when the browser asks. Leave the app open in a tab; it checks every 8 seconds.

## 4. Zones and devices

A **zone** is one area of the farm, such as a vegetable bed or a chicken house. Each zone has **sensors** (they measure) and **actuators** (they act).

![Zones & devices (Farm Owner)](screens/05-zones-and-devices.png)
*Figure 11. Zones & devices (Farm Owner)*

### 4.1 Farm name and location (Owner)

Click **Farm settings**, change the name or location, and click **Save**. The dashboard title changes at once.

![Farm settings](screens/06-farm-settings-dialog.png)
*Figure 12. Farm settings*

### 4.2 Add a zone (Owner)

1. Click **New zone**.
2. Choose the type: **Crop zone** or **Poultry zone**.
3. Type a name (Khmer is fine), and the crop or the number of birds.
4. Click **Save**.

![New zone](screens/07-new-zone-dialog.png)
*Figure 13. New zone*

**Default rules** creates the usual rules for the zone type in one click: a crop zone waters dry soil, a poultry zone cools a hot house and refills the feeder. **Edit** renames a zone; the **bin icon** deletes it.

> **Note:** Deleting a zone also deletes its devices, readings, rules and schedules. The app asks you to confirm first.

### 4.3 Register a device (Technician)

1. Click **+ Device** on the zone.
2. Choose **Sensor** or **Actuator** and its type.
3. Type a name, for example "Soil moisture B1".
4. For a real ESP32 sensor, **untick Simulated**. For an actuator, set the safety limit (the longest it may run).
5. Click **Register**. The device appears in the table with a **#number**. Write it down: the ESP32 needs it.

![Register a device](screens/08-register-device-dialog.png)
*Figure 14. Register a device*

![Zones & devices (Technician)](screens/21-zones-technician.png)
*Figure 15. Zones & devices (Technician)*

### 4.4 Faults and repairs (Technician)

The buttons next to an actuator change with its status, so only the actions that make sense are shown.

| Status | Buttons | What happens |
|---|---|---|
| IDLE or RUNNING | Report fault, Mark offline | The device stops and a CRITICAL or WARNING alert is sent. |
| FAULT | Start repair | The device goes into MAINTENANCE. |
| OFFLINE | Heartbeat restored, Start repair | Back to IDLE, or into MAINTENANCE. |
| MAINTENANCE | Repair done (test passed), Test failed, Decommission | IDLE again; stays in maintenance; or removed from service for good. |

![A feeder in FAULT: only Start repair is offered](screens/22-device-in-fault.png)
*Figure 16. A feeder in FAULT: only Start repair is offered*

![In MAINTENANCE: finish the repair, record a failed test, or decommission](screens/23-device-in-maintenance.png)
*Figure 17. In MAINTENANCE: finish the repair, record a failed test, or decommission*

### 4.5 Rename a device or change its safety limit (Technician)

Click **Edit** next to the device. You can rename a sensor, and rename an actuator or change how long it may run in one go (1 to 240 minutes). The **#number** stays the same, so an ESP32 that sends readings for it keeps working.

![Edit an actuator](screens/29-edit-device-dialog.png)
*Figure 18. Edit an actuator*

**Remove** deletes a device after you confirm. A decommissioned actuator can only be removed.

## 5. Automation rules (Owner)

A rule says: **IF** a sensor reading goes above or below a value **THEN** start an actuator for some minutes and send an alert. Rules work day and night, also when nobody is looking.

![Automation rules](screens/09-automation-rules.png)
*Figure 19. Automation rules*

### 5.1 Create a rule

1. Click **New rule** and give it a clear name, for example "Dry soil - water bed A".
2. Choose the **sensor**, the **condition** (Below or Above) and the **threshold**. The range the sensor can measure is shown under the box.
3. Choose the **actuator** in the same zone and how many minutes it should run, or choose **None (alert only)** to only be warned.
4. Choose the **severity**: Info, Warning or Critical.
5. Set the **cooldown**: the rule fires at most once in this many minutes, so you are not flooded with alerts.
6. Click **Save rule**.

![New rule](screens/10-new-rule-dialog.png)
*Figure 20. New rule*

Use the switch in the **Active** column to pause a rule without deleting it. The pencil edits a rule; the bin deletes it. Workers and technicians see the rules but cannot change them.

![Rules as seen by a Farm Worker (read only)](screens/19-rules-worker-read-only.png)
*Figure 21. Rules as seen by a Farm Worker (read only)*

## 6. Schedules (Owner)

A schedule starts an actuator at a fixed time on chosen days, for example feeding at 07:00 every day. Times are **farm time** (the time zone is shown at the top of the page).

![Schedules with their next run](screens/11-schedules.png)
*Figure 22. Schedules with their next run*

1. Click **New schedule** and type a name.
2. Choose the actuator, the time and how many minutes it runs.
3. Click the days to switch them on or off (green = on).
4. Click **Save schedule**. The **Next run** column tells you when it will start.

![New schedule](screens/12-new-schedule-dialog.png)
*Figure 23. New schedule*

To change a schedule, click the **pencil** in its row, change the time, days, actuator or minutes, and click **Save schedule**. A task that already ran today does not run twice if you move it later. The **bin** deletes a schedule after you confirm.

![Edit a schedule](screens/30-edit-schedule-dialog.png)
*Figure 24. Edit a schedule*

> **Note:** If the server was off at the scheduled time, the task still runs if the server is back within 15 minutes. Otherwise it waits for the next selected day.

## 7. Alerts

An alert is created when a rule fires, when a device reports a fault or goes offline, or when a sensor stops sending readings for 2 minutes.

| Severity | Meaning |
|---|---|
| INFO | For your information; no action needed. |
| WARNING | Something needs attention soon, for example dry soil (the pump has usually been started already). |
| CRITICAL | Act now: a device is broken, or a rule wanted to start a device that could not start. |

![Alerts as seen by a Farm Worker](screens/20-alerts-worker-acknowledge.png)
*Figure 25. Alerts as seen by a Farm Worker*

1. Read the alert and **go and check the zone**.
2. Click **Acknowledge**. Your name is saved with the alert.
3. After checking the whole farm you can click **Acknowledge all**.

The **Open** tab shows alerts still waiting; **All** also shows acknowledged ones and who acknowledged them. If the same problem happens again before anyone acknowledges it, it is counted on the same alert ("happened 2 times") instead of filling the list with copies.

![All alerts, including acknowledged ones](screens/14-alerts-owner.png)
*Figure 26. All alerts, including acknowledged ones*

## 8. History and reports

The quickest way in: click a sensor tile on the dashboard. History opens with that sensor already chosen.

1. Choose a **zone** and a **sensor**.
2. Click a quick range (**Last hour**, **24 hours**, **7 days**, **30 days**), or set **From** and **To** and click **Show**.
3. Read the chart. The red dashed line is the rule's threshold. Move the mouse over the line to see exact values. Long ranges are shown with at most 500 points so the chart stays fast.
4. The **Device activity** list shows every start and stop, who or what did it (rule, schedule, safety, a person) and why.
5. Click **Download CSV** to save every reading in the range as a file that opens in Excel or Google Sheets, for reports or a teacher.

![History & reports](screens/13-history-and-reports.png)
*Figure 27. History & reports*

> **Note:** Readings are kept for 90 days, then deleted to keep the server fast. Download a CSV if you need them longer. Alerts and device activity are kept.

## 9. Using a phone

The app works in the phone's browser. The menu sits at the **bottom of the screen**, within reach of your thumb: **Home**, **Alerts** (with the number still open), **History**, **Zones** and **More**. Lists turn into cards so that messages and statuses stay readable.

![Dashboard on a phone](screens/24-phone-dashboard.png)
*Figure 28. Dashboard on a phone*

![Alerts on a phone](screens/25-phone-alerts.png)
*Figure 29. Alerts on a phone*

**More** opens the other pages (Automation rules, Schedules, and Users for the owner) and the settings: language, alert sound, dark mode, change password and log out.

![The More menu on a phone, in Khmer](screens/31-phone-more-menu.png)
*Figure 30. The More menu on a phone, in Khmer*

![Zones & devices on a phone](screens/26-phone-zones.png)
*Figure 31. Zones & devices on a phone*

## 10. Staff accounts (Owner)

Open **Users** in the menu. Every person should have their own account, so the history shows who did what.

![Staff accounts](screens/32-users.png)
*Figure 32. Staff accounts*

### 10.1 Add a person

1. Click **Add user**.
2. Type their full name and a **username** (3-30 letters, numbers, dots or dashes, no spaces).
3. Choose the **role**: Farm Worker, Technician, or Farm Owner (full access).
4. Type a first password of at least 8 characters and click **Save**.
5. Tell the person their username and password in person. They should change the password with the key icon.

![Add a user](screens/33-add-user-dialog.png)
*Figure 33. Add a user*

### 10.2 Change a role, reset a password, remove a person

- The **pencil** changes the name or role. The farm always keeps at least one Farm Owner.
- The **key** sets a new password for someone who forgot theirs or was locked out. They are signed out on all their devices.
- The **bin** removes a person who left the farm. Their name stays in old alerts and device activity. You cannot remove yourself.

![Reset a password](screens/34-reset-password-dialog.png)
*Figure 34. Reset a password*

## 11. Troubleshooting

| What you see | What to do |
|---|---|
| "Wrong username or password" | Check the username and password. Ask the owner if you forgot the password. |
| "Too many wrong passwords. Wait N min" | Wait, or ask the Farm Owner to reset your password (Users → key icon), which unlocks it at once. |
| "A Farm Worker account cannot do this" | The page or action is for another role. Ask the Farm Owner. |
| "Cannot reach the server... Retrying" | The backend computer is off or the network is down. The page reconnects by itself when it is back. |
| "Your session ended" | Sign in again. This happens after a server restart or after 12 hours. |
| A sensor dot is red / "is offline" alert | The sensor sent nothing for 2 minutes. Check its power, cable and Wi-Fi (Technician). |
| A value says "suspect" | The sensor sent an impossible value (for example 150 %). Check or recalibrate it. |
| "Enter whole minutes from 1 to N" | Type a whole number of minutes within the device's safety limit. |
| "Cannot start ...: it is FAULT" | The device is broken or in repair. The technician must finish the repair first. |
| "Threshold must be between ..." | Choose a threshold the sensor can actually measure. |
| A schedule did not run | Check that it is Active, the day is selected, and the Next run time. The server must be on at that time. |

## 12. Glossary

| Word | Meaning |
|---|---|
| Zone | One area of the farm: a crop bed or a chicken house. |
| Sensor | A device that measures: soil moisture (%), temperature (°C) or feed level (%). |
| Actuator | A device that acts: water pump, fan or feeder. |
| Simulated | A sensor whose values come from the built-in simulator, for demos and training. |
| Rule | IF a reading crosses a threshold THEN start an actuator and/or send an alert. |
| Threshold | The value a reading is compared with, for example 30 % soil moisture. |
| Cooldown | Minutes a rule waits before it may fire again. |
| Safety limit | The longest an actuator may run in one go. It always switches off by itself. |
| Schedule | An actuator start at a fixed time on chosen days. |
| Acknowledge | Confirm you have seen an alert and checked the zone. |
| CSV | A simple table file that Excel and Google Sheets open. |
