# External Secrets Operator

[External Secrets Operator](https://external-secrets.io) (ESO) syncs values from an external store into Kubernetes Secrets. BastionVault works with ESO's **webhook provider**: ESO calls `GET /api/v1/groups/{group}` and turns the JSON into Secret keys.

::: warning Check against your ESO version
These manifests follow the ESO webhook provider documentation, but this repository's tests do not run them. The API version (`external-secrets.io/v1`) and field names can change between ESO releases.
:::

## 1. Create the client

In **Machine identities**:

1. **New client** → client ID `external-secrets`.
2. **Access → Rules**: add the groups it may sync, with **Read** only. For example `affine`, `memos`, `linkding`, or `*` if it syncs everything.
3. **Access → API keys → Create API key**, for example named `eso`. Copy the `bvk_…` value.

## 2. Store the API key in the cluster

The API key is a bootstrap secret, so it cannot come from BastionVault itself. Create it directly, or commit it encrypted with SOPS:

```bash
kubectl -n external-secrets create secret generic bastionvault-eso \
  --from-literal=token='bvk_…'
```

## 3. ClusterSecretStore

```yaml
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata:
  name: bastionvault
spec:
  provider:
    webhook:
      url: "http://bastionvault.bastionvault.svc:8080/api/v1/groups/{{ .remoteRef.key }}"
      headers:
        Authorization: "Bearer {{ print .auth.token }}"
      result:
        jsonPath: "$"
      secrets:
        - name: auth
          secretRef:
            name: bastionvault-eso
            namespace: external-secrets
            key: token
```

- The URL uses the in-cluster Service, so traffic never leaves the cluster. Change it if BastionVault runs elsewhere, for example `https://bastionvault.homelab118.home/...`.
- <code v-pre>{{ .remoteRef.key }}</code> is replaced with the group name from each ExternalSecret.

## 4. ExternalSecret per app

All keys of a group become keys of one Secret:

```yaml
apiVersion: external-secrets.io/v1
kind: ExternalSecret
metadata:
  name: affine-secret
  namespace: affine
spec:
  refreshInterval: 1h
  secretStoreRef: { kind: ClusterSecretStore, name: bastionvault }
  target: { name: affine-secret }
  dataFrom:
    - extract: { key: affine }   # group "affine"
```

Then use it as usual:

```yaml
envFrom:
  - secretRef: { name: affine-secret }
```

To pick single values, use `data` instead of `dataFrom`:

```yaml
  data:
    - secretKey: password          # key in the Kubernetes Secret
      remoteRef:
        key: postgres              # group
        property: ADMIN_PASSWORD   # key in the group
```

## Behaviour while sealed

While BastionVault is sealed, ESO's requests fail with `503`. ESO **keeps the Secrets it already synced**, so running apps are unaffected. New ExternalSecrets, and changes to values, wait until the vault is unsealed. Use [auto-unseal](/deploy/auto-unseal) if that is not acceptable.

## Troubleshooting

| ESO status or event | Likely cause |
| :--- | :--- |
| `401` | Wrong or revoked API key, or the client is disabled |
| `403` | No read rule for that group on `external-secrets` |
| `404` | The group has no secrets, or there is a typo in `key` |
| `503` | The vault is sealed |
| Connection refused | Wrong Service name or namespace in the store URL |

Every read appears in the [audit log](/ui/audit) as `READ_GROUP` by `external-secrets`.
