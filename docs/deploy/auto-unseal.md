# Auto-unseal

By default BastionVault starts **sealed** and waits for a person to enter the master password or the recovery key. That is the safest setup, but after a power cut or node reboot nothing can read secrets until you unseal.

Auto-unseal lets the vault unseal itself on start by reading an **unseal key** from a file.

## The trade-off

| | Manual unseal | Auto-unseal |
| :--- | :--- | :--- |
| After a restart | Sealed until someone unseals | Unsealed within seconds |
| Someone who steals the database | Gets ciphertext only | Gets ciphertext only |
| Someone with cluster access (can read the Secret or exec into the pod) | Can't unseal | **Can unseal** |
| Good for | Vaults that are rarely restarted, and admins who are always around | A homelab where apps must come back on their own |

External Secrets Operator keeps already-synced Secrets while the vault is sealed, so running apps are not affected either way. Auto-unseal matters for new syncs, value changes, and scripts.

## Turn it on

1. **Settings → Auto-unseal → Generate unseal key**, then confirm.
2. Copy the `bvu_…` key. It is shown once.
3. Store it where the pod can read it as a file:

   ::: code-group

   ```bash [kubectl]
   kubectl -n bastionvault create secret generic bastionvault-unseal \
     --from-literal=unseal.key='bvu_…'
   ```

   ```yaml [SOPS manifest]
   # encrypt with: sops -e -i unseal-secret.yaml
   apiVersion: v1
   kind: Secret
   metadata:
     name: bastionvault-unseal
     namespace: bastionvault
   stringData:
     unseal.key: bvu_…
   ```

   :::

4. Mount it and set `VAULT_UNSEAL_KEY_FILE` (see [Deploy on Kubernetes](/deploy/kubernetes#deployment)).
5. Restart the pod. The log shows `Vault auto-unsealed`, and the audit log has an `UNSEAL` row by the system with target `auto-unseal`.

## What happens on start

| Situation | Log line | Result |
| :--- | :--- | :--- |
| Key valid | `Vault auto-unsealed` | Unsealed |
| Key wrong or rotated | `Auto-unseal failed: the configured key does not match (rotated or disabled?). Vault stays sealed.` | Sealed. A `FAILED` audit row. Unseal in the UI and fix the Secret. |
| File not readable | `Auto-unseal key file … is not readable: …` | Sealed |
| Nothing configured | `Vault is sealed; no auto-unseal key configured. Unseal it in the UI.` | Sealed (manual mode) |

The master password and recovery key keep working in every case.

## Rotate the key

**Generate unseal key** again. The previous key stops working immediately. Update the Secret **before the next restart**, otherwise the vault comes up sealed.

## Turn it off

**Settings → Auto-unseal → Turn off** deletes the unseal key's wrapped copy of the data key. Remove the Secret and the variable as well. After the next restart the vault stays sealed until someone unseals it.

## Back up the unseal key

The unseal key is also a recovery path: with the database dump and the unseal key, you can restore the vault. If the Secret is kept in git encrypted with SOPS, your age key effectively protects the vault, so back up the age key offline too. See [Backup and recovery](/operations/backup-and-recovery).
