# Deploy on Kubernetes

This page deploys BastionVault to a cluster (for example K3s) against an external PostgreSQL server such as the homelab's `database` LXC at `192.168.1.140`.

::: warning Example manifests
These manifests show a working shape. They are not part of the repository's tests. Adjust names, namespaces, ports and versions to your cluster.
:::

## Requirements

- A PostgreSQL database and user for BastionVault (see [Use an existing PostgreSQL server](/deploy/docker-compose#use-an-existing-postgresql-server))
- A way to create the bootstrap Secrets: `kubectl`, or SOPS-encrypted manifests applied by your GitOps tool

## Run exactly one replica

BastionVault keeps the data key, session tokens, UI sessions and lockout counters **in memory**. Two pods would each need unsealing separately and would not share tokens or sessions. Run `replicas: 1` with the `Recreate` strategy.

## Manifests

### Namespace and bootstrap Secrets

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: bastionvault
---
# Database password. Keep this file encrypted (SOPS) or create it with kubectl.
apiVersion: v1
kind: Secret
metadata:
  name: bastionvault-db
  namespace: bastionvault
stringData:
  DB_PASSWORD: "…"
---
# Optional: auto-unseal key, generated in Settings → Auto-unseal
apiVersion: v1
kind: Secret
metadata:
  name: bastionvault-unseal
  namespace: bastionvault
stringData:
  unseal.key: "bvu_…"
```

### Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bastionvault
  namespace: bastionvault
spec:
  replicas: 1
  strategy: { type: Recreate }
  selector:
    matchLabels: { app: bastionvault }
  template:
    metadata:
      labels: { app: bastionvault }
    spec:
      securityContext:
        runAsNonRoot: true
      containers:
        - name: bastionvault
          image: sarthakg0yal/bastionvault:0.2.0   # pin a version
          ports:
            - { name: http, containerPort: 8080 }
          env:
            - { name: DB_URL, value: "jdbc:postgresql://192.168.1.140:5432/bastionvault" }
            - { name: DB_USERNAME, value: bastionvault }
            - name: DB_PASSWORD
              valueFrom: { secretKeyRef: { name: bastionvault-db, key: DB_PASSWORD } }
            - { name: VAULT_COOKIE_SECURE, value: "true" }
            - { name: VAULT_TRUSTED_PROXIES, value: "192\\.168\\.1\\.122" }   # Traefik
            - { name: VAULT_UNSEAL_KEY_FILE, value: /etc/bastionvault/unseal.key }
          volumeMounts:
            - { name: unseal, mountPath: /etc/bastionvault, readOnly: true }
          resources:
            requests: { cpu: 100m, memory: 384Mi }
            limits: { memory: 768Mi }
          startupProbe:
            httpGet: { path: /api/v1/health, port: http }
            periodSeconds: 5
            failureThreshold: 36        # up to 3 minutes for the first start
          livenessProbe:
            httpGet: { path: /api/v1/health, port: http }
          readinessProbe:
            httpGet: { path: /api/v1/health, port: http }
      volumes:
        - name: unseal
          secret:
            secretName: bastionvault-unseal
            optional: true              # without it the vault starts sealed
            defaultMode: 0440
```

All three probes use `/api/v1/health` on purpose. See [Health](/api/health#why-there-are-two).

### Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bastionvault
  namespace: bastionvault
spec:
  type: NodePort
  externalTrafficPolicy: Local    # keep the real client IP
  selector: { app: bastionvault }
  ports:
    - { name: http, port: 8080, targetPort: http, nodePort: 30011 }
```

- In-cluster consumers such as External Secrets Operator use `http://bastionvault.bastionvault.svc:8080`.
- People and outside machines reach it through Traefik, which forwards `bastionvault.homelab118.home` to `http://<node-ip>:30011`. See [Reverse proxy and TLS](/deploy/reverse-proxy).
- `ClusterIP` with an Ingress works just as well if Traefik runs inside the cluster.

## First start

```bash
kubectl apply -f namespace-secrets.yaml -f deployment.yaml -f service.yaml
kubectl -n bastionvault logs deploy/bastionvault | grep "setup token"
```

Open `/setup` through the proxy and finish [setup](/guide/getting-started#_3-run-setup). Then, if you want unattended restarts, [generate an auto-unseal key](/deploy/auto-unseal), put it into `bastionvault-unseal`, and restart the pod.

## Restrict network access

Only the proxy and the consumers that need BastionVault should reach it. With a CNI that enforces NetworkPolicy:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: bastionvault
  namespace: bastionvault
spec:
  podSelector: { matchLabels: { app: bastionvault } }
  policyTypes: [Ingress]
  ingress:
    - from:
        - namespaceSelector:
            matchLabels: { kubernetes.io/metadata.name: external-secrets }
        - ipBlock: { cidr: 192.168.1.122/32 }   # Traefik
      ports:
        - { port: 8080 }
```

## Who can unseal

With auto-unseal on, anyone who can read the `bastionvault-unseal` Secret, or exec into the pod, can get at the secrets. Limit RBAC on the `bastionvault` namespace accordingly. See [Security model](/security/model).
