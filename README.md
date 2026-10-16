# formidable-extended-actions

Custom form actions for [Formidable](https://github.com/Jahia/formidable), shipped as **one OSGi bundle outside the
Formidable core release**. Five families that used to be modules of the core repository (`Jahia/formidable`) live here,
each in its own Java packages, with one CND file, one resource bundle and one UI extension for the lot.

| Family | Form action(s) | Node types | Java package | Guide |
|---|---|---|---|---|
| Accounts | Create account, Sign in | `fmdbacc:createAccountAction`, `fmdbacc:signInAction` | `org.jahia.modules.formidable.accounts` | [docs/accounts.md](docs/accounts.md) |
| Content | Create content | `fmdbcnt:createContentAction` | `org.jahia.modules.formidable.content` | [docs/content.md](docs/content.md) |
| Salesforce | Create Salesforce Lead | `fmdbsfdc:createLeadAction` | `org.jahia.modules.formidable.salesforce` | [docs/salesforce.md](docs/salesforce.md) |
| HubSpot | Create HubSpot Contact | `fmdbhs:createContactAction` | `org.jahia.modules.formidable.hubspot` | [docs/hubspot.md](docs/hubspot.md) |
| Efficy | Create Efficy Opportunity | `fmdbeff:createOpportunityAction` | `org.jahia.modules.formidable.efficy` | [docs/efficy.md](docs/efficy.md) |

The three CRM families share one architecture (operator-managed factory connections, a field-mapping selector in Content
Editor, a GraphQL extension): [docs/crm-connectors.md](docs/crm-connectors.md). The index of all pages is
[docs/README.md](docs/README.md).

Every node type, property name, OSGi PID, GraphQL field and selector type is unchanged from the separate modules, so a form
that holds one of these actions keeps working once this bundle replaces them.

## Requirements

- **Formidable core**: `formidable-engine` **0.6.0-SNAPSHOT** (declared `formidable-engine=0.6` in `Jahia-Depends`) with the
  contextual-action extension point ([Jahia/formidable#406](https://github.com/Jahia/formidable/pull/406), being reduced to
  that extension point). The accounts actions need `formidable-extended-inputs` for the password field.
- **Jahia 8.2.3.0 or later** (`Jahia-Required-Version`): the Sign in action uses `AuthenticationService`, an OSGi service from
  that release.
- `graphql-dxm-provider` (the CRM selectors read the CRM metadata through GraphQL).
- Enable the module `formidable-extended-actions` on every site that uses one of the actions (jContent only offers content
  types of modules enabled on the site; the accounts actions also refuse a form whose site did not enable it).

## Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # JDK 17; Node 22 and Yarn 4 on the PATH (see mise.toml)
mvn clean install                                   # add -Denforcer.skip=true if the old enforcer of the Jahia parent
                                                    # rejects the JDK version string; add -o to work offline
```

`mvn clean install` runs the Java unit tests, then the front-end (`yarn install`, `yarn lint`, then `yarn build`: `tsc --noEmit`,
`vitest run`, `vite build`), and produces `target/formidable-extended-actions-0.1.0-SNAPSHOT.jar` with
`javascript/apps/remoteEntry.js` and `javascript/apps/package.json` inside. Use Maven 3.8 (pinned to `maven = "3"` in
`mise.toml`: 3.9 logs dependency-scan errors in the Jahia plugin and may write a wrong `Import-Package`; check the manifest
if you build with 3.9).

`formidable-engine:0.6.0-SNAPSHOT` must be in the local repository: build it in a Formidable checkout
(`mvn -o -q install -DskipTests -pl formidable-engine`).

Front-end only: `yarn install && yarn lint && yarn build` (never `yarn dev` from an agent).

The group id is `org.jahia.community.modules`: the jar carries no `Jahia-Signature`, which Jahia Enterprise requires of
`org.jahia.modules` bundles, so it starts on a local Jahia Enterprise without a profile.

## Layout

```
pom.xml, package.json, vite.config.ts, tsconfig.json, eslint.config.js, yarn.lock
src/main/java/org/jahia/modules/formidable/{accounts,content,salesforce,hubspot,efficy}/
src/main/resources/META-INF/definitions.cnd               every family's node types, namespaces declared once
src/main/resources/META-INF/configurations/               default configuration of the accounts family
src/main/resources/META-INF/jahia-content-editor-forms/   fieldsets that attach the mapping selectors
src/main/resources/resources/                             the module resource bundle + the visitor-text bundles
src/main/resources/javascript/locales/                    en.json, fr.json of the UI extension
src/main/import/permissions.xml                           formidableAccountsActions (under site-admin)
src/javascript/                                           ONE Vite federation app (the three CRM mapping selectors)
src/test/java/                                            JUnit 5 tests of every family
tests/                                                    Cypress specs of the families (reference, see tests/README.md)
docs/                                                     one page per family, docs/README.md is the index
```

## Configuration

Only the accounts family ships a default configuration file
(`META-INF/configurations/org.jahia.modules.formidable.accounts.cfg`, first line `# default configuration`: Jahia keeps the
operator's copy on a redeploy). The CRM connections are factory configurations written by the operator
(`org.jahia.modules.formidable.salesforce-<id>.cfg`, `...hubspot-<id>.cfg`, `...efficy-<id>.cfg`): nothing is bundled, a
credential never enters the repository.

## Status

0.1.0, unreleased. See [CHANGELOG.md](CHANGELOG.md). The Cypress specs under `tests/` are not wired to a runner yet (they rely
on the Formidable test infrastructure): [tests/README.md](tests/README.md).
