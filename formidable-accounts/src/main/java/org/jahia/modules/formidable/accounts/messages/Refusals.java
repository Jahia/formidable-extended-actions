package org.jahia.modules.formidable.accounts.messages;

import org.jahia.modules.formidable.engine.api.FormActionException;

/**
 * How these actions refuse a submission. Formidable 0.6 answers a failed action with its status and the opaque code
 * {@code FMDB-008}, never with a text of the action: the visitor reads the form's own error message. The sentence
 * meant for the visitor travels on the exception all the same, so that the call sites and the tests still say what
 * the visitor should read, and the module takes it up again the day the engine carries a text with a refusal.
 *
 * <p>Stateless; thread-safe.</p>
 */
public final class Refusals {

    private Refusals() {
    }

    /** A refusal that remembers the sentence meant for the visitor. */
    public static final class Refusal extends FormActionException {

        private static final long serialVersionUID = 1L;

        private final String visitorText;

        private Refusal(String logMessage, int status, String visitorText, Throwable cause) {
            super(logMessage, status, cause);
            this.visitorText = visitorText;
        }

        /** @return what the visitor should read; not sent by Formidable 0.6 */
        public String getVisitorMessage() {
            return visitorText;
        }
    }

    /**
     * @param status      the HTTP status of the answer (4xx or 5xx)
     * @param logMessage  what the log says; never shown to the visitor
     * @param visitorText what the visitor should read
     * @return the failure to throw
     */
    public static FormActionException refusal(int status, String logMessage, String visitorText) {
        return new Refusal(logMessage, status, visitorText, null);
    }

    /** As {@link #refusal(int, String, String)} with the cause, for the log. */
    public static FormActionException refusal(int status, String logMessage, String visitorText, Throwable cause) {
        return new Refusal(logMessage, status, visitorText, cause);
    }

    /** @return the sentence a failure holds for the visitor, {@code null} for a failure that is not a refusal */
    public static String visitorText(FormActionException failure) {
        return failure instanceof Refusal refusal ? refusal.getVisitorMessage() : null;
    }
}
