package org.jahia.modules.formidable.pdf.store;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/** The settings of {@code org.jahia.modules.formidable.pdf.cfg}: how long and how many copies wait for their download. */
@ObjectClassDefinition(name = "Formidable PDF", description = "How long a visitor's PDF copy can be downloaded, and how many are kept at once")
public @interface ReceiptConfig {

    String PID = "org.jahia.modules.formidable.pdf";

    @AttributeDefinition(description = "Minutes a copy can be downloaded for, from the submission. 1 to 1440.")
    int downloadMinutes() default 30;

    @AttributeDefinition(description = "Copies kept at once, per JVM. Past it, the oldest is dropped. 1 to 10000.")
    int maxReceipts() default 500;
}
