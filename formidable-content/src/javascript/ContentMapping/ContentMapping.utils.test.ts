import {describe, expect, it} from 'vitest';
import {
    CATEGORIES,
    categoryRowsReadingAnotherField,
    contentRows,
    fieldsFor,
    orphanRows,
    parseMapping,
    resolveRowField,
    serializeMapping,
    siblingValue,
    sourcesFor,
    splitValues,
    visibleProperties
} from './ContentMapping.utils';
import type {FormFieldOption, PropertyOption} from './ContentMapping.types';

const property = (name: string, requiredType: string, multiple = false): PropertyOption => ({name, label: name, requiredType, multiple, mandatory: false});
const FIELDS: FormFieldOption[] = [
    {id: 'id-title', name: 'title', label: 'Title', isFile: false},
    {id: 'id-media', name: 'media', label: 'Photos', isFile: true, fieldKey: 'k-media'},
    {id: 'id-topic', name: 'topic', label: 'Topic', isFile: false, isCategory: true}
];

describe('sourcesFor', () => {
    it('offers what the action accepts on the server, property by property', () => {
        expect(sourcesFor(property('jcr:title', 'STRING'), 'content')).toEqual(['field']);
        expect(sourcesFor(property('company', 'STRING'), 'content')).toEqual(['field', 'constant', 'submitter', 'organization']);
        expect(sourcesFor(property('tags', 'STRING', true), 'content')).toEqual(['field', 'constant']);
        expect(sourcesFor(property('shotDate', 'DATE'), 'content')).toEqual(['field', 'constant']);
        expect(sourcesFor(property('media', 'WEAKREFERENCE', true), 'content')).toEqual(['files', 'submitterProfile']);
        expect(sourcesFor(property('brief', 'WEAKREFERENCE'), 'content')).toEqual(['files', 'page', 'submitterProfile']);
        expect(sourcesFor(property('authors', 'REFERENCE', true), 'content')).toEqual(['files', 'submitterProfile']);
        expect(sourcesFor(property('data', 'BINARY'), 'content')).toEqual([]);
        expect(sourcesFor(property('ctmdCredit', 'STRING'), 'file')).toEqual(['field']);
        expect(sourcesFor(property('ref', 'REFERENCE'), 'file')).toEqual([]);
    });

    it('offers the submitter profile to references only, and the categories their two sources', () => {
        expect(sourcesFor(property('company', 'STRING'), 'content')).not.toContain('submitterProfile');
        expect(sourcesFor(property('tags', 'STRING', true), 'content')).not.toContain('pageCategories');
        expect(sourcesFor(property(CATEGORIES, 'WEAKREFERENCE', true), 'content')).toEqual(['field', 'pageCategories']);
        expect(sourcesFor(property('ref', 'REFERENCE'), 'file')).toEqual([]);
        expect(sourcesFor(property('authors', 'WEAKREFERENCE', true), 'file')).toEqual([]);
    });
});

describe('the stored document', () => {
    it('round-trips each kind of row in the shape the Java side reads, dropping what a source does not own', () => {
        const doc = parseMapping(JSON.stringify({version: 1, future: 'kept', rows: [
            {target: 'content', property: 'jcr:title', source: 'field', nodeId: 'id-title', fieldName: 'title', values: ['stray']},
            {target: 'content', property: 'status', source: 'constant', values: ['submitted', '  '], nodeId: 'stray'},
            {target: 'content', property: 'submittedBy', source: 'submitter', fieldName: 'stray'},
            {target: 'file', property: 'ctmdCredit', source: 'field', nodeId: 'id-credit', extra: 1},
            {target: 'content', property: 'j:defaultCategory', source: 'pageCategories', nodeId: 'stray', values: ['x']},
            {target: 'content', property: 'authors', source: 'submitterProfile', fieldName: 'stray'},
            {source: 'field'}
        ]}));
        const stored = JSON.parse(serializeMapping(doc));

        expect(stored.future).toBe('kept');
        expect(stored.rows).toEqual([
            {target: 'content', property: 'jcr:title', source: 'field', nodeId: 'id-title', fieldKey: '', fieldName: 'title'},
            {target: 'content', property: 'status', source: 'constant', values: ['submitted']},
            {target: 'content', property: 'submittedBy', source: 'submitter'},
            {target: 'file', property: 'ctmdCredit', source: 'field', nodeId: 'id-credit', fieldKey: '', fieldName: '', extra: 1},
            {target: 'content', property: 'j:defaultCategory', source: 'pageCategories'},
            {target: 'content', property: 'authors', source: 'submitterProfile'}
        ]);
    });

    it('reads anything unreadable as an empty mapping', () => {
        expect(parseMapping('caption=caption').rows).toEqual([]);
        expect(parseMapping(undefined).rows).toEqual([]);
    });
});

