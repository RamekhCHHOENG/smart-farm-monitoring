# User acceptance testing in the cloud

Run the app, seed three customer farms, and give testers a public link through a Cloudflare quick tunnel.
Nothing here contains a real password: every secret is generated on the spot and kept out of git.

## 1. Start the app

```bash
export FARM_DEMO_PASSWORD=$(openssl rand -hex 16) FARM_ADMIN_PASSWORD=$(openssl rand -hex 16) FARM_DEVICE_KEY=$(openssl rand -hex 16)
(cd backend && mvn -q -DskipTests package && java -jar target/smart-farm-backend-1.0.0.jar) &
(cd frontend && npm install && npm run dev) &
```

Keep `FARM_DEMO_PASSWORD` and `FARM_ADMIN_PASSWORD` in a private place: they are the logins for
`owner` / `worker` / `technician` and `admin`.

## 2. Seed three farms

```bash
ADMIN_PASSWORD=$FARM_ADMIN_PASSWORD python3 scripts/seed-uat.py
```

This creates Siem Reap Organic Garden, Battambang Poultry Farm and Kampot Mixed Farm (three zones each, simulated
sensors, actuators and default rules). The owner logins (`owner-siemreap`, `owner-battambang`, `owner-kampot`)
and their random passwords are written to `uat-credentials.txt` (git-ignored). Run it again to reset an owner's password.

## 3. Open the tunnel

```bash
cloudflared tunnel --url http://localhost:3000
```

It prints an address like `https://random-words.trycloudflare.com`. One tunnel is enough: the frontend forwards
`/api` to the backend, which stays private. Stop the tunnel (Ctrl+C) when testing ends, because anyone with the
address can reach the login page.

The cloud environment needs **Network access: Full** for `cloudflared`, which uses its own DNS lookups and an
outbound connection to Cloudflare on port 7844. Set it back to Limited afterwards.
