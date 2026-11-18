# formidable-extended-actions

Custom form actions for [Formidable](https://github.com/Jahia/formidable), shipped **outside the Formidable core release** as
a monorepo of **six independent Jahia modules**, one per action family. A Jahia installs only the families it uses: no
Efficy module on a Jahia that has no Efficy. Five of the families used to be modules of the core repository (`Jahia/formidable`); the Redirect action was proposed there and ships here, the core keeping only the mechanism it uses.

| Module (id = artifactId) | Form action(s) | Node types | Java package | Needs | Guide |
|---|---|---|---|---|---|
| `formidable-accounts` | Create account, Sign in | `fmdbacc:createAccountAction`, `fmdbacc:signInAction` | `org.jahia.modules.formidable.accounts` | Jahia 8.2.3.0+, `formidable-engine=0.6` | [docs/accounts.md](docs/accounts.md) |
| `formidable-content` | Create content | `fmdbcnt:createContentAction` | `org.jahia.modules.formidable.content` | Jahia 8.2.2.0+, `formidable-engine=0.6` | [docs/content.md](docs/content.md) |
| `formidable-redirect` | Redirect | `fmdbred:redirectAction` | `org.jahia.modules.formidable.redirect` | Jahia 8.2.2.0+, `formidable-engine=0.6` | [docs/redirect.md](docs/redirect.md) |
| `formidable-salesforce` | Create Salesforce Lead | `fmdbsfdc:createLeadAction` | `org.jahia.modules.formidable.salesforce` | Jahia 8.2.2.0+, `formidable-engine`, `graphql-dxm-provider` | [docs/salesforce.md](docs/salesforce.md) |
| `formidable-hubspot` | Create HubSpot Contact | `fmdbhs:createContactAction` | `org.jahia.modules.formidable.hubspot` | same | [docs/hubspot.md](docs/hubspot.md) |
| `formidable-efficy` | Create Efficy Opportunity | `fmdbeff:createOpportunityAction` | `org.jahia.modules.formidable.efficy` | same | [docs/efficy.md](docs/efficy.md) |

The three CRM modules share one architecture (operator-managed factory connections, a field-mapping selector in Content
Editor, a GraphQL extension) but no code: [docs/crm-connectors.md](docs/crm-connectors.md). The index of all pages is
[docs/README.md](docs/README.md).

No module depends on another one, and none imports a package of another. Every node type, property name, OSGi PID, GraphQL
field and selector type is unchanged from the separate modules of the core repository, so a form that holds one of these
actions keeps working once the module replaces it.

## Requirements

- **Formidable core**: `formidable-engine` **0.6.0**, the released version (declared `formidable-engine=0.6` in `Jahia-Depends` by accounts
  and content) with the contextual-action extension point
  ([Jahia/formidable#406](https://github.com/Jahia/formidable/pull/406), being reduced to that extension point). The accounts
  actions need `formidable-extended-inputs` for the password field.
- **Jahia 8.2.3.0 or later for `formidable-accounts`** (`Jahia-Required-Version`): the Sign in action uses
  `AuthenticationService`, an OSGi service from that release. The other five modules are compiled against, and ask for,
  Jahia 8.2.2.0.
- `graphql-dxm-provider` for the three CRM modules (their selectors read the CRM metadata through GraphQL).
- Enable each module on every site that uses its action (jContent only offers content types of modules enabled on the site;
  the accounts actions also refuse a form whose site did not enable `formidable-accounts`).

## Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # JDK 17; Node 22 and Yarn 4 on the PATH (see mise.toml)
mvn clean install                                   # the six jars; add -o to work offline
mvn clean install -pl formidable-hubspot            # one module (its parent is resolved from the reactor)
```

`mvn clean install` checks the Content Editor layouts of every module (`scripts/check-editor-forms.mjs`, once, at the root),
then builds each module: its Java unit tests and, for the modules with a UI extension (content, salesforce, hubspot, efficy),
the front end (`yarn install`, `yarn lint`, then `yarn build`: `tsc --noEmit`, `vitest run`, `vite build`). Each jar is
`formidable-<name>/target/formidable-<name>-0.1.0-SNAPSHOT.jar`; the four UI modules carry `javascript/apps/remoteEntry.js`
and `javascript/apps/package.json`. Use Maven 3.8 (pinned to `maven = "3"` in `mise.toml`: 3.9 logs dependency-scan errors in
the Jahia plugin and may write a wrong `Import-Package`; check the manifest if you build with 3.9).

`formidable-engine:0.6.0` is resolved from Jahia's Maven repository; nothing has to be built first.

Front end only: `yarn install`, then `yarn lint`, `yarn test` or `yarn build` from the root (every workspace) or from a
module (never `yarn dev` from an agent). `yarn check:forms` runs the editor layout check.

The group id is `org.jahia.community.modules`: the jars carry no `Jahia-Signature`, which Jahia Enterprise requires of
`org.jahia.modules` bundles, so they start on a local Jahia Enterprise without a profile.

## Layout

```
pom.xml                 aggregator: shared versions (dependencyManagement) and plugin settings, <modules>
package.json, yarn.lock Yarn 4 workspaces (content, salesforce, hubspot, efficy), one lock file
eslint.config.shared.js the ESLint rule set every UI module's eslint.config.js reuses
scripts/check-editor-forms.mjs   every module's forms/*.json against that module's own definitions.cnd
formidable-<name>/
  pom.xml               artifactId = module id, packaging bundle, only the dependencies it uses
  src/main/java/org/jahia/modules/formidable/<name>/   the family's code (and src/test/java)
  src/main/resources/META-INF/definitions.cnd          its node types, its namespaces declared once
  src/main/resources/META-INF/configurations/          accounts only: the default configuration
  src/main/resources/META-INF/jahia-content-editor-forms/   its layouts (forms/) and selector bindings (fieldsets/)
  src/main/resources/resources/formidable-<name>{,_fr,_pt}.properties   labels, tooltips and visitor sentences
  src/main/resources/icons/                            one icon per node type
  UI modules only: package.json, vite.config.ts, tsconfig.json, eslint.config.js, src/javascript/init.tsx (registers
  only its selector types), src/main/resources/javascript/locales/{en,fr}.json (i18n namespace = the module id)
tests/                  Cypress specs of the families (reference, see tests/README.md)
docs/                   one page per family, docs/README.md is the index
```

## Configuration

Only `formidable-accounts` ships a default configuration file
(`META-INF/configurations/org.jahia.modules.formidable.accounts.cfg`, first line `# default configuration`: Jahia keeps the
operator's copy on a redeploy). The CRM connections are factory configurations written by the operator
(`org.jahia.modules.formidable.salesforce-<id>.cfg`, `...hubspot-<id>.cfg`, `...efficy-<id>.cfg`): nothing is bundled, a
credential never enters the repository.

## Status

0.1.0, unreleased. See [CHANGELOG.md](CHANGELOG.md). The Cypress specs under `tests/` are not wired to a runner yet (they rely
on the Formidable test infrastructure): [tests/README.md](tests/README.md).
