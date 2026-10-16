# Cypress specs (reference material, not wired yet)

These specs were written in the Formidable repository (`Jahia/formidable`, `tests/`) for the actions that now live in this
bundle. They are copied here unchanged as the reference of what each action must do end to end. **They do not run from this
repository yet**: they import the Formidable test infrastructure (`@jahia/cypress`, the `useFormidableSite` helper, the
`support/fixtures` builders of forms, fields and publication, the `e2e/security/support` helpers, the `cypress.config`
and the Jahia test stack with `formidable-engine`, `formidable-elements` and `formidable-extended-inputs` deployed).

**Follow-up (not done): wire them to a Cypress setup of this repository** (or to a checkout of the Formidable test
module): a `package.json` test script, the `cypress.config`, the stack provisioning that also deploys this bundle, and the
missing shared support files listed below, then run `yarn lint` over `tests/`.

| Family | Specs | Own support files copied here | Support files still in the Formidable repository |
|---|---|---|---|
| Accounts | `e2e/actions/86-accounts-create-account.cy.ts`, `87-accounts-sign-in.cy.ts`, `88-accounts-password-never-stored.cy.ts` | `e2e/support/accounts.ts`, the account builders of `support/fixtures/extendedActions.ts` | `e2e/support/useFormidableSite.ts`, `e2e/security/support.ts`, `support/fixtures/*` (forms, fields, publication, site), `support/commands.ts` |
| Content | `e2e/actions/90-content-create-content.cy.ts` | the content builder of `support/fixtures/extendedActions.ts` | same |
| Salesforce | `e2e/actions/80-salesforce-lead-action.cy.ts`, `81-salesforce-lead-mapping-selector.cy.ts` | `support/fixtures/salesforce.ts`, `page-object/Salesforce*.ts`, `salesforce-mock/` | same |
| HubSpot | `e2e/actions/82-hubspot-contact-action.cy.ts`, `83-hubspot-contact-mapping-selector.cy.ts` | `support/fixtures/hubspot.ts`, `page-object/Hubspot*.ts`, `hubspot-mock/` | same |
| Efficy | `e2e/actions/84-efficy-opportunity-action.cy.ts`, `85-efficy-opportunity-mapping-selector.cy.ts` | `support/fixtures/efficy.ts`, `page-object/Efficy*.ts`, `efficy-mock/` | same |
| CRM (three families) | `e2e/actions/86-crm-graphql-gate.cy.ts` | - | same |

Notes:

- The spec numbers are those of the Formidable repository; `86-accounts-*` and `86-crm-graphql-gate` both start with 86 there too.
- The module is `formidable-extended-actions` now. In the Formidable repository, `tests/cypress/support/constants.ts` lists the
  modules enabled on the test site (`formidable-accounts`, `formidable-content`, `formidable-salesforce`, `formidable-hubspot`,
  `formidable-efficy`) and `tests/provisioning-manifest-snapshot.yml` lists their `mvn:` coordinates: both must name
  `formidable-extended-actions` (`mvn:org.jahia.community.modules/formidable-extended-actions`) once the five modules are gone from
  that repository. The accounts specs include the "module not enabled on the site" refusal.
- The CRM specs skip themselves unless the matching mock server (`tests/*-mock/`, Python) is running and a `mock` connection
  is declared; see the README of each mock.
- The fixtures reference the node types by name (`fmdbacc:createAccountAction`, ...): unchanged.
