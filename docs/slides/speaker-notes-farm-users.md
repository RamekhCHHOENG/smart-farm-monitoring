# Smart Farm guide for farm users: speaker notes

Plain-language deck with no code. One section per slide. Open `farm-users.html` to present (arrow keys), or use `Smart-Farm-Guide.pdf`.
The technical OOAD deck is `index.html` (notes in `speaker-notes.md`).

## 1. Your farm watches itself

This is the Smart Farm system, explained for the people who will use it every day. In short: sensors watch the farm day and night, the machines start by themselves when needed, and you get a message when something needs a person.

## 2. Checking a farm by hand finds problems too late

Today most small farms are checked by walking around. Problems like dry soil, heat in the chicken house or an empty feeder are found too late. Smart Farm measures all the time, reacts in seconds, and keeps a record, so there are fewer losses and less work.

## 3. Measure, decide, act, tell you

It works in four steps. Sensors measure every few seconds. Your rules say what is normal. When a value goes outside that range, the right machine starts, and stops again when things are back to normal. And you get an alert in the app, and on Telegram if you want.

## 4. Everyone sees what they need, and only that

Four kinds of people use the app. The owner can do everything. A worker watches the farm, starts and stops machines and marks alerts as seen. A technician installs and repairs devices. Company staff set up new customer farms and help them, but only when the owner allows it. Everyone has their own password and only sees the buttons they may use.

## 5. The whole farm on one screen

This is the first screen after you sign in. It tells you in one sentence how the farm is doing, shows the average temperature, and lists the zones that need attention first. The values refresh by themselves.

## 6. Each zone has its own page

Click a zone to open its page. At the top you see the values now, how much of the day it stayed in the good range, its machines and its alerts. Below are the charts of the last day, how long each machine ran, and a list of everything that happened. With many zones, type a name in Find a zone.

## 7. Alerts tell you what happened and what was done

When a value goes out of range, you get an alert that says where, what, and what the system already did, for example: the soil in Vegetable Bed A is at 24 percent and the pump started. Critical alerts also make a sound. Mark it as seen when you have checked; the alert shows when the value is back to normal.

## 8. It keeps the chicken house comfortable, like a thermostat

Rules work like a thermostat at home. When the chicken house gets hotter than 32 degrees, the fan starts and keeps running until it is back to 29. When it gets colder than 18, the heater runs until 21. Between 21 and 29 nothing runs. The fan and the heater never run together, and every machine has a longest run time as a safety limit.

## 9. Setting a rule is one short form

The owner sets a rule in one short form: which sensor, when to start, which machine, and the value where it should stop. The form repeats the rule in plain words, so you can check it before saving.

## 10. Rules that would fight are refused

The app also protects you from mistakes. Here someone asks the heater to run up to 34 degrees, but the fan already starts at 32, so the two would switch on and off against each other. The app refuses and says exactly what to change.

## 11. Schedules for the jobs you do every day

For jobs that happen at fixed times, like feeding the chickens at seven in the morning or watering the beds at five in the evening, use a schedule. The machine runs for the minutes you choose, at the farm's local time.

## 12. Every device on one page

The Devices page lists every sensor and machine with its status and latest value. One click on Need attention shows only the devices that are broken or have stopped sending.

## 13. A broken sensor cannot fool the farm

If a sensor breaks, it could send wrong values and start a pump for nothing. So the technician reports it as faulty: its values are ignored and its rules wait. After the repair, the sensor only counts as working again when a good reading has arrived. A sensor that simply lost its cable comes back by itself.

## 14. Adding a device takes one page

To add a new sensor or machine, the technician picks the zone and the kind of device, with a short explanation for each. After saving, the app shows how to connect the board and suggests setting up a rule for it.

## 15. Choose hardware from the catalog

The catalog lists about forty common sensors and machines, with a picture, what each one measures, its voltage and its price, so choosing hardware is easy. A farm can add its own models as well.

## 16. History and reports

The History page shows a chart for any sensor over the last day, week or the dates you choose, together with what each machine did. You can download the readings as a spreadsheet.

## 17. On your phone, in Khmer or English

Nobody has to sit at a computer: the same app works on a phone, with a tab bar at the bottom. It works in Khmer and in English, and remembers the language you picked.

## 18. Farm Lab: practise without any hardware

Before buying hardware, you can try everything in the Farm Lab. Virtual sensors and machines behave like a small farm. Play a dry spell, a heat wave or a power cut, and watch the system react. Each test says PASS or FAIL. Lab alerts never go to staff phones.

## 19. Many customer farms, one system

One installation can serve many customer farms. The company creates a farm and sends the owner a link to set their own password. Each farm only ever sees its own data, and company support can only help a farm when its owner allows it.

## 20. Safe, private and tested

Your data is safe. Everyone has their own password, and after five wrong tries the account waits fifteen minutes. Each farm only sees its own data, and passwords and device keys are never stored in plain text. Before every release, seventy three automatic checks, a walk through every screen, and seventeen farm situations are tested.

## 21. Less walking, faster reaction, fewer losses

To sum up: the sensors watch the farm, the rules react in seconds, you are told what happened, and everything is recorded. That means less walking, a faster reaction, and fewer losses. Thank you.
