/*
 * Formidable PDF: the download button of a visitor's PDF copy.
 *
 * The form island announces an accepted submission with a bubbling `formidable:submitted` DOM event whose detail
 * carries the server's answer. When the form has the PDF copy action, that answer holds a `pdfCopy` block
 * ({url, label, fileName}); this script adds a link to it inside the confirmation message, styled as the form's
 * own buttons. The link leaves with the message when the visitor starts a new form.
 *
 * One instance per page whatever the number of forms: every form declares the same static asset.
 */
(function () {
  "use strict";

  if (window.formidablePdf) {
    return;
  }
  window.formidablePdf = true;

  const MESSAGE = ".fmdb-message-success .fmdb-message-content";
  const BUTTON = "fmdb-pdf-copy";

  function button(copy) {
    const link = document.createElement("a");
    link.className = "fmdb-btn fmdb-btn-primary " + BUTTON;
    link.href = copy.url;
    link.setAttribute("download", copy.fileName || "");
    link.rel = "nofollow noopener";
    // a link drawn as the form's buttons: the site's own link style must not underline it
    link.style.display = "inline-block";
    link.style.textDecoration = "none";
    link.textContent = copy.label;
    return link;
  }

  /** The confirmation message is drawn after the event: wait for it next to the form, a few seconds at most. */
  function whenMessage(form, then) {
    const root = form.parentElement || document.body;
    const found = () => root.querySelector(MESSAGE);
    if (found()) {
      then(found());
      return;
    }
    const observer = new MutationObserver(() => {
      const message = found();
      if (message) {
        observer.disconnect();
        then(message);
      }
    });
    observer.observe(root, { childList: true, subtree: true });
    window.setTimeout(() => observer.disconnect(), 5000);
  }

  document.addEventListener("formidable:submitted", (event) => {
    const copy = event.detail && event.detail.response && event.detail.response.pdfCopy;
    // only a path of this site, as the server builds it: never a link another script could have put in the answer
    if (!copy || typeof copy.url !== "string" || !/^\/(?!\/)/.test(copy.url) || !copy.label || !(event.target instanceof Element)) {
      return;
    }
    whenMessage(event.target, (message) => {
      if (!message.querySelector("." + BUTTON)) {
        message.appendChild(button(copy));
      }
    });
  });
})();
