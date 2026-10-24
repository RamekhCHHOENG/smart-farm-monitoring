# Smart Farm OOAD: speaker notes

One section per slide, in order. Open `index.html` to present (arrow keys), or use `Smart-Farm-OOAD.pdf`.

## 1. From OOAD design to a working farm

In this presentation we walk through our eleven object oriented analysis and design deliverables. For each one we show the concept, the diagram, the real Java code that implements it, and the running application. The goal is to prove two things: that we understand the object oriented concepts, and that we used them to build a real, working farm system.

## 2. Each deliverable is shown four ways

Every chapter follows the same pattern: the concept, the diagram from our design, the real Java code that implements it with file and line numbers, and the running application. At the end we added the features built this week: new device kinds, sensor repair, thermostat rules and the Devices page.

## 3. Farms are checked by hand, so problems are found too late

Small farms are still checked by hand, so problems like dry soil, heat stress or an empty feeder are found too late. Our solution: sensors watch each zone, rules react automatically, and staff are alerted within seconds.

## 4. The live dashboard

Here is the result: the live dashboard of a crop bed, a chicken house and a greenhouse, refreshing every four seconds. Each tile shows the latest value, a bar with the rule's threshold marks, and how fresh the value is.

## 5. Three people and two external systems

We identified five actors: three people, the farm owner, worker and technician, and two external systems, the sensor device and the notification service. The technician now also repairs sensors, not only actuators.

## 6. Permissions are checked on the server

The permissions table becomes this method. Every service calls require with the allowed roles, so the server enforces the rules even if someone bypasses the user interface. In the app, a farm worker sees the rules read only: there is no button to create, edit or delete a rule.

## 7. Each requirement is a screen and a service

Each functional requirement maps to a screen and to the service that implements it. Zones and devices cover requirements one and two, the automation rules page five and six, schedules seven, history ten.

## 8. Use case diagram

The use case diagram shows eleven use cases. Submit sensor reading always includes evaluate rules, and send alert extends it only when a threshold is breached.

## 9. Use case 3: Submit Sensor Reading

In the code, the include is this loop that always checks every active rule, and the extend is the response that only happens when a rule is triggered. Lines 75 to 77 are new: a thermostat rule that reached its target stops its own run.

## 10. A farm contains zones; a zone owns its devices

The domain model has ten classes. A farm contains zones, a zone has sensors and actuators, and a sensor produces readings. The composition from the domain model is real in the code: a zone owns its sensors and actuators, so deleting a zone removes them too.

## 11. Design class diagram

The design class diagram adds attributes, methods, and five kinds of relationship. It now shows the six sensor subclasses and seven actuator subclasses, the sensor status, and the new thermostat members: stopAt, holdUntil, isTargetReached and opposes. Next, the four pillars of object oriented programming in the real code.

## 12. The actuator protects its own state

Encapsulation: the actuator's status is private. It only changes through methods like activate, which first check that the actuator is idle and the duration is within its safety limit.

## 13. Sensor says what every sensor must do, not how

Abstraction: Sensor is an abstract class. It declares what every sensor must provide, like its unit and valid range, without saying how. This week simulateNext changed: it now receives every kind of actuator running in the zone, so a temperature sensor can react to both a fan and a heater.

## 14. A humidity sensor IS-A sensor

Inheritance: the humidity sensor extends Sensor. It inherits recording, validation, offline detection and the new repair flow, and only adds what is special about humidity: percent, zero to one hundred, and how it reacts to the mister and the fan. It was added this week as one small class, like the light level and water tank sensors.

## 15. One call, different behaviour per zone

Polymorphism: the service calls create default rules on any zone. A crop zone answers with dry soil, dry air and low tank rules; a poultry zone with hot house, cold house and feed rules. The last number is the new stop value: the fan cools to 29 degrees, the heater warms to 21, so they never take turns. Enum names are shortened on the slide to fit.

## 16. 6 sensors and 7 actuators, each one small class

This week we added three sensor kinds and four actuator kinds. Each is one small subclass, so the services, the screens and the Farm Lab work with them without special cases. Hibernate had put a check constraint on the type column of older databases; a small startup upgrade removes it so existing farms accept the new kinds without losing data.

## 17. Sequence: Submit Sensor Reading

The sequence diagram follows one reading: from the device, through the controller and the monitoring service, to the sensor, the rules, the actuator and the alert.

## 18. Each message is a method call

Every arrow in the diagram is a method call here. And the alternative fragment is this branch: when the actuator refuses, the alert becomes critical. Two things are new: before starting, the interlock stops any opposing actuator in the zone, and a keep-in-range rule tells staff where it will stop. Long lines are shortened on the slide; the line numbers are the real ones.

## 19. The [valid?] decision protects the farm

The activity diagram shows the whole workflow. First a decision: is the reading valid? Then a fork: the actuator acts while the alert is sent. The decision node is this if statement. An invalid value marks the sensor as suspect and is rejected, so a broken sensor can never start a pump. Two new guards come before and after it: a sensor reported faulty is ignored, and a sensor under repair is stored but starts nothing.

## 20. State machine: the actuator

The state machine gives the actuator six states. Every transition in the diagram is a method in the code.

## 21. Guarded transitions

Each transition first checks the current state. A pump in fault can only go to maintenance, and only a passed test brings it back to idle. In the app, as the technician, the buttons change with the state, so only valid transitions are offered. The enum prefix ActuatorStatus is shortened on the slide.

## 22. A broken sensor can be repaired

