/**
 * The mapping document of fmdbcnt:createContentAction (property `mapping`), as stored. This shape is the ONLY
 * contract with the Java side (org.jahia.modules.formidable.content.mapping.ContentMapping): keep it exact.
 * Unknown keys are kept verbatim on re-serialization, so a document written by a newer editor round-trips.
 */
export type MappingTarget = 'content' | 'file';

/** Where a row's value comes from. */
export type MappingSource = 'field' | 'files' | 'constant' | 'submitter' | 'organization' | 'page';

export interface MappingRow {
    target: MappingTarget;
    property: string;
    source: MappingSource;
    // field and files rows: the three identifiers of the form field (node id first, then key, then name)
    nodeId?: string;
    fieldKey?: string;
    fieldName?: string;
    // constant rows
    values?: string[];
    [extra: string]: unknown;
}

export interface MappingDocument {
    version: number;
    rows: MappingRow[];
    [extra: string]: unknown;
}

/** A property of the content type (or of the stored files), from the platform's node type GraphQL. */
export interface PropertyOption {
    name: string;
    label: string;
    requiredType: string;
    multiple: boolean;
    mandatory: boolean;
}

/** A submittable field of the enclosing form. */
export interface FormFieldOption {
    id: string;
    fieldKey?: string;
    name: string;
    label: string;
    isFile: boolean;
}
