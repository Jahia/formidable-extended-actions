# Changelog

## 0.1.0 (unreleased)

First release of `formidable-extended-actions`: the form actions that no longer live in the Formidable core repository
(`Jahia/formidable`), gathered in one repository as six independent modules, one per action family. Node types, properties,
PIDs, GraphQL fields, selector types and module ids are those of the separate modules.

### New features

* **Accounts** - Create account (`fmdbacc:createAccountAction`) and Sign in (`fmdbacc:signInAction`), with optional e-mail
  activation, the abuse limits of `org.jahia.modules.formidable.accounts.cfg`, the `allowedGroups` allow list and the
  `formidableAccountsActions` permission. From Jahia/formidable#406 (branch `feat/create-content-action`, the reviewed
  state).
* **Content** - Create content (`fmdbcnt:createContentAction`): a signed-in submitter's form becomes a content node of a chosen
  type with its uploaded files, written in a folder their group is entitled to, then published. From Jahia/formidable#415.
* **Redirect** - Redirect (`fmdbred:redirectAction`): once the submission is accepted, sends the visitor to a page of the site
  (a weak reference, its URL built in the visitor's language) or a same-origin path; never another host. Proposed in
  Jahia/formidable#432 as `fmdb:redirectAction`, which keeps only the mechanism (`FormActionResult.redirect`, `RedirectTargets`,
  `PageLinks`, the island's navigation).
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

* **Content stories** - the Create content mapping fills a full editorial story (`docs/content.md`, "Stories"):
  * **Categories**: a **Categories** row (`j:defaultCategory`, listed for every content type; the action adds
    `jmix:categorized`) filled from a choice field in category mode (`fmdbmix:categoryOptions`: the submitted node names are
    looked up among the direct `jnt:category` children of the field's root, read as the system user in live; an unknown name
    refuses the submission, a category is never created) and/or from the page's own categories (`source: "pageCategories"`,
    copied as they are); several rows are united.
  * **Rich text**: a form field that fills a rich-text property (`richtext` selector) is converted from plain text to escaped
    HTML (`<p>` per blank-line paragraph, `<br>` for a single break).
  * **The submitter's profile**: the mixin `fmdbcntmix:submitterProfile` (`submitterUser`, addable to any content in the Content
    Editor; labels and tooltips in English, French and Portuguese) and the row source `submitterProfile`, which references the
    submitter's profile content (a bound JCR-SQL2 query under `/sites/<site of the content folder>`, node type constraints
    applied; none found writes nothing; several for a single property write none).
  * **A single image**: a file stored for a single weak reference with a node type constraint (`image < jmix:image`) is checked
    against it before the write (a PDF is refused with the invalid-value sentence instead of failing the save); every `image/*`
    file is stored as a `jmix:image`, also WebP and SVG, which the JDK cannot size.
  * The selector offers the Categories row, the two category sources and the profile source, and its labels are shipped in
    English, French and Portuguese (`locales/pt.json` is new).

### Changes against the separate modules

* **Six independent modules**, not one bundle: `formidable-accounts`, `formidable-content`, `formidable-redirect`, `formidable-salesforce`,
  `formidable-hubspot`, `formidable-efficy` (group `org.jahia.community.modules`; the `dev-unsigned` profile is gone). A Jahia
  installs only the families it uses. The root `formidable-extended-actions` is the aggregator pom and the Yarn workspace root;
  no jar of that name is built.
* Module ids, resource bundles (`resources/<module id>{,_fr,_pt}.properties`, labels and visitor sentences together) and UI
  extension i18n namespaces are those of the separate modules: the accounts guard checks that the site enabled
  `formidable-accounts`, the selectors read the namespaces `formidable-salesforce`, `formidable-hubspot`, `formidable-efficy`
  and `formidable-content`.
* One `definitions.cnd` per module (its namespaces declared once, only its types) and one UI extension per module that has a
  selector (content, salesforce, hubspot, efficy), each registering only its own selector types.
* `Jahia-Required-Version` is per module: 8.2.3.0 for `formidable-accounts` (the sign-in needs `AuthenticationService`, so
  it is compiled against Jahia 8.2.3.2), 8.2.2.0 for the others. `Jahia-Depends` is what each family needs: `graphql-dxm-provider`
  only on the three CRMs.
* `scripts/check-editor-forms.mjs` checks the forms of every module against that module's own `definitions.cnd`.
* The UI extensions are built with the versions the Formidable core resolves (`@jahia/vite-federation-plugin` 0.1.1,
  `@jahia/ui-extender` 1.2.0, `graphql` 16.13.2, `@module-federation/vite` 1.12.3), pinned exactly; `@apollo/client` stays
  at the host's 3.14.0. A lock resolved from scratch (plugin 0.1.3, ui-extender 1.3.0) broke jContent on a real Jahia.
