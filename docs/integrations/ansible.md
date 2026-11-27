# Ansible

Ansible reads secrets with the built-in `url` lookup or the `uri` module. No collection is needed.

## Setup

1. Create a client `ansible` with read rules for the groups your playbooks use. Add write as well if playbooks should [generate](/api/secrets#generate-a-value-if-missing) passwords.
2. Create an API key for it and export it on the control node:

```bash
export BV_API_KEY='bvk_…'
```

3. Set the URL once, for example in `group_vars/all.yml`:

```yaml
bv_url: https://bastionvault.homelab118.home
```

## Read one value

```yaml
- name: Read the Postgres admin password from BastionVault
  ansible.builtin.set_fact:
    postgres_admin_password: >-
      {{ (lookup('ansible.builtin.url', bv_url ~ '/api/v1/secrets/postgres/ADMIN_PASSWORD',
          headers={'Authorization': 'Bearer ' ~ lookup('ansible.builtin.env', 'BV_API_KEY')},
          split_lines=False) | from_json).value }}
  no_log: true
```

## Read a whole group

```yaml
- name: Read every secret of the memos group
  ansible.builtin.set_fact:
    memos: >-
      {{ lookup('ansible.builtin.url', bv_url ~ '/api/v1/groups/memos',
          headers={'Authorization': 'Bearer ' ~ lookup('ansible.builtin.env', 'BV_API_KEY')},
          split_lines=False) | from_json }}
  no_log: true

- name: Use a value
  ansible.builtin.debug:
    msg: "DSN has {{ memos.MEMOS_DSN | length }} characters"
```

## Write an `.env` file on a host

```yaml
- name: Render the app's .env from BastionVault
  ansible.builtin.copy:
    dest: /opt/affine/.env
    mode: "0600"
    content: >-
      {{ lookup('ansible.builtin.url', bv_url ~ '/api/v1/env/affine',
          headers={'Authorization': 'Bearer ' ~ lookup('ansible.builtin.env', 'BV_API_KEY')},
          split_lines=False) }}
  no_log: true
```

## Good practice

- Put `no_log: true` on every task that handles a value, so it does not appear in output or logs.
- Lookups run on the **control node**. The managed host never needs to reach BastionVault.
- If the CA for `homelab118.home` is not trusted on the control node, add `validate_certs=False` to the lookup, or better, install the CA.
- A `503` from the lookup means the vault is sealed. Unseal it and re-run.
