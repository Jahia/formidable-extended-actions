import type {FormFieldOption, MappingDocument, MappingRow, MappingSource, MappingTarget, PropertyOption} from './ContentMapping.types';

export const MAPPING_VERSION = 1;
/** Formik keys of the sibling settings the mapping depends on. */
export const CONTENT_TYPE_KEY = 'fmdbcnt:createContentAction_contentType';
export const FILE_MIXIN_KEY = 'fmdbcnt:createContentAction_fileMixin';
export const TITLE = 'jcr:title';

const SOURCES: MappingSource[] = ['field', 'files', 'constant', 'submitter', 'organization', 'page'];
const isRecord = (candidate: unknown): candidate is Record<string, unknown> =>
    typeof candidate === 'object' && candidate !== null && !Array.isArray(candidate);
const asString = (candidate: unknown): string => (typeof candidate === 'string' ? candidate : '');

const normalizeRow = (raw: unknown): MappingRow | null => {
    if (!isRecord(raw) || !asString(raw.property)) {
        return null;
    }

    const source = SOURCES.includes(raw.source as MappingSource) ? raw.source as MappingSource : 'field';
    const target: MappingTarget = raw.target === 'file' ? 'file' : 'content';
    const row: MappingRow = {...raw, target, property: asString(raw.property), source};
    if (source === 'constant') {
        row.values = Array.isArray(raw.values) ? raw.values.filter((value): value is string => typeof value === 'string') : [];
    }

    return row;
};

/** Parses the stored value; anything unreadable yields the empty document (the editor never throws on load). */
export const parseMapping = (value?: string): MappingDocument => {
    if (!value || value.trim() === '') {
        return {version: MAPPING_VERSION, rows: []};
    }

    try {
        const parsed: unknown = JSON.parse(value);
        const rawRows = Array.isArray(parsed) ? parsed : (isRecord(parsed) && Array.isArray(parsed.rows) ? parsed.rows : []);
        const base = isRecord(parsed) ? parsed : {};
        return {...base, version: MAPPING_VERSION, rows: rawRows.map(normalizeRow).filter((row): row is MappingRow => row !== null)};
    } catch {
        return {version: MAPPING_VERSION, rows: []};
    }
};

/** Serializes in the stored shape: a field row keeps its three identifiers, a constant its values, the others nothing. */
export const serializeMapping = (doc: MappingDocument): string => JSON.stringify({
    ...doc,
    version: MAPPING_VERSION,
    rows: doc.rows.map(row => {
        const stored: MappingRow = {...row};
        if (row.source === 'field' || row.source === 'files') {
            stored.nodeId = row.nodeId ?? '';
            stored.fieldKey = row.fieldKey ?? '';
            stored.fieldName = row.fieldName ?? '';
            delete stored.values;
        } else if (row.source === 'constant') {
            stored.values = (row.values ?? []).filter(value => value.trim() !== '');
            delete stored.nodeId;
            delete stored.fieldKey;
            delete stored.fieldName;
        } else {
            delete stored.values;
            delete stored.nodeId;
            delete stored.fieldKey;
            delete stored.fieldName;
        }

        return stored;
    })
});

const REFERENCE_TYPES = new Set(['REFERENCE', 'WEAKREFERENCE']);
const CONVERTIBLE_TYPES = new Set(['STRING', 'LONG', 'DOUBLE', 'DECIMAL', 'BOOLEAN', 'DATE']);

/**
 * The sources a property can take, in the order the editor offers them; the same rules the action checks on the
 * server (PlanBuilder): a reference takes the files of an upload field (several when multiple) or, single, the page;
 * a single text the submitter or the organization; every convertible type a form field or a fixed value. The title
 * also names the node, so it takes a form field only. A file property takes a form field only.
 */
export const sourcesFor = (property: PropertyOption, target: MappingTarget): MappingSource[] => {
    const type = property.requiredType.toUpperCase();
    if (target === 'file') {
        return CONVERTIBLE_TYPES.has(type) ? ['field'] : [];
    }

    if (property.name === TITLE) {
        return ['field'];
    }

    if (REFERENCE_TYPES.has(type)) {
        return property.multiple ? ['files'] : ['files', 'page'];
    }

    if (!CONVERTIBLE_TYPES.has(type)) {
        return [];
    }

    return type === 'STRING' && !property.multiple
        ? ['field', 'constant', 'submitter', 'organization']
        : ['field', 'constant'];
};

/** The form fields a source reads: the upload fields for files, every other submittable field for a value. */
export const fieldsFor = (source: MappingSource, fields: FormFieldOption[]): FormFieldOption[] =>
    fields.filter(field => (source === 'files' ? field.isFile : !field.isFile));

/** The form field a stored row designates: node id first, then the field key, then the name (a re-created field). */
export const resolveRowField = (row: MappingRow, fields: FormFieldOption[]): FormFieldOption | undefined => {
    if (row.source !== 'field' && row.source !== 'files') {
        return undefined;
    }

    return (row.nodeId ? fields.find(field => field.id === row.nodeId) : undefined)
        ?? (row.fieldKey ? fields.find(field => field.fieldKey === row.fieldKey) : undefined)
        ?? (row.fieldName ? fields.find(field => field.name === row.fieldName) : undefined);
};

/**
 * The properties shown, in the order a contributor reads them: the title first, then the type's own properties as the
 * definition lists them; protected and hidden ones are never offered, nor a property no source can fill.
 */
export const visibleProperties = (properties: PropertyOption[], target: MappingTarget): PropertyOption[] => {
    const usable = properties.filter(property => sourcesFor(property, target).length > 0);
    const title = usable.filter(property => property.name === TITLE);
    return [...title, ...usable.filter(property => property.name !== TITLE)];
};

/** Rows whose property the current types no longer have: shown apart, so the author removes them. */
export const orphanRows = (rows: MappingRow[], contentProperties: PropertyOption[], fileProperties: PropertyOption[]): MappingRow[] =>
    rows.filter(row => !(row.target === 'file' ? fileProperties : contentProperties).some(property => property.name === row.property));

/** One value per line in, the values out (blank lines dropped). */
export const splitValues = (text: string): string[] => text.split(/\r?\n/).map(value => value.trim()).filter(value => value !== '');

/** Reads a sibling setting of the edit form (Formik holds a string, or a one-element array for a choice list). */
export const siblingValue = (values: Record<string, unknown> | undefined, key: string): string => {
    const raw = values?.[key];
    if (typeof raw === 'string') {
        return raw.trim();
    }

    return Array.isArray(raw) && typeof raw[0] === 'string' ? raw[0].trim() : '';
};
