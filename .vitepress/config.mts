import { defineConfig } from 'vitepress'

const repo = 'https://github.com/Sarthak-G0yal/bastionvault'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  srcDir: 'docs',

  // "/" for a custom domain or your own server, "/bastionvault/" for a GitHub Pages project site
  base: process.env.DOCS_BASE ?? '/',

  lang: 'en-US',
  title: 'BastionVault',
  titleTemplate: ':title · BastionVault',
  description:
    'A self-hosted secrets engine for homelab automation. A web UI for people, a token-protected API for machines, AES-256-GCM at rest.',
  cleanUrls: true,
  lastUpdated: true,

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }],
    ['meta', { name: 'theme-color', content: '#f7f4ee', media: '(prefers-color-scheme: light)' }],
    ['meta', { name: 'theme-color', content: '#161513', media: '(prefers-color-scheme: dark)' }],
    ['link', { rel: 'preload', href: '/fonts/Geist-Variable.woff2', as: 'font', type: 'font/woff2', crossorigin: '' }],
  ],

  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: { src: '/logo.svg', width: 24, height: 24, alt: '' },
    siteTitle: 'BastionVault',

    nav: [
      { text: 'Guide', link: '/guide/what-is-bastionvault', activeMatch: '/guide/' },
      { text: 'Web UI', link: '/ui/', activeMatch: '/ui/' },
      { text: 'API', link: '/api/', activeMatch: '/api/' },
      { text: 'Integrations', link: '/integrations/', activeMatch: '/integrations/' },
      {
        text: 'Deploy',
        activeMatch: '/(deploy|operations)/',
        items: [
          {
            text: 'Deploy',
            items: [
              { text: 'Docker Compose', link: '/deploy/docker-compose' },
              { text: 'Kubernetes', link: '/deploy/kubernetes' },
              { text: 'Configuration', link: '/deploy/configuration' },
            ],
          },
          {
            text: 'Operate',
            items: [
              { text: 'Backup and recovery', link: '/operations/backup-and-recovery' },
              { text: 'Upgrading', link: '/operations/upgrading' },
              { text: 'Troubleshooting', link: '/operations/troubleshooting' },
            ],
          },
        ],
      },
      {
        text: 'v0.2',
        items: [
          { text: 'Releases', link: `${repo}/releases` },
          { text: 'Docker Hub', link: 'https://hub.docker.com/r/sarthakg0yal/bastionvault' },
          { text: 'Contributing', link: '/project/contributing' },
          { text: 'License', link: '/project/license' },
        ],
      },
    ],

    sidebar: [
      {
        text: 'Introduction',
        items: [
          { text: 'What is BastionVault?', link: '/guide/what-is-bastionvault' },
          { text: 'Getting started', link: '/guide/getting-started' },
          { text: 'Concepts', link: '/guide/concepts' },
          { text: 'How it works', link: '/guide/how-it-works' },
        ],
      },
      {
        text: 'Web UI',
        collapsed: false,
        items: [
          { text: 'Overview', link: '/ui/' },
          { text: 'Managing secrets', link: '/ui/secrets' },
          { text: 'Import and export', link: '/ui/import-export' },
          { text: 'Machine identities', link: '/ui/clients' },
          { text: 'Audit log', link: '/ui/audit' },
          { text: 'Settings', link: '/ui/settings' },
        ],
      },
      {
        text: 'API reference',
        collapsed: false,
        items: [
          { text: 'Overview', link: '/api/' },
          { text: 'Authentication', link: '/api/authentication' },
          { text: 'Secrets', link: '/api/secrets' },
          { text: 'Groups', link: '/api/groups' },
          { text: 'Import and export', link: '/api/import-export' },
          { text: 'Health', link: '/api/health' },
        ],
      },
      {
        text: 'Integrations',
        collapsed: false,
        items: [
          { text: 'Overview', link: '/integrations/' },
          { text: 'External Secrets Operator', link: '/integrations/external-secrets' },
          { text: 'Ansible', link: '/integrations/ansible' },
          { text: 'Terraform and OpenTofu', link: '/integrations/terraform' },
          { text: 'Scripts and CI', link: '/integrations/scripts' },
        ],
      },
      {
        text: 'Deploy',
        collapsed: false,
        items: [
          { text: 'Docker Compose', link: '/deploy/docker-compose' },
          { text: 'Kubernetes', link: '/deploy/kubernetes' },
          { text: 'Configuration', link: '/deploy/configuration' },
          { text: 'Auto-unseal', link: '/deploy/auto-unseal' },
          { text: 'Reverse proxy and TLS', link: '/deploy/reverse-proxy' },
        ],
      },
      {
        text: 'Operations',
        collapsed: false,
        items: [
          { text: 'Backup and recovery', link: '/operations/backup-and-recovery' },
          { text: 'Logging and audit', link: '/operations/logging-and-audit' },
          { text: 'Upgrading', link: '/operations/upgrading' },
          { text: 'Troubleshooting', link: '/operations/troubleshooting' },
        ],
      },
      {
        text: 'Security',
        items: [{ text: 'Security model', link: '/security/model' }],
      },
      {
        text: 'Project',
        items: [
          { text: 'Development', link: '/development/' },
          { text: 'Contributing', link: '/project/contributing' },
          { text: 'License', link: '/project/license' },
        ],
      },
    ],

    outline: { level: [2, 3], label: 'On this page' },

    search: { provider: 'local' },

    editLink: {
      pattern: `${repo}/edit/docs/docs/:path`,
      text: 'Edit this page on GitHub',
    },

    socialLinks: [{ icon: 'github', link: repo }],

    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2026 Sarthak Goyal',
    },
  },
})
