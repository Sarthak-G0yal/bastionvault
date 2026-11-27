# Groups

Read every secret of a group in one call. This is what most consumers use.

## Read a group as JSON

```http
GET /api/v1/groups/{group}
```

Needs **read** on the group. Returns a flat object of key to value:

```json
{
  "DATABASE_URL": "postgres://affine:…@192.168.1.140:5432/affine",
  "REDIS_SERVER_HOST": "redis"
}
```

This is the shape the [External Secrets Operator webhook provider](/integrations/external-secrets) expects. Recorded as `READ_GROUP`.

## Read a group as `.env`

```http
GET /api/v1/env/{group}
```

Needs **read** on the group. Returns `text/plain` you can write straight to a file:

```bash
curl -s -H "Authorization: Bearer $TOKEN" $BV/api/v1/env/affine > .env
chmod 600 .env
```

```ini
DATABASE_URL='postgres://affine:…@192.168.1.140:5432/affine'
REDIS_SERVER_HOST='redis'
```

Output rules:

- `KEY='value'` when the value has no `'` and no newline. Single quotes are literal, with no `$` interpolation.
- Otherwise `KEY="value"` with `\\ \" \$ \n \r` escaped.
- Keys that are not valid variable names (such as `private.key`) are left out and listed in a trailing comment.

The output can be uploaded again unchanged with [`PUT /env/{group}`](/api/import-export#import-env-into-a-group). Recorded as `READ_ENV`.

### Use with Docker Compose

```bash
curl -s -H "Authorization: Bearer $BV_API_KEY" $BV/api/v1/env/affine > affine.env
docker compose --env-file affine.env up -d
rm affine.env
```

Both formats return `404` for a group that has no secrets.
