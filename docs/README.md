# Documentation of formidable-extended-actions

One page per family (one module each). The repository (requirements, build, layout) is described in the [README](../README.md); release notes
are in the [CHANGELOG](../CHANGELOG.md). Generic Formidable documentation (form pipeline, sensitive fields, error codes, the
form action SPI) stays in the [Formidable repository](https://github.com/Jahia/formidable/tree/main/docs).

| Page | For | What it covers |
|---|---|---|
| [accounts.md](accounts.md) | administrators, authors | Create account and Sign in: the configuration file, the permission `formidableAccountsActions`, the sign-up and sign-in forms, e-mail activation, what is and is not protected, troubleshooting |
| [content.md](content.md) | administrators, authors | Create content: the folder and group model, the mapping (a selector, one row per property), files, the security model, a worked example, troubleshooting, the Java layout |
| [crm-connectors.md](crm-connectors.md) | everyone | What the three CRM families share (connections as operator configuration, the mapping selector, the GraphQL extension, error policy, security) and how they differ |
| [salesforce.md](salesforce.md) | administrators, authors, developers | Create Salesforce Lead: Connected App and JWT setup, the connection file, authoring, value conversion, errors, security, troubleshooting |
| [hubspot.md](hubspot.md) | administrators, authors, developers | Create HubSpot Contact: private app token, the connection file, authoring, errors, security, troubleshooting |
| [efficy.md](efficy.md) | administrators, authors, developers | Create Efficy Opportunity: the tenant token, the field catalog, person lookup, authoring, errors, security, troubleshooting |

Test material (Cypress specs and mock CRM servers, not wired yet): [../tests/README.md](../tests/README.md).

## Module ids, resource bundles, namespaces

Each family is a module of its own: enable on a site only the ones it uses.

| Family | Module id (enable it on the site) | Resource bundle (editor labels, tooltips, visitor sentences) | UI extension i18n namespace and keys |
|---|---|---|---|
| Accounts | `formidable-accounts` | `formidable-accounts{,_fr,_pt}.properties` | no UI extension |
| Content | `formidable-content` | `formidable-content{,_fr,_pt}.properties` | `formidable-content`: `contentMapping` |
| Salesforce | `formidable-salesforce` | `formidable-salesforce{,_fr}.properties` | `formidable-salesforce`: `leadMapping` |
| HubSpot | `formidable-hubspot` | `formidable-hubspot{,_fr}.properties` | `formidable-hubspot`: `contactMapping` |
| Efficy | `formidable-efficy` | `formidable-efficy{,_fr}.properties` | `formidable-efficy`: `opportunityMapping` |

Bundles are under `formidable-<name>/src/main/resources/resources/`, locale files (`en.json`, `fr.json`) under
`formidable-<name>/src/main/resources/javascript/locales/`.

| What | Value |
|---|---|
| Accounts activation link | `/modules/formidable-accounts/verify?t=...` |
| OSGi PIDs | `org.jahia.modules.formidable.accounts`, `...salesforce-<id>`, `...hubspot-<id>`, `...efficy-<id>` |
