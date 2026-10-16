# AGENTS.md - formidable-extended-actions

A monorepo of five independent OSGi modules (group `org.jahia.community.modules`), one per family of custom
[Formidable](https://github.com/Jahia/formidable) form actions: `formidable-accounts` (create account, sign in),
`formidable-content` (create content), `formidable-salesforce`, `formidable-hubspot` and `formidable-efficy` (CRM
connectors). The families used to be modules of the core repository; they are shipped here outside the core release, and a
Jahia installs only the ones it uses. What each one does: [README.md](README.md) and [docs/](docs/README.md).

## Rules

The AIStartupKit harness applies in full: start from `/Users/stephane/Runtimes/0.Modules/AIStartupKit/CLAUDE.md` and its
skills (`jahia-osgi-module`, `jahia-dev-osgi-module`, `jahia-osgi-ui-extension`, `jahia-java-osgi`, `jahia-java-jcr`,
`jahia-java-security`, `jahia-review-java`). The ones that bite here:

- **Modules stay independent.** A module never imports a package of another one, never lists another one in `Jahia-Depends`,
  and carries its own `definitions.cnd`, resource bundle (`resources/<module id>{,_fr,_pt}.properties`, named after the
  module: Jahia resolves the CND labels from it), configurations, Content Editor overrides and, if it has one, UI extension.
  Shared code is not an option: copy, or put it in the Formidable engine.
- The module id (`Bundle-SymbolicName`) is the artifactId, the i18n namespace of the UI extension and, for accounts, the module a
  site must have enabled (`AccountsGuard.MODULE_ID`). Keep the three equal.
- React 18, Vite federation (`@jahia/vite-federation-plugin`), Moonstone 2.16.2, `@apollo/client` pinned to the host's
  3.14.0. Never `yarn dev` from an agent.
- A `.cfg` bundled in `META-INF/configurations/` starts with `# default configuration`, or it overwrites the operator's copy on
  every deploy. The CRM connections are factory configurations: never bundle one, never write a credential in the repository.
- Every action that changes content has a CSRF decision; none of these actions is whitelisted (they ride the Formidable submit
  endpoint). The accounts servlet `/modules/formidable-accounts/verify` only follows a single-use token.
- Node types, property names, PIDs, GraphQL type and field names and selector type names are public: never rename one.
- Each namespace is declared once, at the top of the module's `src/main/resources/META-INF/definitions.cnd`; no other `.cnd`
  of the module declares one. Every type and property has a label and a `ui.tooltip` in the module's bundle, and the
  `fmdb.section.actionSettings` label has the same wording in the accounts and content bundles.
- A new Content Editor layout (`forms/*.json` with the `actionSettings` section) places every property of its node type once:
  `node scripts/check-editor-forms.mjs` (run by the build) fails otherwise.
- Java packages stay one per family (`org.jahia.modules.formidable.<family>`); nothing is exported.
- `Jahia-Required-Version` is as low as the code allows (a module's `jahia.server.version` property is the Jahia API it
  compiles against): 8.2.3.0 for accounts only (`AuthenticationService`), 8.2.2.0 elsewhere. Do not raise it for a module
  that does not need the newer API.

## Commands

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean install                       # check forms, then per module: unit tests, yarn install + lint + build, the jar
mvn -o clean install                    # offline, when ~/.m2 and the yarn cache are warm
mvn -o clean install -pl formidable-hubspot   # one module
mvn -o test -Dexec.skip=true            # Java unit tests only (-Dexec.skip=true skips the form check and the yarn steps)
yarn lint && yarn test                  # every UI module (Node 22, Yarn 4 through .yarnrc.yml); from a module: that one
node scripts/check-editor-forms.mjs [formidable-accounts]    # the editor layout check, all modules or one
```

`formidable-engine:0.6.0-SNAPSHOT` must be in `~/.m2` (build it in a Formidable checkout). Do not deploy to a shared Jahia
without the owner's say-so.

## Layout

See the tree in [README.md](README.md#layout). The Cypress specs in `tests/` are reference material copied from the
Formidable repository; they need its test infrastructure ([tests/README.md](tests/README.md)).

## Verification

A change is done when `mvn clean install` passes at the root and each jar holds its own family only: its `definitions.cnd`,
bundles and, where it has them, `META-INF/configurations/`, `META-INF/import.zip` (accounts), `javascript/apps/remoteEntry.js`
and `javascript/apps/package.json` (content and the CRMs). The manifests say:

| Module | `Jahia-Depends` | `Jahia-Required-Version` |
|---|---|---|
| `formidable-accounts` | `default,formidable-engine=0.6` | 8.2.3.0 |
| `formidable-content` | `default,formidable-engine=0.6` | 8.2.2.0 |
| `formidable-salesforce`, `-hubspot`, `-efficy` | `default,formidable-engine,graphql-dxm-provider` | 8.2.2.0 |
