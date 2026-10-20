# Create content from a form (module `formidable-content`)

The module `formidable-content` adds one form action: **Create content** (`fmdbcnt:createContentAction`). A **signed-in** user
fills in a Formidable form; the action turns the submission into a content node of a type you choose (the form's fields fill its
properties), stores the uploaded files as files that the content references, and publishes both, in a folder that **the user's
own group** is entitled to.

It is meant for the extranet case: suppliers, partners or members who send briefs, proposals, photos or videos, each into
the folder of their own company, without ever being given write access to the repository.

Requirements: Jahia **8.2.2.0 or later** and `formidable-engine` 0.6. Nothing else: no configuration file, no CSRF entry, no
endpoint of its own (the form is submitted through the engine's endpoint). Java package: `org.jahia.modules.formidable.content` (layout at the end of this page). Bundle overview: [README](../README.md).

## Contents

- [Before you start (administrator)](#before-you-start-administrator)
- [Configure the action (author)](#configure-the-action-author)
- [The mapping](#the-mapping)
- [Files](#files)
- [Stories: categories, rich text, the submitter's profile](#stories-categories-rich-text-the-submitters-profile)
- [The security model](#the-security-model)
- [A worked example](#a-worked-example)
- [Troubleshooting](#troubleshooting)

## Before you start (administrator)

1. Deploy the engine, then `formidable-content` (nothing of the other action modules is needed); enable `formidable-content` on the site.
2. Create the **folders**. With the group mechanism below, that is one folder tree for the content and one for the files, and
   **one subfolder per company** in each. Create the subfolders yourself, with their ACLs: the action never creates a folder,
   because their access rights are your decision, not the form's.
3. For each company's subfolder, **break the ACL inheritance** and grant the company's group the `reader` role (or any role
   that includes read in live). That ACL is what the action relies on (see the security model): the group name only *names* a
   folder, the folder's rights decide who may use it.
4. **Publish** the folders: the action reads them in the live workspace.
5. Create the groups, one per company, named with a common prefix (`orvane-supplier-northlight`, `orvane-supplier-harbor`), and put
   each user in their company's group. A user in **two** of those groups cannot send content (the action will not guess which
   company they mean).

## Configure the action (author)

Add a **Create content** action to the form. Turn on the form's **Require authentication** option too: the engine then rejects
an anonymous submission (FMDB-009) before any action runs. The action refuses guests anyway, so the option is a convenience, not
the protection.

The editor shows what defines the action first (**Content**), the rest under **Action settings**:

| Where | Setting | What it does |
|---|---|---|
| Content | **Content type** | The type to create, chosen among the content types of the modules installed on the site. It must be allowed in the target folder; checked at every submission |
| Content | **Content folder** | Where the content goes. With a group prefix, the folder that holds one subfolder per group |
| Content | **What fills the content** | The mapping: one row per property of the type, then one per property of the stored files (see [The mapping](#the-mapping)) |
| Action settings, Files | **Files folder** | Where uploaded files go (same subfolder layout). Required as soon as files are mapped |
| Action settings, Files | **File mixin** | Optional. A mixin added to every stored file (`ctmdmix:credited` for a credit); the mapping then lists its properties under the files |
| Action settings, Who owns it | **Group prefix** | Optional. The submitter's groups are searched for the one whose name starts with it; the rest of the name is the subfolder, in both folders. None, several or a missing subfolder refuse the submission |
| Action settings, Companion files | **Required companion files**, **Message for a missing companion file** | `dependentField<-field:mimePattern` rules and the sentence the visitor reads when one is broken |
| Action settings, Publication | **Publish** | On by default: the content and its files are published in the request, in every language of the site |

Publish the form. Submissions run against the live workspace. The action does **not** carry the read-only compatibility marker:
it writes, so the form is unavailable during a read-only maintenance window.

## The mapping

Choose the content type first: **What fills the content** lists its properties (the title first, then the type's own, as its
definition declares them; protected and hidden ones never), each with a **Filled from** choice among what the property can take:

| Source | For | What is written |
|---|---|---|
| **A form field** | text, number, boolean, date properties (single or multiple); the title | The field's value, converted to the property's type (below). The title also names the node |
| **The files of an upload field** | `reference` / `weakreference` properties | The files, stored and referenced (see [Files](#files)) |
| **A fixed value** | text, number, boolean, date properties | The value typed in the mapping; one per line for a multiple property |
| **The person who submits** | a single text property | Their user name |
| **Their organization** | a single text property | The `j:organization` of their account |
| **The page the form was sent from** | a single reference property | A reference to that page (read from the request, checked: a page or main resource of the site the submitter can read) |
| **The submitter's profile** | a reference or weakreference property, single or multiple | References to the content that carries the submitter's profile (see [Stories](#stories-categories-rich-text-the-submitters-profile)) |
| **Categories** (the **Categories** row, listed last for every content type) | `j:defaultCategory` | The categories of a choice field in category mode, or the categories of the page the form was sent from (see [Stories](#stories-categories-rich-text-the-submitters-profile)) |

Below, **The stored files** lists the properties of `jnt:file` and of the file mixin, each filled from a form field (the credit
line written on every file, for instance). A property marked `*` is mandatory: the editor warns while it is not mapped.

The mapping is stored as one JSON document (`mapping`); nobody types it. A form field is remembered by its node, its field key
and its name, so **renaming a field keeps the mapping**; **deleting one** makes the editor say which row lost it, and the action
refuse every submission (the visitor reads "temporarily unavailable", the log names the row) until another field is chosen.
Rows whose property the chosen types no longer have are listed apart, to be removed.

A **form field** is converted to the type the property's definition declares:

| Property type | What is accepted |
|---|---|
| string | Any text |
| long | A whole number (`42`, `-7`), nothing else: no `4.2`, no `0x10`, no `1e3` |
| double, decimal | A plain decimal literal (`1250.5`, `1e3`); no `NaN`, no `Infinity`, no `1d` |
| boolean | `true`, `false`, `on`, `off`, `yes`, `no`, `1`, `0` |
| date | `yyyy-MM-dd` (what a date field sends), stored as the start of that day in UTC |
| multiple property | Every value of the field (a checkbox group); a single property takes the first |

A form field that fills a **rich-text** property (selector `richtext`) is converted to safe HTML, see [Stories](#stories-categories-rich-text-the-submitters-profile).

A field left blank writes nothing. A value that does not fit refuses the submission with "one of the values you entered is
not valid" (the log says which field, never what was typed). A property that the type does not declare, a protected one, one of
the platform's own (`jcr:` and `rep:`, except `jcr:title` and `jcr:description`), or a type that text cannot fill (a reference,
a binary) is a configuration error.

When several rows fill the same multiple property, their values add up; for one property, **the form's fields, then the fixed
values, then the submitter, the organization and the page** win in that order. What a visitor types can never override what the platform knows.

## Files

A property filled from **the files of an upload field** stores them and points the content at them. The property is a
`reference` or `weakreference` property; if it is not multiple, a submission with more than one file for it is refused.

Each file is stored as a `jnt:file` under the files folder (and the user's subfolder):

- its node name is the original name made safe: lower case, accents removed, anything but letters and digits turned into a
  hyphen, at most 80 characters, the extension kept; a name that is taken gets `-1`, `-2`... (and a random suffix in the rare
  case of two submissions at the same instant);
- `jcr:title` is the original name, in every language of the site;
- the MIME type is the one the engine detected, never the browser's;
- an image the JDK can read (PNG, JPEG, GIF, BMP) also gets `jmix:image` with its width and height (read from the header, the image
  is not decoded);
- the **file mixin** is added, and the file properties of the mapping are written (the value of the field `credit` in `ctmdCredit`).

Files of an upload field the mapping does not use are not stored. **Required companion files** (`captions<-media:video/*`) say
that when `media` holds *n* files whose detected type matches the pattern (`video/mp4`, `video/*`, `*/*`),
`captions` must hold at least *n* files; otherwise the submission is refused with your sentence (or a generic one) before
anything is written.

## Stories: categories, rich text, the submitter's profile

A form for an editorial story (a supplier sends an article with its image, its topics and its authors) uses four more things
of the mapping. None of them lets a submitted value choose a node or a path.

### Categories

The mapping lists a **Categories** row (`j:defaultCategory`) for **every** content type, although the type does not declare the
property: the action adds the mixin `jmix:categorized` to the content (unless the type already is one) before it sets the
property, a multiple weak reference to the chosen categories. Two sources fill it; the values of every categories row are
united, without duplicates:

```json
{"target":"content","property":"j:defaultCategory","source":"field","nodeId":"…","fieldKey":"…","fieldName":"topic"}
{"target":"content","property":"j:defaultCategory","source":"pageCategories"}
```

- **A choice field in category mode** (`source: "field"`): the field carries the mixin `fmdbmix:categoryOptions` and a root category
  (`optionsRootCategory`). Formidable offers the direct children of that root, and the value the visitor sends is the child's
  **node name**. The action reads the children of the root (a system session, the `live` workspace, so the categories the
  visitor was offered) and looks each value up among them by name. An unknown value refuses the submission with the visitor
  sentence of an invalid value; a category is **never created**. A value is compared with the children's names, never used
  as a path: `../x`, `a/b`, a path or `name[2]` match nothing. A categories row that reads a field that is not in category
  mode (or whose root is gone or not published) is a configuration error ("temporarily unavailable").
- **The page's own categories** (`source: "pageCategories"`): the `j:defaultCategory` values of the page the form was sent
  from, found and read exactly as the **page** source does (live, readable by the submitter), copied as they are. No page, or a
  page without categories: nothing.

Nothing to write (no value submitted, no page category) writes neither the property nor the mixin. The selector's categories row
offers only the choice fields in category mode.

### Rich text

When the target property is a rich-text string (its selector is `richtext`) and the source is a **form field**, the action
turns the plain text the visitor typed into HTML: `&`, `<`, `>`, `"` and `'` are escaped, blank lines separate `<p>`
paragraphs, a single line break inside a paragraph becomes `<br>`. `a < b` and `<script>` give
`<p>a &lt; b</p>` and `<p>&lt;script&gt;</p>`. A text with no visible character writes nothing. A form field is **always** text:
there is no field whose value is meant as HTML. The same conversion applies to a file property filled from a field. A **fixed
value** typed in the mapping by the author is not converted (it is the author's, not a visitor's). Where the
`html-filtering` module is installed, it still filters what is saved, as for any rich text.

### The submitter's profile

A story's authors are content, not text. The module declares a mixin, `fmdbcntmix:submitterProfile`, with one property,
`submitterUser` (label "Submitter profile: user name"): an editor adds it to the content that stands for a person (an author
page, a contributor card) in the Content Editor (it extends `jnt:content`) and types the person's Jahia user name.

```json
{"target":"content","property":"authors","source":"submitterProfile"}
```

For a reference or weakreference property, single or multiple, the source **The submitter's profile** finds the nodes that
carry the mixin with the submitter's user name, writes references to them and nothing else:

- the query is a bound JCR-SQL2 query, `ISDESCENDANTNODE` of `/sites/<site>` where the site is the one of the **content folder**
  (the folder the author of the action configured: it must be under `/sites/<site key>`, or the action is misconfigured), run as the
  system user in the `default` workspace;
- only the profiles that satisfy the property's value constraints (the node types of the definition, for example a
  `jnt:person` or a `ctmd:author`; any one is enough) are kept;
- a **single** property takes the profile when exactly one fits; **several** fit: none is written and a warning is logged
  (the action never guesses); a **multiple** property takes them all (at most 50 candidates are read);
- none found: nothing is written and the log says so at info level; the story is still created. The profile content must be
  **published** for the reference to resolve in live.

The profile source cannot share a property with the page source or a files row (configuration error).

### A single image

A **files** row that targets a single weak reference with a node type constraint (a story's `image`,
`weakreference, picker[type='image'] < jmix:image`) stores the file and writes one reference to it. Every `image/*` file is
stored as a `jmix:image` (with its size when the JDK reads the header: PNG, JPEG, GIF, BMP; a WebP or an SVG gets the mixin
without a size), so the constraint holds. A file that cannot satisfy the constraint (a PDF for an image property) is refused
**before** anything is written, with the visitor sentence of an invalid value. A single property still refuses more than one file.

### A story, end to end

For a content type `ctmd:story` (`mix:title`, a rich-text `body`, `image` as above, a multiple weak reference `authors` to
`jnt:person`), the mapping of a supplier's story form reads:

| Property | Filled from |
|---|---|
| Title (`jcr:title`) | the form field `headline` |
| `body` | the form field `text` (a text area: escaped to paragraphs) |
| `image` | the files of the upload field `cover` |
| `authors` | the submitter's profile |
| Categories (`j:defaultCategory`) | the choice field `topic`, in category mode over the root `/sites/systemsite/categories/topics` |

```json
{"version":1,"rows":[
  {"target":"content","property":"jcr:title","source":"field","fieldName":"headline"},
  {"target":"content","property":"body","source":"field","fieldName":"text"},
  {"target":"content","property":"image","source":"files","fieldName":"cover"},
  {"target":"content","property":"authors","source":"submitterProfile"},
  {"target":"content","property":"j:defaultCategory","source":"field","fieldName":"topic"}
]}
```

(The editor also stores each field's node id and key, so a renamed field keeps its row.)

### The threat model

The new reads and writes run with a **system session**, like the rest of the action, so what limits them is what they can reach:

| Surface | What a visitor controls | What bounds it |
|---|---|---|
| Category values | The text of a choice field | The value is only a key into the map of the direct `jnt:category` children of the **field's own root** (chosen by the form's author, read in live). It is never used as a path, a query or a node name: an unknown value refuses the submission, nothing is created |
| Page categories | The `Referer` header (it finds the page, as for the page source) | The page must be on the form's site, readable by the submitter in live, and a page or a main resource; its categories are copied as identifiers and a deleted one is left out when written |
| Profile lookup | Nothing: the user name is the platform's (`JahiaUser.getUsername()`) | A bound variable; the path is `/sites/<site key>` from the **author-configured** content folder, the key validated as `[A-Za-z0-9_-]+` before it enters the statement (a path cannot be bound); each candidate is checked again in code (path under the site, exact user name); only identifiers leave the query |
| Rich text | The text of a field | Escaped to text: no tag, attribute or entity can come out of it |
| A file for a constrained reference | The file and its detected type | Checked against the constraint before the write; a file never chooses where it is stored |

What the module still trusts: the **author of the form** (the folders, the root categories, the properties), and the **editors
who add the profile mixin** and type user names: anyone who can edit a profile node can attach their content to another person's
user name, so restrict who may edit those nodes, as for any content that decides who is credited.

## The security model

The form is public; the action is not, and it writes with a **system session**. The submitter holds no write right in the
`default` workspace (a reader in an extranet never does), so no session of theirs could create the content: this is the usual
pattern for public-facing writes, with the submitter recorded on the node. The session is attributed to the submitter
(`jcr:createdBy`), but it applies no ACL. Everything that decides *where* the system session may write is therefore decided
**before** it is used, and again at **every** submission, in this order:

1. **Only signed-in users.** A guest is refused with 401 before the settings are read.
2. **The submitter's live session** must be a live session (it is, when the form is submitted normally).
3. **The submitter's folder.** With a group prefix, the submitter's groups (the platform's membership, nested groups included;
   only global groups and groups of the form's own site count) are searched for the one that starts with the prefix. No group,
   several groups, or what is left of the name not being a plain node name (no `/`, no `..`) refuses the submission. The
   subfolder must **already exist** under the content folder (and the files folder): it is never created.
4. **The submitter's own live session must be able to read the content subfolder, and the files subfolder.** An unreadable
   node is a `PathNotFoundException` for a user session, so this applies the folder's ACLs as the user. This is the check that
   keeps one company from posting into another's folder, **even if the form is misconfigured** (a wrong folder, a group named
   like another company's): the folder named by a group is only used when the folder's own rights say the user belongs there. A
   check that cannot be made refuses too.
5. **The type and the mappings.** The type must exist, be concrete, and be creatable in that folder; every mapped property must
   be declared by it; every value must convert.
6. **Then the write**: files first (each saved on its own), then the content with the references to them, then the translations,
   then the **scheduling** of the publication (Jahia's own publication service, with the translations of every language of the
   site). If a step fails up to and including the scheduling, what the earlier steps created is removed and the visitor reads
   "we could not save your submission". The publication itself is asynchronous: live follows within a second or two, and a
   failure inside the publication job is logged by the platform, after the visitor was answered.

What the visitor reads is always generic: "your account is not set up to send content here" for 3 and 4, "one of the values is
not valid" for a bad value, "temporarily unavailable" for a configuration error. Folder names, group names and properties go to
the server log only.

**The page property** is found from the `Referer` the browser sent, which the visitor controls. It is only used to *find*
a node: the address must be on the server the request reached, under `/sites/<this site>/` (behind a language such as `/fr/` or
Jahia's `/cms/render/live/fr/` prefix; the query, the fragment and the extension are dropped), with no empty, dotted or
encoded-separator segment; the node it names must be readable by the submitter in live **and** be a page or a content with its
own page. Anything else (another site, a vanity URL, an unreadable or ordinary node) leaves the property empty, never an error.

**What the module does not do**

- It adds **no CAPTCHA and no rate limit**: users are signed in and accountable. Turn on the form's CAPTCHA if you want one; the
  engine's upload limits (size, types) apply.
- It trusts the form's **author**: whoever can edit the action chooses the type, the folders and the properties. Protect the
  form's editing like any other configuration.
- It trusts the **folder ACLs** you set. A group with read access to a folder is allowed to send content into it.
- Publication follows the **Publish** setting, not the submitter's own right to publish: the whole point is that the
  submitter has none.
- Names are made unique by checking, then by retrying; two submissions of one file name at the same instant can cost one of
  them a retry. Nothing is overwritten.

## A worked example

Suppliers send briefs, each into their own folder, from the page of a brief.

- Groups `orvane-supplier-northlight` and `orvane-supplier-harbor`; folders `/sites/orvane/contents/briefs/<name>` and
  `/sites/orvane/files/briefs/<name>`, each closed to everyone but its group, published.
- A content type `ctmd:submission` (`mix:title`, a `submissionStatus` string, `submittedBy`, `company`, a `brief` weak reference,
  a multiple weak reference `media`, a multiple weak reference `captions`) and a mixin `ctmdmix:credited` with `ctmdCredit`.

The action:

| Setting | Value |
|---|---|
| Content type | `ctmd:submission` |
| Content folder / Files folder | `/sites/orvane/contents/briefs` / `/sites/orvane/files/briefs` |
| Group prefix | `orvane-supplier-` |
| File mixin | `ctmdmix:credited` |
| Required companion files | `captions<-media:video/*` |
| Message for a missing companion file | Every video needs its captions file (WebVTT). |

The mapping:

| Property | Filled from |
|---|---|
| Title (`jcr:title`) | the form field `title` |
| `description` | the form field `summary` |
| `media`, `captions` | the files of the upload fields `media`, `captions` |
| `submissionStatus` | the fixed value `submitted` |
| `submittedBy`, `company`, `brief` | the person who submits, their organization, the page the form was sent from |
| The stored files: `ctmdCredit` | the form field `credit` |

A member of `orvane-supplier-northlight` who submits from `/sites/orvane/contents/briefs/summer-launch.html` creates
`/sites/orvane/contents/briefs/northlight/<title>`, with `submissionStatus` `submitted`, `submittedBy` their name, `company` their
organization and `brief` pointing at the summer-launch brief; their files are in `/sites/orvane/files/briefs/northlight`. They see
it in live as soon as the publication ends. A redirect action placed after it can send them to the list of their submissions.

## Troubleshooting

The server log says what the visitor is not told, with the prefix `[formidable-content]`.

| The visitor reads | The log says | Cause |
|---|---|---|
| Please sign in to send this form | `the submitter is a guest` | Not signed in. Turn on **Require authentication** on the form |
| Your account is not set up to send content here | `has no folder through the groups of <user> ... (NONE)` | No group of the user starts with the prefix |
| same | `(SEVERAL)` | The user is in two company groups |
| same | `(UNSAFE)` | What is left of the group name is not a plain node name |
| same | `The folder <path> of <user> does not exist` | The subfolder was not created: create it, with its ACL, and publish it |
| same | `<user> cannot read <path> in live` | The folder's ACL does not give the user's group a read role, or it is not published |
| This form is temporarily unavailable | `the action is misconfigured. Setting 'mapping': ...` | A field the mapping reads left the form, a property the type no longer has, a type that cannot be created there, a folder that is not published |
| One of the values you entered is not valid | `field '...': Property '...' expects ...` | A value that does not fit its property |
| same | `field '...': a value is not a category offered by the field` | A category value that is not the node name of a direct `jnt:category` child of the field's root |
| same | `a file of '...' does not fit the property '...', which takes [jmix:image] only` | A file that cannot satisfy the reference's node type constraint (a PDF for an image) |
| This form is temporarily unavailable | `... is not a choice field in category mode` / `The root category ... does not exist in live` | A categories row reads a field that is not in category mode, or its root category is deleted or not published |
| same | `the profile of the submitter is looked up in the site of the content folder, which is not under /sites/<site key>` | The content folder is outside `/sites/<site key>`: the profile source needs a site |
| (nothing: the story is created) | `has no profile in the site ... that fits '...'` (info) or `has N profiles ... which holds one: none is written` (warn) | No profile node carries the user name, or several for a single property |
| The author's sentence, or "a file is missing" | `a file of '...' matching ... has no companion` | A required companion file is missing |
| We could not save your submission | `could not be created in <path>: nothing was kept` | The platform refused a step (mandatory property, constraint, publication); the cause is logged |

## Java layout

```
org.jahia.modules.formidable.content
  action/       CreateContentFormAction (the ContextualFormAction and its checks), ActionProps
  choicelist/   FormFieldsInitializer (the form's fields, for the title field)
  convert/      ValueConverter, PropertyShape, TypedValue (text to the property's type), PlainTextHtml (text to safe HTML)
  folders/      SubmitterFolder (the group's subfolder)
  mapping/      Mappings (left=right lines), PairRule (companion files)
  names/        NodeNames (safe, unique node names), ImageSize (header only)
  page/         RefererPages (the Referer as repository paths)
  plan/         PlanBuilder, ContentPlan, Settings (everything decided and validated before a write)
  platform/     Submitters, TypeCatalog, Categories, SubmitterProfiles (seams over the platform) and their Jahia implementations
  store/        ContentWriter, JcrContentWriter (the only code that writes, and cleans up)
  messages/     VisitorText (what a visitor can read, EN, FR, PT)
```

The editor labels of the action and the sentences a visitor reads are in the one bundle of the module
(`formidable-content{,_fr,_pt}.properties`); the Content Editor selector (`ContentMapping`) is the UI extension of the module,
in the i18n namespace `formidable-content`.
