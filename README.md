# CamAPSDisplay

This app is designed to show blood glucose level (BGL) readings from CamAPS FX (camdiab.com) as a unsleeping display, so at a glance at your smartphone screen you can see what the current glucose level is.

### Why
While looking after our Type 1 Diabetes (T1D) son I don't like interrupting our time by pulling out his phone and checking his BGL.
Instead I like to put his phone down somewhere close with the BGL display locked.
When using vanilla Dexcom on iOS I did this with guided access which would:
1. Lock the phone, so if I left it no one could get into the phone.
2. Stop the screen from falling asleep which would make it easier to parse.

### HowTo
With CamAPS and android this is not possible without annoyance, so I made this little app.
Just:
1. enable pinning apps in settings
2. install it (by clicking on the build in TODO) (you should already have Chrome enabled to install apps from installing CamAPS)
3. open the app
4. enable the app to listen to notifications
5. click the stop screen sleeping button (TODO)
6. pin the app

The phone will not shut off, so make sure it is plugged in otherwise you will run out of battery.

### Notes
Also this is my first android app, so no guarantees.

### TODO (in kinda a priority order)

#### Fixes/required features for 0.1.0
Better erroring when things go wrong (wrong password, no server, no internet...)
Change of units actually doing something (including changing decimal points in UI)
Show logs/report logs in the app (maybe firebase?)

#### Other features
highlight peaking and onsetting insulin
add upcoming insulin (like next 20mins of insulin)
Clickable Graph for BGL/basal changes
Last time data was recieved from diasend (checking that it is actually working or the other end is broken)

#### Replacement of xdrip as side car
send to dexcom share BGLs
Dexcom follow UI to manage sharers
local alerts and notifications
send to nightscout 

#### Really in the furture
Autotune ISF/CSF... and autotune the peaks and onsets of insulin
Suggestions based on learning


