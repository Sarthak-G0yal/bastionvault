# Reverse proxy and TLS

BastionVault speaks **plain HTTP** on port `8080`. Passwords, tokens and secret values cross that connection, so put it behind a TLS-terminating proxy and do not expose `8080` beyond the proxy and in-cluster consumers.

## Traefik (file provider)

A route for a Traefik instance outside the cluster, forwarding to a Kubernetes NodePort:

```yaml
http:
  routers:
    bastionvault:
      rule: Host(`bastionvault.homelab118.home`)
      entryPoints: [websecure]
      service: bastionvault
      tls: {}
  services:
    bastionvault:
      loadBalancer:
        servers:
          - url: http://192.168.1.170:30011
```

In the homelab's Ansible inventory this is one entry in `reverse_proxy.yml`:

```yaml
  - name: bastionvault
    host: "bastionvault.{{ traefik_domain }}"
    url: "http://192.168.1.170:30011"
```

Then set on BastionVault:

```ini
VAULT_COOKIE_SECURE=true
VAULT_TRUSTED_PROXIES=192\.168\.1\.122
```

## Trusted proxies

Behind a proxy, every request seems to come from the proxy's IP. The lockout and the audit log need the **real** client IP, which the proxy sends in `X-Forwarded-For`.

BastionVault trusts that header **only** when the TCP connection comes from an address that matches `VAULT_TRUSTED_PROXIES`. Otherwise the header is ignored, so a client can't fake its IP to escape the lockout.

`VAULT_TRUSTED_PROXIES` is a **Java regular expression** matched against the peer address:

| Setup | Value |
| :--- | :--- |
| No proxy, or proxy on the same host | `127\.0\.0\.1` (default, plus `::1`) |
| One Traefik host | `192\.168\.1\.122` |
| Two proxies | `192\.168\.1\.122\|192\.168\.1\.123` |
| A subnet such as `10.42.0.0/16` | `10\.42\.\d{1,3}\.\d{1,3}` |

Escape the dots. An unescaped `.` matches any character.

::: tip Kubernetes NodePort
With a NodePort Service, kube-proxy may rewrite the source address to the node's IP. Use `externalTrafficPolicy: Local` so the pod sees the proxy's real address, or add the node IP to `VAULT_TRUSTED_PROXIES`.
:::

Check it: sign in through the proxy, then open **Audit log**. The `LOGIN` row should show your workstation's IP, not the proxy's.

## Cookies

With `VAULT_COOKIE_SECURE=true`, the browser sends the session cookie only over HTTPS. If you set it while still testing on plain `http://host:8080`, sign-in appears to work but you are sent straight back to the login page. Use HTTPS, or leave it `false` for local testing only.

## Keep the API internal

Machines on the LAN and in the cluster are usually the only API consumers. Don't publish BastionVault to the internet. If you need remote access, use a VPN such as WireGuard or Tailscale instead of a public route.