New this week: sensors got their own state machine, the counterpart of the actuator's. Online and offline follow the signal, so a sensor whose cable was fixed comes back by itself. A technician can report a sensor faulty: its readings are then ignored and its rules paused. In maintenance readings are stored again for testing, and completing the repair only works if a valid reading arrived after the repair began.

## 23. The repair test is the sensor’s own data

The sensor's status is worked out from two timestamps a technician sets, so it is never stored twice. The transitions use the same guard pattern as the actuator. And the repair test is real: completeRepair only passes if the sensor itself sent a valid reading after the repair began. On the slide the null check on lastSeenAt and the SensorStatus prefix are shortened.

## 24. Only valid actions are offered

Live, as the technician. The greenhouse humidity sensor was reported faulty: its badge says FAULT, the note explains that its readings are ignored and its rules paused, and the only action offered is start repair. During the repair the page refreshes every four seconds, so the technician sees when a valid reading has arrived and the repair can be completed.

## 25. A fixed 5-minute run is not enough

The problem the user raised: if the fan rule above 34 degrees and the heater rule below 20 each just run for five minutes, the run is the wrong length and the two can conflict. Best practice for holding a temperature is hysteresis, like a thermostat: start at one value, stop at another inside the comfortable band. The chicken house default is fan from 32 down to 29, heater from 18 up to 21, leaving a quiet band of 21 to 29 degrees. On top of that an interlock guarantees fan and heater never run together, and the server refuses rules whose bands overlap.

## 26. The rule knows when its job is done

The rule itself knows when its job is done. holdUntil refuses a stop value on the wrong side of the threshold, so a fan can never be told to stop above where it starts. isTargetReached says whether the reading is back in range; the monitoring service then stops the run, but only if this rule started it: a run a worker started by hand is never cut short.

## 27. Fan and heater never run together

The interlock follows Information Expert too: the heater knows the fan is its opposite, and the fan says the same about the heater. Before any actuator starts, whether a rule, a schedule or a worker starts it, the actuator service stops every running opposite in the same zone and writes the reason in the device log. So fan and heater can never run at the same time.

## 28. Rules that would fight are refused

The last guard is in the rule service. For two rules on the same sensor that push in opposite directions, the heater must stop before the fan starts, and the fan must stop before the heater starts. If not, they would take turns forever, so the server refuses the rule with a message that names both rules and says what to change. On the right, the owner tried a heater that warms up to 34 degrees while the fan starts at 32. The code is condensed on the slide; line numbers are real.

## 29. Keep in range, in the form

In the app, the owner edits the hot house rule: keep in range is ticked, stop at 29 degrees, and the minutes are now labelled longest run. The rules page can also be filtered by zone and searched, which was asked for this week.

## 30. Information Expert

Information expert: the class that has the information does the work. The rule owns its condition, threshold and cooldown, so the rule decides if a reading triggers it. The same idea is behind isValid on the sensor, isTargetReached on the rule, and opposes on the heater and the fan.

## 31. Creator

Creator: a sensor produces readings, so the sensor creates the sensor reading object, after validating the value. Recording a reading is also what brings an offline sensor back online by itself.

## 32. Controller, Low Coupling, High Cohesion

Controller: the reading controller receives the device's request, checks the device key, and hands the work to the monitoring service. It now also answers 202 when the sensor was reported faulty. Each class stays small and focused.

## 33. Single Responsibility: one reason to change

Single responsibility: every class has one reason to change. The rule decides, the actuator acts, the sensor guards its repair state, the alert service alerts, and repositories store. The new pieces follow the same idea: the sensor service only runs the technician's repair steps, and the schema upgrade only lets an older database accept the new device kinds.

## 34. Open/Closed: we just did it

Open closed: to add a heater we added a subclass of actuator, and to add a humidity sensor a subclass of sensor. The services that use actuator and sensor did not change. To be honest about the limit: the small create factory needs one new line per kind, and older databases needed a startup upgrade because of a check constraint Hibernate had created.

## 35. Dependency Inversion

Dependency inversion: the alert service depends on the notification service interface. Whether alerts go to the console or to Telegram is decided by configuration, not by changing code. Set a Telegram token, and the same alerts go to staff phones instead.

## 36. Every device on one page

New this week: a full Devices page. It lists every sensor and actuator with live status and the latest reading. The summary cards at the top double as filters, and the list can be filtered by zone, kind, status or name. The repair actions are on each row.

## 37. A full page to register a device

Registering a device is now a full page: pick the zone, sensor or actuator, then a type card that explains what each kind is for. Choosing the heater fills in its 30 minute safety limit. After saving, the page shows the device number, the line to paste into the ESP32's secrets file for a real sensor, and a link to set up a rule for it.

## 38. The thermostat, tested live

In the Farm Lab, virtual sensors and actuators talk to the server exactly like real ESP32 boards. The new Thermostat scenario sets the chicken house to 35 degrees on a normal day. The fan starts, and as soon as the house is back to 30 degrees the rule stops it, about ten seconds later and far before its two minute safety limit. The heater never starts. The scenario passes.

## 39. Every rule of the design has a test

Finally, every rule of the design has a test. Fifty eight automated tests, a thirty four step browser walkthrough, and seventeen farm scenarios check that the code really follows the diagrams. One scenario is a known gap on purpose: a clogged pipe cannot be detected without a flow sensor.

## 40. Design first, then code that follows it

We designed first, and then wrote code that follows the design: every diagram became classes and methods, and every rule became a test. The features added this week followed the same path. Thank you.
