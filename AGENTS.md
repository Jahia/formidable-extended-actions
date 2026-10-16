# AGENTS.md - formidable-extended-actions

One Maven module, one OSGi bundle (`formidable-extended-actions`, group `org.jahia.community.modules`) that holds five
families of custom [Formidable](https://github.com/Jahia/formidable) form actions: accounts (create account, sign in),
content (create content), and the Salesforce, HubSpot and Efficy CRM connectors. The families used to be modules of the
core repository; they are shipped here outside the core release. What each one does: [README.md](README.md) and
[docs/](docs/README.md).

## Rules

The AIStartupKit harness applies in full: start from `/Users/stephane/Runtimes/0.Modules/AIStartupKit/CLAUDE.md` and its
skills (`jahia-osgi-module`, `jahia-dev-osgi-module`, `jahia-osgi-ui-extension`, `jahia-java-osgi`, `jahia-java-jcr`,
`jahia-java-security`, `jahia-review-java`). The ones that bite here:

- React 18, Vite federation (`@jahia/vite-federation-plugin`), Moonstone 2.16.2, `@apollo/client` pinned to the host's
  3.14.0. Never `yarn dev` from an agent.
- A `.cfg` bundled in `META-INF/configurations/` starts with `# default configuration`, or it overwrites the operator's copy on
  every deploy. The CRM connections are factory configurations: never bundle one, never write a credential in the repository.
- Every action that changes content has a CSRF decision; none of these actions is whitelisted (they ride the Formidable submit
  endpoint). The accounts servlet `/modules/formidable-accounts/verify` only follows a single-use token.
- Node types, property names, PIDs, GraphQL type and field names and selector type names are public: never rename one.
- Each namespace is declared once, at the top of `src/main/resources/META-INF/definitions.cnd`; a family's block adds types
  only. Every type and property has a label and a `ui.tooltip` in `src/main/resources/resources/formidable-extended-actions*.properties`.
- Java packages stay one per family (`org.jahia.modules.formidable.<family>`); nothing is exported.

## Commands

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn clean install                  # unit tests, then yarn install + lint + build (tsc, vitest, vite), then the jar
mvn -o clean install               # offline, when ~/.m2 and the yarn cache are warm
mvn test                           # Java tests only run in the test phase, after the front-end steps; add -Dexec.skip=true to skip those
yarn lint && yarn build            # front-end only (Node 22, Yarn 4 through .yarnrc.yml)
```

`formidable-engine:0.6.0-SNAPSHOT` must be in `~/.m2` (build it in a Formidable checkout). Do not deploy to a shared Jahia
without the owner's say-so.

## Layout

See the tree in [README.md](README.md#layout). The Cypress specs in `tests/` are reference material copied from the
Formidable repository; they need its test infrastructure ([tests/README.md](tests/README.md)).

## Verification

A change is done when `mvn clean install` passes, the jar lists `META-INF/definitions.cnd`, `META-INF/configurations/`,
`META-INF/import.zip`, `javascript/apps/remoteEntry.js` and `javascript/apps/package.json`, and the manifest has
`Jahia-Depends: default,formidable-engine=0.6,graphql-dxm-provider` and `Jahia-Required-Version: 8.2.3.0`.
