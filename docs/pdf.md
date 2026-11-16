# formidable-pdf: a PDF copy for the visitor

The **PDF copy** action (`fmdbpdf:pdfCopyAction`) gives the visitor a PDF of what they submitted: a **Download a
copy (PDF)** button appears with the confirmation message. The document is their record of the submission, with a
reference and the time it was received. Module `formidable-pdf`, Java package `org.jahia.modules.formidable.pdf`.
Requires Jahia 8.2.3.0 or later (the platform exports PDFBox 3 to modules; checked on 8.2.3.2) and
`formidable-engine` 0.6.

## For the contributor

Add **PDF copy** to the form's actions (New Form Action). Its place among the actions does not matter. Three
optional settings, each per language:

| Setting | What it does | Empty |
|---|---|---|
| **Document title** | The heading of the PDF | The form's title |
| **Button label** | The text of the download button | *Download a copy (PDF)* |
| **Closing note** | A line printed after the answers, plain text | Nothing |

Publish the form. The button shows on the live site only, after a submission every action accepted: when another
action refuses the submission (a CRM that rejects the lead), the visitor gets no copy of something that was not
recorded.

## What the document holds

- The title, then **Reference** and **Submitted on**, in the visitor's language and time zone.
- Every field that received an answer, under the label the visitor saw, in the form's order, grouped as the form
  groups them (a step, a fieldset: `Your claim / The damage`).
- A choice shows the label of its option, typed in the form or read from a category or a content item; a yes/no
  field says Yes or No; a date is written as the language writes it; an attached file shows its name and size.
- The closing note, and at the foot of each page the reference and the page number.

Left out: a field the visitor did not answer or did not see (hidden by its conditional display), the content of
the attached files, and any sensitive field (a password never reaches an action).

The text is set in Liberation Sans, embedded: Latin, Greek and Cyrillic print as typed. A character the font has
no glyph for (Chinese, Arabic, an emoji) prints as `?`. The option of a declared options source prints as its
stored value.

## The reference is a fingerprint

The reference is not a number the module stores. It is the start of the SHA-256 of the submitted values: the same
answers to the same form always give the same reference, so you can compute it again from a stored submission and
compare it with the one a visitor quotes. The full fingerprint is in the PDF's keywords.

The text hashed, in UTF-8, one line per item, each ended by a line feed:

```
form=<UUID of the form>
<field name>=<value>                               one line per value, fields by name
file:<field name>/<file name>:<size in bytes>      files by field name then file name
```

The reference is the first 12 hexadecimal characters, upper case, in groups of four (`524B-34DB-1A2B`). It proves
that the document matches a stored submission; it is not an electronic signature.

## How it works

| Part | Role |
|---|---|
| `PdfCopyFormAction` | Builds the PDF during the submission and holds it. Never fails a submission: a copy that cannot be built is logged and the visitor gets the confirmation without the button |
| `ReceiptStore` | Keeps the copies in memory, for a while. Nothing is written to the repository |
| `ReceiptEnricher` | A `SubmissionResponseEnricher`: once every action succeeded, adds `pdfCopy: {url, label, fileName}` to the submission's answer |
| `PdfCopyScriptFilter` | A render filter: declares the page script on the live pages whose form has the action |
| `formidable-pdf.js` | Listens to `formidable:submitted` and adds the button to the confirmation message (class `fmdb-pdf-copy`, with the form's `fmdb-btn fmdb-btn-primary`) |
| `ReceiptServlet` | `GET <context>/modules/formidable-pdf/copy?t=<token>` serves the PDF as a download |

The link carries a token of 256 random bits, given only to the browser that made the submission. It is the whole
authorisation: anyone who has the link can download the copy until it expires, so the answer is sent with
`Cache-Control: no-store` and `Referrer-Policy: no-referrer`. The servlet answers GET only and changes nothing.

## Configuration

`org.jahia.modules.formidable.pdf.cfg`, copied to `digital-factory-data/karaf/etc` at the first start and kept as
edited:

| Key | Default | Meaning |
|---|---|---|
| `downloadMinutes` | 30 | Minutes a copy can be downloaded for, from the submission (1 to 1440) |
| `maxReceipts` | 500 | Copies kept at once; past it, the oldest is dropped (1 to 10000) |

**Copies live in memory, per JVM.** A restart drops them, and on a cluster the download must reach the node that
took the submission (a sticky session). A visitor who comes back after the minutes have passed reads "This copy is
no longer available".

## Not done

- No copy by e-mail, and no copy stored with the submission: the document exists for the visitor's download only.
- No logo, colours or page layout to configure.
- English and French only for the editor labels and the sentences of the document.
- Cypress tests: the module has unit tests (the document, the store, the reader, the labels); the button was
  checked by hand on a live form.
