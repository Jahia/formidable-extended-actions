# Documentation of formidable-extended-actions

One page per family. The bundle itself (requirements, build, layout) is described in the [README](../README.md); release notes
are in the [CHANGELOG](../CHANGELOG.md). Generic Formidable documentation (form pipeline, sensitive fields, error codes, the
form action SPI) stays in the [Formidable repository](https://github.com/Jahia/formidable/tree/main/docs).

| Page | For | What it covers |
|---|---|---|
| [accounts.md](accounts.md) | administrators, authors | Create account and Sign in: the configuration file, the permission `formidableAccountsActions`, the sign-up and sign-in forms, e-mail activation, what is and is not protected, troubleshooting |
| [content.md](content.md) | administrators, authors | Create content: the folder and group model, the mapping lines, files, the security model, a worked example, troubleshooting, the Java layout |
| [crm-connectors.md](crm-connectors.md) | everyone | What the three CRM families share (connections as operator configuration, the mapping selector, the GraphQL extension, error policy, security) and how they differ |
| [salesforce.md](salesforce.md) | administrators, authors, developers | Create Salesforce Lead: Connected App and JWT setup, the connection file, authoring, value conversion, errors, security, troubleshooting |
| [hubspot.md](hubspot.md) | administrators, authors, developers | Create HubSpot Contact: private app token, the connection file, authoring, errors, security, troubleshooting |
| [efficy.md](efficy.md) | administrators, authors, developers | Create Efficy Opportunity: the tenant token, the field catalog, person lookup, authoring, errors, security, troubleshooting |

Test material (Cypress specs and mock CRM servers, not wired yet): [../tests/README.md](../tests/README.md).

## Module id, resource bundles, namespace

| What | Value |
|---|---|
| Module id (enable it on the site) | `formidable-extended-actions` |
| Editor labels and tooltips (CND) | `src/main/resources/resources/formidable-extended-actions{,_fr,_pt}.properties` |
| What a visitor reads, accounts / content | `formidable-extended-actions-accounts*.properties`, `formidable-extended-actions-content*.properties` |
| UI extension i18n namespace | `formidable-extended-actions` (`javascript/locales/{en,fr}.json`: `leadMapping`, `contactMapping`, `opportunityMapping`) |
| Accounts activation link | `/modules/formidable-accounts/verify?t=...` (the servlet alias is unchanged from the separate module) |
| OSGi PIDs | unchanged: `org.jahia.modules.formidable.accounts`, `...salesforce-<id>`, `...hubspot-<id>`, `...efficy-<id>` |
