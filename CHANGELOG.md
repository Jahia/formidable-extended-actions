# Changelog

## 0.1.0 (unreleased)

First release of `formidable-extended-actions`: the form actions that no longer live in the Formidable core repository
(`Jahia/formidable`), gathered in one bundle. Node types, properties, PIDs, GraphQL fields and selector types are those of
the separate modules; only the module id, the resource bundle and the UI extension namespace changed.

### New features

* **Accounts** - Create account (`fmdbacc:createAccountAction`) and Sign in (`fmdbacc:signInAction`), with optional e-mail
  activation, the abuse limits of `org.jahia.modules.formidable.accounts.cfg`, the `allowedGroups` allow list and the
  `formidableAccountsActions` permission. From Jahia/formidable#406 (branch `feat/create-content-action`, the reviewed
  state).
* **Content** - Create content (`fmdbcnt:createContentAction`): a signed-in submitter's form becomes a content node of a chosen
  type with its uploaded files, written in a folder their group is entitled to, then published. From Jahia/formidable#415.
* **Salesforce** - Create Salesforce Lead (`fmdbsfdc:createLeadAction`), the `SalesforceLeadMapping` selector and the
  `formidableSalesforce` GraphQL query. From the CRM connector branch of Jahia/formidable
  (`feature/crm-connector-actions`, commit 336c045).
* **HubSpot** - Create HubSpot Contact (`fmdbhs:createContactAction`), `HubspotContactMapping`, `formidableHubspot`. Same origin.
* **Efficy** - Create Efficy Opportunity (`fmdbeff:createOpportunityAction`), `EfficyOpportunityMapping`, `formidableEfficy`.
  Same origin.

* **Content Editor layouts** for Create account, Sign in and Create content: what defines the action first (Content), the
  rest in an **Action settings** section (rank 1.07) of titled fieldsets: after sign-up or sign-in, password rule, e-mail
  activation, messages; files, who owns it, companion files, publication. `scripts/check-editor-forms.mjs`, run by the build,
  fails when a property of a laid-out action is not placed exactly once.
* **Create content mapping selector** (`ContentMapping`): one row per property of the chosen content type, then of the stored
  files, each filled from a form field, the files of an upload field, a fixed value, the submitter, their organization or the
  page the form was sent from; stored as one JSON document, `mapping`, which replaces the eight text settings `titleField`,
  `fieldMappings`, `fileMappings`, `fileProperties`, `fixedValues`, `submitterProperty`, `organizationProperty`,
  `pageProperty`. A field is remembered by node, field key and name: a renamed field keeps its row. The content type and the
  file mixin are chosen from the types of the site's modules (`formidableContentTypes`, `formidableContentFileMixins`).

### Changes against the separate modules

* One bundle, `formidable-extended-actions`, group `org.jahia.community.modules`: the `dev-unsigned` profile is gone.
* The accounts guard checks that the site enabled `formidable-extended-actions` (it checked `formidable-accounts`).
* One `definitions.cnd`, one UI extension (`src/javascript/init.tsx` registers the three CRM selector types) with the i18n
  namespace `formidable-extended-actions`, one module resource bundle for every editor label. The visitor sentences of the
  accounts and content families keep their own bundles (`formidable-extended-actions-accounts`,
  `formidable-extended-actions-content`): both define the same `visitor.*` keys.
* Compiled against Jahia 8.2.3.2 and `Jahia-Required-Version: 8.2.3.0` for the whole bundle (the sign-in needs it).