describe('fields', () => {
    it('are the upload fields for files and the others for a value, and a row resolves by id, key, then name', () => {
        expect(fieldsFor('files', FIELDS).map(field => field.name)).toEqual(['media']);
        expect(fieldsFor('field', FIELDS).map(field => field.name)).toEqual(['title', 'topic']);
        expect(resolveRowField({target: 'content', property: 'x', source: 'files', fieldKey: 'k-media'}, FIELDS)?.id).toBe('id-media');
        expect(resolveRowField({target: 'content', property: 'x', source: 'field', nodeId: 'gone', fieldName: 'title'}, FIELDS)?.id).toBe('id-title');
        expect(resolveRowField({target: 'content', property: 'x', source: 'field', nodeId: 'gone'}, FIELDS)).toBeUndefined();
    });
});

describe('the rows shown', () => {
    it('put the title first, leave out what nothing can fill, and set apart the rows of properties the types lost', () => {
        const props = [property('caption', 'STRING'), property('data', 'BINARY'), property('jcr:title', 'STRING')];
        expect(visibleProperties(props, 'content').map(p => p.name)).toEqual(['jcr:title', 'caption']);
        const rows = [{target: 'content' as const, property: 'gone', source: 'submitter' as const}, {target: 'content' as const, property: 'caption', source: 'constant' as const}];
        expect(orphanRows(rows, props, []).map(row => row.property)).toEqual(['gone']);
    });

    it('split one value per line and read a sibling setting whatever shape Formik keeps', () => {
        expect(splitValues('a\n\n b \r\nc')).toEqual(['a', 'b', 'c']);
        // the Content Editor names a field <fieldset>_<property>: the type's own fieldset, or the one a layout moved it to
        const values = {'fmdbcnt:createContentAction_contentType': ' ctmd:submission ', files_fileMixin: ['ctmdmix:credited'], files_filesFolder: 'x'};
        expect(siblingValue(values, 'contentType')).toBe('ctmd:submission');
        expect(siblingValue(values, 'fileMixin')).toBe('ctmdmix:credited');
        expect(siblingValue(values, 'groupPrefix')).toBe('');
        expect(siblingValue(undefined, 'contentType')).toBe('');
    });
});

describe('the categories row', () => {
    it('reads only the choice fields in category mode', () => {
        expect(fieldsFor('field', FIELDS, CATEGORIES).map(field => field.name)).toEqual(['topic']);
        expect(fieldsFor('field', FIELDS, 'title').map(field => field.name)).toEqual(['title', 'topic']);
    });

    it('is listed last for every content type, once, whether or not the type declares j:defaultCategory itself', () => {
        const plain = [property('caption', 'STRING'), property('jcr:title', 'STRING')];
        const rows = contentRows(plain, 'Categories');
        expect(rows.map(row => row.name)).toEqual(['jcr:title', 'caption', CATEGORIES]);
        expect(rows[rows.length - 1]).toMatchObject({label: 'Categories', requiredType: 'WEAKREFERENCE', multiple: true, mandatory: false});

        const declared = contentRows([...plain, property(CATEGORIES, 'WEAKREFERENCE', true), property('data', 'BINARY')], 'Catégories');
        expect(declared.map(row => row.name)).toEqual(['jcr:title', 'caption', CATEGORIES]);
        expect(declared[2].label).toBe('Catégories');
        expect(contentRows([], 'Categorias').map(row => row.name)).toEqual([CATEGORIES]);
    });

    it('is never an orphan, and a row reading a field that is not in category mode is flagged', () => {
        const rows = [
            {target: 'content' as const, property: CATEGORIES, source: 'pageCategories' as const},
            {target: 'content' as const, property: CATEGORIES, source: 'field' as const, nodeId: 'id-topic'},
            {target: 'content' as const, property: 'gone', source: 'submitter' as const}
        ];
        expect(orphanRows(rows, [], []).map(row => row.property)).toEqual(['gone']);
        expect(categoryRowsReadingAnotherField(rows, FIELDS)).toEqual([]);
        const wrong = [{target: 'content' as const, property: CATEGORIES, source: 'field' as const, nodeId: 'id-title'}];
        expect(categoryRowsReadingAnotherField(wrong, FIELDS)).toHaveLength(1);
        const gone = [{target: 'content' as const, property: CATEGORIES, source: 'field' as const, nodeId: 'nope'}];
        expect(categoryRowsReadingAnotherField(gone, FIELDS)).toEqual([]);
    });

    it('serializes a category row from a field with its three identifiers, like any field row', () => {
        const doc = parseMapping(JSON.stringify({rows: [{target: 'content', property: CATEGORIES, source: 'field', nodeId: 'id-topic', fieldKey: 'k', fieldName: 'topic'}]}));
        expect(JSON.parse(serializeMapping(doc)).rows).toEqual([
            {target: 'content', property: CATEGORIES, source: 'field', nodeId: 'id-topic', fieldKey: 'k', fieldName: 'topic'}
        ]);
    });
});
