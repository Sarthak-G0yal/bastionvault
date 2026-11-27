# Using the web UI

The UI is served by BastionVault itself on the same port as the API (`8080`). Every page except **Login**, **Setup** and **Unseal** needs a signed-in admin.

## Pages

| Page | Path | What you do there |
| :--- | :--- | :--- |
| Setup | `/setup` | First-time initialization. Only works while the vault is uninitialized. |
| Login | `/login` | Sign in with the admin username and login password |
| Unseal | `/unseal` | Load the data key with the master password or the recovery key |
| [Secrets](/ui/secrets) | `/` | Browse groups, add, reveal, replace, delete, import and export secrets |
| [Machine identities](/ui/clients) | `/clients` | Clients, their policy rules and API keys |
| [Audit log](/ui/audit) | `/audit` | Recent events with actor, outcome and source IP |
| [Settings](/ui/settings) | `/settings` | Passwords, recovery key, auto-unseal, export or import everything, 0.1 import |

## Sidebar

The left sidebar holds the navigation, a card showing whether the vault is sealed or unsealed with a **Seal vault** button, and at the bottom your username with the theme switch and **Log out**.

- **Seal vault** drops the data key from memory right away. Every machine client gets `503` until someone unseals again.
- **Log out** ends your UI session. It does **not** seal the vault.

## Theme

The sun/moon button switches between light and dark. The choice is stored in the browser (`localStorage`, key `bv-theme`). Without a stored choice, the UI follows the operating system's `prefers-color-scheme`.

## Sessions

- The session cookie is `HttpOnly` and `SameSite=Strict`. It is also `Secure` when `VAULT_COOKIE_SECURE=true`, which you should set when serving over HTTPS.
- After `VAULT_SESSION_TIMEOUT` (30 minutes by default) without activity, you have to sign in again.
- After 5 failed sign-ins for one username (or 20 from one IP) within 15 minutes, sign-in is locked for 15 minutes.
