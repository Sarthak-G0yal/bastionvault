# Secrets

Endpoints for single values. Each is addressed by `{group}/{key}`. See [Concepts](/guide/concepts#groups-and-keys) for the naming rules.

## Read a secret

```http
GET /api/v1/secrets/{group}/{key}
```

Needs **read** on the group.

```json
{"group": "postgres", "key": "ADMIN_PASSWORD", "value": "…"}
```

`404` if the secret does not exist. Recorded as `READ_SECRET`.

```bash
curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/secrets/postgres/ADMIN_PASSWORD | jq -r .value
```

## Write a secret

```http
PUT /api/v1/secrets/{group}/{key}
Content-Type: application/json

{"value": "s3cret"}
```

Needs **write** on the group. Creates the secret, or replaces its value.

| Status | Meaning |
| :---: | :--- |
| `201` | Created |
| `204` | Updated |
| `400` | Invalid group or key name, or the value is over 64 KiB |

Multi-line values work. JSON-encode the newlines:

```bash
jq -Rs '{value: .}' < tls.key | curl -s -X PUT -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' --data-binary @- $BV/api/v1/secrets/traefik/tls.key
```

## Delete a secret

```http
DELETE /api/v1/secrets/{group}/{key}
```

Needs **write** on the group. Returns `204`, or `404` if the secret does not exist. Deleting is permanent.

## Generate a value if missing

```http
POST /api/v1/secrets/{group}/{key}/generate
Content-Type: application/json

{"length": 32}
```

Needs **read and write** on the group.

- If the secret **does not exist**, BastionVault stores a random value and returns `201` with `"created": true`.
- If it **already exists**, the stored value is returned unchanged with `200` and `"created": false`.

```json
{"group": "memos", "key": "DB_PASSWORD", "value": "…", "created": true}
```

`length` is optional. It defaults to 32 and must be between 16 and 256. The alphabet is letters and digits without look-alikes (`0 O 1 l I` are left out), so values are safe in URLs, shells and connection strings.

Because it is idempotent, you can call it on every run of a provisioning script. The first run creates the password and later runs read it back:

```yaml
# Ansible: same password every run, created on the first one
- name: Ensure the memos database password exists
  ansible.builtin.uri:
    url: "{{ bv_url }}/api/v1/secrets/memos/DB_PASSWORD/generate"
    method: POST
    headers: { Authorization: "Bearer {{ lookup('ansible.builtin.env', 'BV_API_KEY') }}" }
    body_format: json
    body: { length: 40 }
    status_code: [200, 201]
  register: memos_db
  no_log: true
```
