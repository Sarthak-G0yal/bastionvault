# Concepts

## Overview

| Thing | What it is | Example |
| :--- | :--- | :--- |
| **Group** | A namespace for related secrets, usually one per app | `affine`, `firefly3`, `postgres` |
| **Key** | The name of one secret in a group | `DATABASE_URL`, `private.key` |
| **Secret** | One encrypted value, identified by `(group, key)` | `affine / DATABASE_URL` |
| **Admin** | A person who signs in to the web UI | `admin` |
| **Client** | A machine identity with a client ID and a client secret | `external-secrets`, `ansible` |
| **Policy rule** | Grants a client read and/or write on groups whose name matches a glob | `app-*` read |
| **Session token** | `bvt_…`, short-lived, from `POST /api/v1/auth/token` | scripts, CI jobs |
| **API key** | `bvk_<id>_<secret>`, long-lived, revocable, optional expiry | External Secrets Operator |

## Groups and keys

A **group** collects the secrets of one consumer, usually one app. Machines can fetch a whole group in a single call, as JSON (`/groups/{group}`) or as a `.env` file (`/env/{group}`). That makes one group per app the natural layout.

A **key** names one value inside a group.

| Name | Allowed characters |
| :--- | :--- |
| Group | `[A-Za-z0-9][A-Za-z0-9._-]*`: starts with a letter or digit, then letters, digits, `.`, `_`, `-` |
| Key | `[A-Za-z0-9_][A-Za-z0-9_.-]*`: starts with a letter, digit or `_`, then letters, digits, `_`, `.`, `-` |
| Client ID | Lowercase letters, digits, dot, dash or underscore |

Values are strings of up to **64 KiB**. They may be multi-line, for example PEM keys or certificates.

::: tip Keys that are not variable names
A key like `private.key` is valid, but it is not a valid shell variable name, so it is left out of `.env` output. It is still available through the JSON and YAML endpoints.
:::

## Admins and the vault state

Using the UI involves two separate steps:

1. **Signing in** proves who you are. You need the admin **username** and **login password**.
2. **Unsealing** loads the data key into memory. You need the **master password**, the **recovery key** or the **auto-unseal key**.

These are different secrets on purpose. Someone who steals your login password still cannot decrypt anything while the vault is sealed.

The vault is always in one of three states:

| State | Meaning |
| :--- | :--- |
| **Uninitialized** | Fresh install. Only `/setup` works. |
| **Sealed** | Initialized, but the data key is not in memory. Secrets cannot be read or written. The API returns `503`. |
| **Unsealed** | The data key is in memory. Everything works. |

A restart always returns the vault to **sealed**, unless [auto-unseal](/deploy/auto-unseal) is configured.

## Clients and policy rules

A **client** is a machine identity: a client ID (`ansible`) plus a client secret. Only the secret's BCrypt hash is stored, and you see the secret once, when you create or rotate it.

A client has **no access** until you add policy rules. Each rule has:

- a **group pattern**, a glob where `*` matches any characters and `?` matches one character
- **Read** and/or **Write**. A rule must grant at least one.

A request is allowed if **at least one** of the client's rules matches the group name and grants the capability needed.

| Rules on client `external-secrets` | `GET /groups/affine` | `PUT /secrets/affine/X` | `GET /groups/postgres` |
| :--- | :---: | :---: | :---: |
| `affine` read | ✅ | ❌ 403 | ❌ 403 |
| `*` read | ✅ | ❌ 403 | ✅ |
| `affine` read+write | ✅ | ✅ | ❌ 403 |

Clients can be **disabled** (all credentials stop working, nothing is deleted), **re-enabled**, **rotated** (new secret, old secret and its tokens stop working, API keys keep working) or **deleted** (with all rules and API keys).

## Session tokens and API keys

A client authenticates in one of two ways. Both are sent as `Authorization: Bearer …`.

| | Session token `bvt_…` | API key `bvk_<id>_<secret>` |
| :--- | :--- | :--- |
| How you get it | `POST /api/v1/auth/token` with client ID and secret | **Machine identities → Access → Create API key** in the UI |
| Lifetime | 15 min by default, 60 min maximum | Until revoked, or an optional expiry in days |
| Stored | In memory only, gone on restart or seal | SHA-256 hash in the database |
| Best for | Scripts and CI jobs that already hold the client secret | Long-running consumers like External Secrets Operator |
| Visibility | Last token time per client | Last used time per key |

An API key has exactly the same access as its client: the client's policy rules apply.

## Where to go next

- [How it works](/guide/how-it-works): what happens on seal, unseal and every read
- [Managing secrets](/ui/secrets) and [Machine identities](/ui/clients) in the UI
- [API reference](/api/)
