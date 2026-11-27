# Import and export

BastionVault reads and writes three formats: `.env`, JSON and YAML. Anything it exports, in any format, imports back to exactly the same values.

| Scope | Where in the UI | API |
| :--- | :--- | :--- |
| One group | **Secrets → Import file**, export links on each group | [`/env`, `/import/{group}`, `/export/{group}`](/api/import-export) |
| Whole vault | **Settings → Export and import everything** | [`/import`, `/export`](/api/import-export) |

## Import a file into one group

1. On **Secrets**, click **Import file**.
2. Choose the **Group** to import into. It is created if it does not exist.
3. Drop a `.env`, `.json` or `.yaml` file. The format is detected from the file name, and you can change it.
4. Check the **preview**. It lists every key with a masked value and what will happen to it: **create**, **overwrite** or **skip**. It also shows any parser warnings.
5. Tick **Overwrite secrets that already exist** if you want existing keys replaced. They are skipped by default.
6. Click **Import N secrets**.

Nothing is stored before step 6. The uploaded file itself is never stored, only the values from it, encrypted.

Limits: 256 KiB and 1000 entries per file.

## Export one group

Each group on the Secrets page has **Export: .env / JSON / YAML** links. The download is plain text. Treat it like the secret itself.

## Export everything

**Settings → Export and import everything → Export everything** produces one file with every group, as JSON or YAML:

```yaml
affine:
  DATABASE_URL: postgres://affine:…@192.168.1.140:5432/affine
memos:
  MEMOS_DSN: …
```

Because this file holds every secret in plain text, you must enter the **master password** first, even though you are already signed in. Encrypt the file (with SOPS or a password manager) and delete the download when you are done.

::: tip This is not a backup
Use export to move secrets between instances. For routine backups, dump PostgreSQL instead. It holds only ciphertext. See [Backup and recovery](/operations/backup-and-recovery).
:::

## Import a vault file

**Settings → Import vault file** takes a whole-vault JSON or YAML file (`{group: {KEY: value}}`). It shows a preview **per group** before writing anything, and offers the same overwrite choice.

## Format rules

### `.env`

Parsing follows Docker Compose:

- comments, blank lines and `export KEY=…` lines are handled
- `'single'` quotes are literal
- `"double"` quotes unescape `\n \r \t \\ \" \$`
- quoted values may span several lines, for example PEM keys
- unquoted values are trimmed, and ` #` (space, then hash) starts a comment
- duplicate keys keep the last value
- empty values and invalid key names are skipped with a warning

When BastionVault **writes** `.env`:

- `KEY='value'` when the value contains no `'` and no newline. Single quotes are literal, so there is no `$` interpolation.
- otherwise `KEY="value"` with `\\ \" \$ \n \r` escaped
- keys that are not valid variable names (such as `private.key`) are left out and listed in a trailing comment. Use JSON or YAML for those.

### JSON and YAML

| Shape | JSON | YAML |
| :--- | :--- | :--- |
| One group | `{"DB_PASS": "x", "PORT": "5432"}` | `DB_PASS: x`<br>`PORT: "5432"` |
| Whole vault | `{"affine": {"DATABASE_URL": "…"}}` | `affine:`<br>`  DATABASE_URL: …` |

- Values are always strings.
- YAML values are kept **exactly as written**: `yes`, `0123` and `5432` stay those strings instead of becoming `true`, `83` or a number.
- JSON numbers and booleans are accepted and stored as their text, with a warning.
- Multi-line values are exported as YAML literal blocks (`|`).
- Nested objects or lists inside a group, `null`, and empty values are skipped with a warning.
- YAML aliases are limited, so "billion laughs" files are refused.
- Limits: 256 KiB per group file, 4 MiB per vault file.
