# Send the visitor elsewhere after a submission (module `formidable-redirect`)

The module `formidable-redirect` adds one form action: **Redirect** (`fmdbred:redirectAction`). Once the submission is
accepted, the visitor's browser goes to another page of the site: a thank-you page, the list of what they sent, the next
step of a process. The form's submission message shows while the browser navigates.

Requirements: Jahia **8.2.2.0 or later** and `formidable-engine` 0.6. No configuration file, no endpoint of its own: the
engine names the target in its answer to the form and the form island navigates (the mechanism is described in
`docs/architecture/redirect-action.md` of the [Formidable repository](https://github.com/Jahia/formidable)). Java
package: `org.jahia.modules.formidable.redirect`. Bundle overview: [README](../README.md).

## Configure the action (author)

1. Enable `formidable-redirect` on the site (jContent only offers the types of modules enabled on the site).
2. In the form's actions, add **Redirect**, usually last: the actions before it (Save to JCR, Create content, an e-mail)
   still run, and the visitor leaves only once all of them accepted the submission.
3. Choose the **Destination**:

| Destination | Field | What is stored |
|---|---|---|
| **A page of the site** (default) | **Page**, picked in the page picker | A reference to the page: it follows the page when it is moved or renamed. Its URL is built in the visitor's language, vanity URLs included. The page must be published. |
| **A path of the site** | **Path**, typed in, per language | A path starting with a single slash (`/sites/mysite/thank-you.html`, or a vanity URL such as `/thank-you`). Useful for a page with parameters or a page of the site that is not in the picker. |

4. Publish the form.

## What is refused

The target is always a path of the server the visitor is on. The action and the engine both refuse, and the visitor stays
on the form page with the submission message:

- another website (`https://...`, `//host/...`), a scheme (`javascript:`), a backslash, a space or a control character in
  a path;
- a page that is not published, or was deleted;
- a path longer than 2048 characters.

The answers of the form are never put in the URL (they would end up in browser history, server logs and referers). A
contributor who needs to send visitors to another website links to it from the submission message.

## Behaviour

- **One redirect per form.** With two Redirect actions, or a Redirect action after a sign-in action that also redirects, the
  first one in the list wins.
- **Only on success.** When an action fails or the engine rejects the submission, there is no redirect: the visitor sees the
  error on the form.
- **Read-only compatible.** The action writes nothing, so it runs on read-only forms too.
- **No redirect in edit mode or preview**, where submission is disabled anyway.
- The *back* button returns to the form page.

## Troubleshooting

| Symptom | Check |
|---|---|
| **Redirect** is not offered in the form's actions | `formidable-redirect` is installed, started and enabled on the site |
| The message shows, the visitor stays on the form | The page is published; the path starts with one slash and names no host. The log says why: `[RedirectFormAction]` or `[PageLinks]` warnings |
| The visitor lands on the wrong language | A path is per language: fill it in each language, or pick a page instead |

## Java layout

`RedirectFormAction` is the `ContextualFormAction` of `fmdbred:redirectAction`: it returns `FormActionResult.redirect(path)`,
built with `PageLinks.pathOf` for a page and checked with `RedirectTargets.sameOriginPath` for a path, or
`FormActionResult.none()` (never an exception) when the target does not resolve. Unit tests: `RedirectFormActionTest`,
`ResourceBundlesTest`. End to end (reference material, not wired): `tests/cypress/e2e/actions/89-redirect-action.cy.ts`.
