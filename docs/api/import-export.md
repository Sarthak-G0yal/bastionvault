# Import and export

Bulk endpoints for moving whole groups or the whole vault. The format rules are the same as in the UI. See [Format rules](/ui/import-export#format-rules).

## Formats

| `format` | Content-Type | Group shape | Vault shape |
| :--- | :--- | :--- | :--- |
| `json` | `application/json` | `{"KEY": "value"}` | `{"group": {"KEY": "value"}}` |
| `yaml` | `application/yaml` | `KEY: value` | `group:`<br>`  KEY: value` |
| `env` | `text/plain` | `KEY='value'` lines | (groups only) |

For imports, the format comes from `?format=` if given, otherwise from the request's `Content-Type` (JSON or YAML), otherwise `.env`.

## Export one group

```http
GET /api/v1/export/{group}?format=json
```

Needs **read** on the group. `format` is `json` (default), `yaml` or `env`. The response is a download (`Content-Disposition: attachment; filename="{group}.json"`) with `Cache-Control: no-store`. `404` if the group is empty.

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BV/api/v1/export/affine?format=yaml" > affine.yaml
```

## Export every readable group

```http
GET /api/v1/export?format=json
```

Returns `{group: {KEY: value}}` for **every group the client can read**. Groups it can't read are left out silently, so no `403` is returned. The download is named `bastionvault-export.json` or `.yaml`. Recorded as `EXPORT_ALL` with the number of groups.

## Import `.env` into a group

```http
PUT /api/v1/env/{group}?overwrite=false
Content-Type: text/plain

DB_PASSWORD=…
export API_KEY="…"
```

Needs **write** on the group. The body is parsed as a `.env` file.

## Import into one group

```http
PUT /api/v1/import/{group}?format=yaml&overwrite=false
```

Needs **write** on the group. The body is a group file in any of the three formats.

```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/yaml' \
  --data-binary @affine.yaml "$BV/api/v1/import/affine?overwrite=true"
```

Response for both group imports:

```json
{
  "group": "affine",
  "created": 3,
  "updated": 0,
  "skippedExisting": 2,
  "warnings": ["line 7: OPTIONAL_FLAG has an empty value, skipped"]
}
```

## Import many groups

```http
PUT /api/v1/import?format=json&overwrite=false
```

The body is a vault file. The client needs **write on every group in the file**. If any group is not writable, the whole request is refused with `403` before anything is written, and the error lists the groups.

```json
{"groups": 4, "created": 17, "updated": 0, "skippedExisting": 3, "warnings": []}
```

## `overwrite`

| Value | Existing keys | New keys |
| :--- | :--- | :--- |
| `false` (default) | kept, counted in `skippedExisting` | created |
| `true` | replaced, counted in `updated` | created |

Keys that are in the vault but not in the file are **never** deleted.

## Limits and errors

| Limit | Value |
| :--- | :--- |
| Group file | 256 KiB |
| `.env` entries | 1000 |
| Vault file | 4 MiB |
| One value | 64 KiB |

An empty body, an oversized file, or a file that can't be parsed returns `400` with a message.
