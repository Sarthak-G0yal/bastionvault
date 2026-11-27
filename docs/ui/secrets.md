# Managing secrets

The **Secrets** page is the home page of the UI.

## Overview figures

The top of the page shows:

- the number of **groups** and **secrets**
- **machine clients**: active ones, out of all registered
- **API reads in the last 24 hours**, compared with the 24 hours before
- a bar chart of **API reads over the last 14 days**
- **Recent machine access**: which clients called the API most recently

These come from the audit log, so they cover machine reads only, not your own reveals in the UI.

## Groups

Secrets are listed per group. Each group box shows its keys, which clients can open it according to the current policy rules, the last change, and the last read.

Use **Search** to filter by group or key name. A group that matches opens automatically.

Each group also has export links: **.env**, **JSON** and **YAML**. See [Import and export](/ui/import-export).

## Add a secret

1. Click **New secret**.
2. Fill in **Group**, **Key** and **Value**. Multi-line values such as PEM keys are kept exactly as typed.
3. Click **Save secret**.

The secret is encrypted before it is written. The audit log records `WRITE_SECRET` with your username.

### Generate a random value

In the same dialog, **Generate value** stores a random 32-character value, but only **if the secret does not exist yet**. An existing secret is left unchanged, and the dialog tells you so.

Generated values use letters and digits without look-alike characters (no `0 O 1 l I`). For a different length, use the [API](/api/secrets#generate-a-value-if-missing), which accepts 16 to 256 characters.

## Row actions

Each secret row has these buttons:

| Button | Effect |
| :--- | :--- |
| **Reveal** (eye) | Decrypts the value and shows it in a dialog. Recorded as `REVEAL_SECRET`. |
| **Copy address** | Copies `group/key` to the clipboard, the path you use in the API |
| **Replace value** (pencil) | Opens the editor with group and key fixed. You enter a new value. |
| **More → Delete** | Deletes the secret after confirmation |

::: warning Deleting is final
Machines that read a deleted secret get `404` from then on. There is no trash or version history. Export the group first if you might need the value again.
:::

## While the vault is sealed

Nothing on this page works while the vault is sealed. You are sent to **Unseal** first.
