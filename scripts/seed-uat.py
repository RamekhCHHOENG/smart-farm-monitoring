"""Seeds three UAT customer farms (Siem Reap, Battambang, Kampot) with zones, simulated devices and default rules.

Needs the backend running with FARM_ADMIN_PASSWORD set. Safe to run again: existing farms get a new owner invite.

    ADMIN_PASSWORD=<the FARM_ADMIN_PASSWORD> python3 scripts/seed-uat.py [API_BASE] [credentials file]

Each owner gets a fresh random password, written only to the credentials file (git-ignored, default
uat-credentials.txt). Nothing is printed. Share the file only with your testers.
"""
import json, os, secrets, sys, urllib.request, urllib.error
B = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080") + "/api"
OUT = sys.argv[2] if len(sys.argv) > 2 else "uat-credentials.txt"
def call(method, path, body=None, tok=None):
    r = urllib.request.Request(B + path, method=method, data=json.dumps(body).encode() if body is not None else None,
                               headers={"Content-Type": "application/json", **({"Authorization": f"Bearer {tok}"} if tok else {})})
    try:
        with urllib.request.urlopen(r) as f:
            t = f.read(); return json.loads(t) if t else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> {e.code} {e.read().decode()[:300]}")
admin = call("POST", "/auth/login", {"username": "admin", "password": os.environ["ADMIN_PASSWORD"]})["token"]

FARMS = [
 ("Siem Reap Organic Garden", "Siem Reap, Cambodia", "owner-siemreap", [
   ("CROP", "Lettuce Bed 1", "Lettuce", None, ["SOIL_MOISTURE", "TEMPERATURE"], ["WATER_PUMP"]),
   ("CROP", "Cucumber Trellis", "Cucumber", None, ["SOIL_MOISTURE", "HUMIDITY"], ["WATER_PUMP", "MISTER"]),
   ("CROP", "Tomato Greenhouse", "Tomato", None, ["SOIL_MOISTURE", "TEMPERATURE", "LIGHT_LEVEL"], ["WATER_PUMP", "FAN"])]),
 ("Battambang Poultry Farm", "Battambang, Cambodia", "owner-battambang", [
   ("POULTRY", "Broiler House 1", None, 2000, ["TEMPERATURE", "HUMIDITY", "FEED_LEVEL"], ["FAN", "FEEDER"]),
   ("POULTRY", "Broiler House 2", None, 1500, ["TEMPERATURE", "FEED_LEVEL"], ["FAN", "FEEDER", "HEATER"]),
   ("POULTRY", "Layer House", None, 800, ["TEMPERATURE", "WATER_LEVEL"], ["FAN", "LIGHT"])]),
 ("Kampot Mixed Farm", "Kampot, Cambodia", "owner-kampot", [
   ("CROP", "Pepper Plot", "Kampot pepper", None, ["SOIL_MOISTURE", "TEMPERATURE"], ["WATER_PUMP"]),
   ("CROP", "Mango Orchard", "Mango", None, ["SOIL_MOISTURE"], ["WATER_VALVE"]),
   ("POULTRY", "Village Chicken Coop", None, 300, ["TEMPERATURE", "FEED_LEVEL"], ["FAN", "FEEDER"])]),
]
NAMES = {"SOIL_MOISTURE": "Soil moisture", "TEMPERATURE": "Air temperature", "HUMIDITY": "Humidity", "LIGHT_LEVEL": "Light",
         "FEED_LEVEL": "Feed level", "WATER_LEVEL": "Water level", "WATER_PUMP": "Water pump", "FAN": "Fan", "FEEDER": "Feeder",
         "HEATER": "Heater", "MISTER": "Mister", "LIGHT": "Grow light", "WATER_VALVE": "Water valve"}
creds = []
for name, loc, user, zones in FARMS:
    have = [f for f in call("GET", "/company/farms", None, admin) if f["name"] == name]
    if have:
        inv = call("POST", f"/company/farms/{have[0]['id']}/invite", None, admin)
    else:
        inv = call("POST", "/company/farms", {"name": name, "location": loc, "ownerName": "UAT Owner", "ownerUsername": user,
                                              "ownerContact": None}, admin)
    pw = secrets.token_hex(16)
    tok = call("POST", f"/auth/invites/{inv['inviteToken']}", {"password": pw})["token"]
    existing = {z["name"] for z in call("GET", "/farm", None, tok)["zones"]}
    for ztype, zname, crop, birds, sensors, acts in zones:
        if zname in existing:
            continue
        z = call("POST", "/zones", {"type": ztype, "name": zname, "cropName": crop, "birdCount": birds, "lab": False}, tok)
        for s in sensors: call("POST", f"/zones/{z['id']}/sensors", {"type": s, "name": f"{NAMES[s]} - {zname}", "simulated": True}, tok)
        for a in acts: call("POST", f"/zones/{z['id']}/actuators", {"type": a, "name": f"{NAMES[a]} - {zname}", "maxRunMinutes": 10}, tok)
        call("POST", f"/zones/{z['id']}/default-rules", None, tok)
    creds.append(f"{name}\n  login: {user}\n  password: {pw}\n")
    print("seeded", name, "owner", user, "zones", len(zones))
fd = os.open(OUT, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, "w") as f:
    f.write("".join(creds))
