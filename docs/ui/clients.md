# Machine identities

The **Machine identities** page manages the clients that use the API: External Secrets Operator, Ansible, Terraform, CI jobs and scripts.

The list shows each client's ID, display name, status, the groups it **can open** according to its rules, its API keys, and when it last got a token.

## Create a client

1. Click **New client**.
2. Enter a **Client ID** (lowercase letters, digits, dot, dash or underscore, for example `external-secrets`) and a **Display name**.
3. Click **Create client**.
4. Copy the **client secret** from the dialog. It is shown only once. **Done** stays disabled until you tick *I have stored this somewhere safe*. The recovery key, unseal key and API key dialogs work the same way.

The client can use the secret with `POST /api/v1/auth/token`, but it can open **nothing** until you add a rule.

## Grant access: rules

Click **Access** on a client.

Under **Rules**, add a **Group pattern** with **Read**, **Write** or both:

| Pattern | Matches |
| :--- | :--- |
| `affine` | only the group `affine` |
| `app-*` | `app-memos`, `app-linkding`, … |
| `db?` | `db1`, `db2`, but not `db10` |
| `*` | every group |

`*` matches any characters and `?` matches one character. With no rule, there is no access. Remove a rule with its trash button. The change applies to the next request.

Give each client the smallest set it needs. For example, External Secrets Operator usually needs **read** on the app groups and nothing else.

## API keys

Under **API keys** on the same dialog:

1. Enter a **Key name**, for example `eso-prod`.
2. Optionally set **Expires in days** (1 or more). Leave it empty for no expiry.
3. Click **Create API key** and copy the `bvk_…` value. It is shown once.

Send it as `Authorization: Bearer bvk_…`. It has the same access as its client.

The table shows when each key was last used and when it expires. **Revoke key** (×) stops it working immediately. Revoked keys stay listed for reference.

## Other actions

Under **More** (⋯) on a client:

| Action | Effect |
| :--- | :--- |
| **Rotate secret** | Issues a new client secret. The old secret and its active session tokens stop working now. **API keys keep working.** Update the consumer's configuration. |
| **Disable** / **Enable** | A disabled client cannot authenticate: its secret and its API keys are refused. Nothing is deleted. |
| **Delete** | Removes the client, its rules and all its API keys. This cannot be undone. |

All of these are recorded in the [audit log](/ui/audit).

## Lockout

After 5 failed authentications for one client ID (or 20 from one IP) within 15 minutes, that client ID or IP is locked out for 15 minutes and gets `429`. The lockout state is held in memory and resets on restart.
