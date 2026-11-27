# Health

Two endpoints that need no credentials. Both return the same body:

```json
{"initialized": true, "sealed": false, "version": "0.2.0"}
```

| Endpoint | Status | Use it for |
| :--- | :--- | :--- |
| `GET /api/v1/health` | Always `200` while the process runs | Liveness probes, Docker `HEALTHCHECK` |
| `GET /api/v1/health/ready` | `200` when unsealed, `503` while sealed | Monitoring and alerts on a sealed vault |

## Why there are two

A sealed vault is **healthy**. It is waiting for a person. Restarting it does not help, and it brings back the sealed state anyway. So:

- **Liveness** must use `/health`, or a sealed vault would be restarted in a loop.
- **Readiness** should normally use `/health` too. If readiness used `/health/ready`, Kubernetes would take a sealed pod out of the Service, and you could no longer reach the **Unseal** page through it.
- Use `/health/ready` (or the `sealed` field) in **monitoring**, so you are alerted when the vault needs unsealing.

The Docker image's built-in `HEALTHCHECK` calls `/api/v1/health` every 30 seconds.

## Examples

```yaml
# Kubernetes probes
livenessProbe:
  httpGet: { path: /api/v1/health, port: 8080 }
  initialDelaySeconds: 60
readinessProbe:
  httpGet: { path: /api/v1/health, port: 8080 }
```

```bash
# Uptime Kuma, cron or any shell check: fail when sealed
curl -fsS https://bastionvault.homelab118.home/api/v1/health/ready > /dev/null || echo "vault sealed"
```
