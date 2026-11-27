---
# https://vitepress.dev/reference/default-theme-home-page
layout: home
title: BastionVault
titleTemplate: Self-hosted secrets for your homelab

hero:
  name: "Your homelab's secrets,"
  text: "sealed on your own hardware"
  tagline: One place for every password, API key and .env file. You manage them in a web UI, your automation reads them over a token-protected API, and the encryption key never leaves memory.
  actions:
    - theme: brand
      text: Get started
      link: /guide/getting-started
    - theme: alt
      text: How it works
      link: /guide/how-it-works
    - theme: alt
      text: GitHub
      link: https://github.com/Sarthak-G0yal/bastionvault

features:
  - icon: <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
    title: Sealed by default
    details: The data key lives only in memory. A restart or the Seal button drops it, and nothing can be read until you unseal with the master password, the recovery key or the auto-unseal key.
    link: /guide/how-it-works
    linkText: How sealing works
  - icon: <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>
    title: Deny-by-default access
    details: Each machine client gets glob rules such as "app-*" read. With no rule, it gets no access. Short-lived session tokens and revocable API keys.
    link: /guide/concepts
    linkText: Clients and rules
  - icon: <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect width="20" height="8" x="2" y="2" rx="2" ry="2"/><rect width="20" height="8" x="2" y="14" rx="2" ry="2"/><line x1="6" x2="6.01" y1="6" y2="6"/><line x1="6" x2="6.01" y1="18" y2="18"/></svg>
    title: Built for automation
    details: Works with External Secrets Operator, Ansible, Terraform and plain curl. Groups come out as JSON, YAML or .env files.
    link: /integrations/
    linkText: Integrations
  - icon: <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m17 8-5-5-5 5"/><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/></svg>
    title: Import and export
    details: Upload a .env, JSON or YAML file and check a preview before anything is stored. Export one group or the whole vault, and the same values import back.
    link: /ui/import-export
    linkText: Formats
  - icon: <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M15 12h-5"/><path d="M15 8h-5"/><path d="M19 17V5a2 2 0 0 0-2-2H4"/><path d="M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3"/></svg>
    title: Audited
    details: Every read, write, reveal, login, unseal and policy denial is recorded in PostgreSQL and in ECS JSON logs ready for Loki. Secret values are never logged.
    link: /operations/logging-and-audit
    linkText: Audit fields
  - icon: <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect width="20" height="5" x="2" y="3" rx="1"/><path d="M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8"/><path d="M10 12h4"/></svg>
    title: Recoverable
    details: To recover, you need a pg_dump plus one of three keys. Losing the server does not mean losing your secrets.
    link: /operations/backup-and-recovery
    linkText: Backup and recovery
---
