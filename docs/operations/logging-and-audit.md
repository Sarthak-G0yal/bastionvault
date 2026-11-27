# Logging and audit

BastionVault records security events in **two** places:

| Where | Kept | Good for |
| :--- | :--- | :--- |
| `audit_events` table | `VAULT_AUDIT_RETENTION_DAYS` (90 days by default) | The [Audit log](/ui/audit) page and the Secrets page figures |
| Console log, logger `bastionvault.audit` | As long as your log system keeps it | Search, dashboards, alerts in Loki or Grafana |

Secret values are never written to either.

## Log format

With the `prod` profile (on in the Docker image), every log line is one JSON object in **Elastic Common Schema** (ECS). Audit events carry extra `audit.*` fields:

```json
{
  "@timestamp": "2026-11-27T10:15:02.114Z",
  "log.level": "INFO",
  "log.logger": "bastionvault.audit",
  "message": "external-secrets READ_GROUP affine -> SUCCESS",
  "audit.actor_type": "CLIENT",
  "audit.actor": "external-secrets",
  "audit.action": "READ_GROUP",
  "audit.target": "affine",
  "audit.outcome": "SUCCESS",
  "audit.source_ip": "10.42.0.17"
}
```

| Field | Values |
| :--- | :--- |
| `audit.actor_type` | `ADMIN`, `CLIENT`, `SYSTEM`, `ANONYMOUS` |
| `audit.actor` | Username, client ID, or a system name |
| `audit.action` | See [Audit log actions](/ui/audit#actions) |
| `audit.target` | `group/key`, a group, a client ID, or empty |
| `audit.outcome` | `SUCCESS`, `DENIED`, `FAILED`, `NOT_FOUND` |
| `audit.source_ip` | The client IP ([trusted proxies](/deploy/reverse-proxy#trusted-proxies) applied) |

The `message` is `<actor> <action> <target> -> <outcome>` and is meant for people. Build queries on the `audit.*` fields.

## Shipping to Loki

Grafana Alloy or Promtail on Kubernetes already collects pod stdout. Parse the JSON and keep the audit fields as structured metadata or labels. Keep label cardinality low: `audit_action` and `audit_outcome` are fine as labels, while `actor`, `target` and IP should stay as fields.

Example LogQL queries:

```text
# every denied or failed request
{namespace="bastionvault"} | json | log_logger="bastionvault.audit" | audit_outcome=~"DENIED|FAILED"

# who revealed secrets in the UI today
{namespace="bastionvault"} | json | audit_action="REVEAL_SECRET"

# reads per client, per hour
sum by (audit_actor) (count_over_time(
  {namespace="bastionvault"} | json | audit_action=~"READ_.*" [1h]))
```

## Alerts worth setting

| Alert | Query idea |
| :--- | :--- |
| The vault is sealed | `/api/v1/health/ready` returns `503` (Uptime Kuma or Blackbox exporter) |
| Brute force | More than a few `FAILED` outcomes from one `audit.source_ip` in 15 minutes |
| A client hits a group it shouldn't | Any `DENIED`. These usually mean a misconfigured rule, sometimes something worse. |
| Bulk export | Any `EXPORT_ALL*` action |
| Key management | `CHANGE_MASTER_PASSWORD`, `REGENERATE_RECOVERY_KEY`, `ENABLE_AUTO_UNSEAL` |

## Plain-text logs

Without the `prod` profile, for example in local development, logs use Spring Boot's normal text format. The audit events are the same, just not structured.
