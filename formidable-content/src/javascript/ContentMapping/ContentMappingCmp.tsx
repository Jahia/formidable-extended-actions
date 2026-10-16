import {useApolloClient} from '@apollo/client';
import {Button, Close, Dropdown, Input, Loader, Typography} from '@jahia/moonstone';
import React, {useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {CURRENT_NODE_BY_PATH, FORM_FIELDS_BY_PATH, NODE_TYPE_PROPERTIES} from './graphql';
import {
    CONTENT_TYPE_KEY,
    FILE_MIXIN_KEY,
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
import type {FormFieldOption, MappingRow, MappingSource, MappingTarget, PropertyOption} from './ContentMapping.types';
import {currentNodePathOf, formPathOf, languageOf, type SelectorProps, workspaceOf} from '../shared/editorContext';
import './contentMapping.css';

/** Moonstone Dropdown option; the package does not re-export its own type. */
type DropdownOption = {label: string; value?: string; description?: string};

const NONE = 'none';
const NS = 'formidable-content';

interface RawProperty {
    name: string;
    displayName?: string | null;
    requiredType: string;
    multiple?: boolean | null;
    mandatory?: boolean | null;
    protected?: boolean | null;
    hidden?: boolean | null;
}

interface RawField {
    uuid: string;
    name: string;
    displayName?: string | null;
    isNonSubmittable?: boolean;
    isFileField?: boolean;
    properties?: Array<{name: string; value?: string | null}> | null;
}

const toPropertyOptions = (raw: RawProperty[] = []): PropertyOption[] => raw
    .filter(property => !property.protected && !property.hidden)
    .map(property => ({
        name: property.name,
        label: property.displayName && property.displayName !== property.name ? property.displayName : property.name,
        requiredType: property.requiredType,
        multiple: Boolean(property.multiple),
        mandatory: Boolean(property.mandatory)
    }));

const toFieldOptions = (raw: RawField[] = []): FormFieldOption[] => raw
    .filter(field => !field.isNonSubmittable)
    .map(field => ({
        id: field.uuid,
        fieldKey: field.properties?.find(property => property.name === 'fieldKey')?.value ?? undefined,
        name: field.name,
        label: field.displayName || field.name,
        isFile: Boolean(field.isFileField)
    }));

/** The value cell of a row: the form field it reads, its fixed values, or what the platform fills. */
const ValueCell = ({row, multiple, fields, readOnly, inputId, onField, onValues}: {
    row?: MappingRow;
    multiple: boolean;
    fields: FormFieldOption[];
    readOnly: boolean;
    inputId: string;
    onField: (field: FormFieldOption) => void;
    onValues: (values: string[]) => void;
}) => {
    const {t} = useTranslation(NS);
    if (!row) {
        return <span/>;
    }

    if (row.source === 'field' || row.source === 'files') {
        const choices = fieldsFor(row.source, fields);
        const resolved = resolveRowField(row, fields);
        const options: DropdownOption[] = choices.map(field => ({label: field.label, value: field.id, description: field.name}));
        if (!resolved && (row.fieldName || row.nodeId)) {
            // a field that left the form keeps a labelled entry, rather than an empty chip that reads as data loss
            options.push({label: t('contentMapping.missingField', {name: row.fieldName || row.nodeId}), value: row.nodeId || row.fieldName});
        }

        return (
            <Dropdown
                variant="outlined"
                data={options}
                hasSearch={options.length >= 6}
                value={resolved?.id ?? (row.nodeId || row.fieldName || undefined)}
                placeholder={t(row.source === 'files' ? 'contentMapping.selectFileField' : 'contentMapping.selectField')}
                isDisabled={readOnly}
                onChange={(_event, item) => {
                    const field = choices.find(candidate => candidate.id === item.value);
                    if (field) {
                        onField(field);
                    }
                }}
            />
        );
    }

    if (row.source === 'constant') {
        const values = row.values ?? [];
        if (multiple) {
            return (
                <textarea
                    id={inputId}
                    className="fmdbxa-contentMappingValues"
                    rows={Math.max(2, values.length + 1)}
                    readOnly={readOnly}
                    placeholder={t('contentMapping.valuesPlaceholder')}
                    aria-label={t('contentMapping.columns.value')}
                    value={values.join('\n')}
                    onChange={event => onValues(splitValues(event.target.value))}
                />
            );
        }

        return (
            <Input
                id={inputId}
                isReadOnly={readOnly}
                placeholder={t('contentMapping.valuePlaceholder')}
                aria-label={t('contentMapping.columns.value')}
                value={values[0] ?? ''}
                size="big"
                onChange={event => onValues([event.target.value])}
            />
        );
    }

    return <Typography variant="caption" className="fmdbxa-contentMappingMuted">{t(`contentMapping.filled.${row.source}`)}</Typography>;
};

/**
 * The ContentMapping selector type: the `mapping` setting of the Create content action. One row per property of the
 * chosen content type, then one per property of the stored files (jnt:file and the file mixin): a source chosen among
 * what the property can take, and its form field or fixed values. Stored as one JSON document (ContentMapping.types),
 * only the rows that map something. It writes only on a user edit: the Content Editor derives its dirtiness from the
 * form values.
 */
export const ContentMappingCmp = (props: SelectorProps) => {
    const {field, id, value, onChange} = props;
    const {t} = useTranslation(NS);
    const client = useApolloClient();
    const readOnly = Boolean(props.readOnly || field.readOnly);
    const nodePath = currentNodePathOf(props);
    const language = languageOf(props);
    const workspace = workspaceOf(props);
    const contentType = siblingValue(props.form?.values, CONTENT_TYPE_KEY);
    const fileMixin = siblingValue(props.form?.values, FILE_MIXIN_KEY);

    const [fields, setFields] = useState<FormFieldOption[]>([]);
    const [fieldsState, setFieldsState] = useState<'loading' | 'ready' | 'error'>('loading');
    const [contentProperties, setContentProperties] = useState<PropertyOption[]>([]);
    const [fileProperties, setFileProperties] = useState<PropertyOption[]>([]);
    const [typesState, setTypesState] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');

    const doc = useMemo(() => parseMapping(value), [value]);
    const rows = doc.rows;

    // (a) The submittable fields of the enclosing form.
    useEffect(() => {
        let cancelled = false;
        const load = async () => {
            if (!nodePath) {
                setFieldsState('error');
                return;
            }

            setFieldsState('loading');
            try {
                const current = await client.query({query: CURRENT_NODE_BY_PATH, variables: {path: nodePath, workspace}, fetchPolicy: 'network-only'});
                const formPath = formPathOf(current.data?.jcr?.nodeByPath);
                if (!formPath) {
                    throw new Error(`No fmdb:form above ${nodePath}`);
                }

                const result = await client.query({query: FORM_FIELDS_BY_PATH, variables: {path: formPath, workspace, language}, fetchPolicy: 'network-only'});
                if (!cancelled) {
                    setFields(toFieldOptions(result.data?.jcr?.nodeByPath?.descendants?.nodes ?? []));
                    setFieldsState('ready');
                }
            } catch (error) {
                if (!cancelled) {
                    console.error('[ContentMappingCmp] the fields of the form could not be read', error);
                    setFieldsState('error');
                }
            }
        };

        void load();
        return () => {
            cancelled = true;
        };
    }, [client, nodePath, workspace, language]);

    // (b) The properties of the content type, and of the stored files (jnt:file and the file mixin).
    useEffect(() => {
        let cancelled = false;
        const propertiesOf = async (name: string): Promise<PropertyOption[]> => {
            const result = await client.query({query: NODE_TYPE_PROPERTIES, variables: {name, language}, fetchPolicy: 'cache-first'});
            return toPropertyOptions(result.data?.jcr?.nodeTypeByName?.properties ?? []);
        };

        const load = async () => {
            if (!contentType) {
                setTypesState('idle');
                setContentProperties([]);
                return;
            }

            setTypesState('loading');
            try {
                const [content, file, mixin] = await Promise.all([
                    propertiesOf(contentType),
                    propertiesOf('jnt:file'),
                    fileMixin ? propertiesOf(fileMixin) : Promise.resolve([] as PropertyOption[])
                ]);
                if (!cancelled) {
                    setContentProperties(content);
                    const merged = [...file, ...mixin.filter(property => !file.some(other => other.name === property.name))];
                    setFileProperties(merged);
                    setTypesState('ready');
                }
            } catch (error) {
                if (!cancelled) {
                    console.error('[ContentMappingCmp] the properties of the types could not be read', error);
                    setTypesState('error');
                }
            }
        };

        void load();
        return () => {
            cancelled = true;
        };
    }, [client, contentType, fileMixin, language]);

    const rowOf = (target: MappingTarget, property: string) => rows.find(row => row.target === target && row.property === property);

    const commit = (next: MappingRow[]) => onChange(serializeMapping({...doc, rows: next}));
    const without = (target: MappingTarget, property: string) => rows.filter(row => !(row.target === target && row.property === property));

    const setSource = (target: MappingTarget, property: PropertyOption, source: string) => {
        const rest = without(target, property.name);
        if (source === NONE) {
            commit(rest);
            return;
        }

        const row: MappingRow = {target, property: property.name, source: source as MappingSource};
        if (source === 'constant') {
            row.values = [];
        }

        commit([...rest, row]);
    };

    const setField = (target: MappingTarget, property: string, chosen: FormFieldOption) => commit(rows.map(row => (
        row.target === target && row.property === property
            ? {...row, nodeId: chosen.id, fieldKey: chosen.fieldKey ?? '', fieldName: chosen.name}
            : row)));

    const setValues = (target: MappingTarget, property: string, values: string[]) => commit(rows.map(row => (
        row.target === target && row.property === property ? {...row, values} : row)));

    const content = visibleProperties(contentProperties, 'content');
    const files = visibleProperties(fileProperties, 'file');
    const orphans = typesState === 'ready' ? orphanRows(rows, contentProperties, fileProperties) : [];
    const missing = typesState === 'ready'
        ? content.filter(property => property.mandatory && !rowOf('content', property.name)).map(property => property.label)
        : [];
    const brokenFields = fieldsState === 'ready'
        ? rows.filter(row => (row.source === 'field' || row.source === 'files') && (row.fieldName || row.nodeId) && !resolveRowField(row, fields))
        : [];

    const renderRow = (target: MappingTarget, property: PropertyOption) => {
        const row = rowOf(target, property.name);
        const sources = sourcesFor(property, target);
        const sourceOptions: DropdownOption[] = [
            {label: t('contentMapping.sources.none'), value: NONE},
            ...sources.map(source => ({label: t(`contentMapping.sources.${source}`), value: source}))
        ];
        return (
            <div key={`${target}-${property.name}`} className="fmdbxa-contentMappingRow" role="row" data-sel-role="content-mapping-row" data-property={property.name}>
                <div role="cell" className="fmdbxa-contentMappingProperty">
                    <Typography variant="body">{property.mandatory ? `${property.label} *` : property.label}</Typography>
                    <Typography variant="caption" className="fmdbxa-contentMappingMuted">{property.name}</Typography>
                </div>
                <div role="cell">
                    <Dropdown
                        variant="outlined"
                        data={sourceOptions}
                        value={row?.source ?? NONE}
                        isDisabled={readOnly}
                        onChange={(_event, item) => setSource(target, property, item.value ?? NONE)}
                    />
                </div>
                <div role="cell">
                    <ValueCell
                        row={row}
                        multiple={property.multiple}
                        fields={fields}
                        readOnly={readOnly}
                        inputId={`${id}-${target}-${property.name}`}
                        onField={chosen => setField(target, property.name, chosen)}
                        onValues={values => setValues(target, property.name, values)}
                    />
                </div>
            </div>
        );
    };

    const header = (
        <div className="fmdbxa-contentMappingRow fmdbxa-contentMappingHeader" role="row">
            <Typography variant="caption" role="columnheader">{t('contentMapping.columns.property')}</Typography>
            <Typography variant="caption" role="columnheader">{t('contentMapping.columns.source')}</Typography>
            <Typography variant="caption" role="columnheader">{t('contentMapping.columns.value')}</Typography>
        </div>
    );

    if (fieldsState === 'loading') {
        return <Loader size="small"/>;
    }

    return (
        <div className="fmdbxa-contentMapping flexFluid" data-sel-role="content-mapping">
            {fieldsState === 'error' && <Typography variant="caption" className="fmdbxa-contentMappingError">{t('contentMapping.formNotFound')}</Typography>}
            {!contentType && <Typography variant="caption" className="fmdbxa-contentMappingMuted">{t('contentMapping.chooseTypeFirst')}</Typography>}
            {typesState === 'loading' && <Loader size="small"/>}
            {typesState === 'error' && <Typography variant="caption" className="fmdbxa-contentMappingError">{t('contentMapping.typeNotFound', {type: contentType})}</Typography>}

            {typesState === 'ready' && (
                <>
                    <Typography variant="subheading" className="fmdbxa-contentMappingTitle">{t('contentMapping.contentTitle')}</Typography>
                    <div className="fmdbxa-contentMappingGrid" role="table" aria-label={t('contentMapping.contentTitle')}>
                        {header}
                        {content.map(property => renderRow('content', property))}
                    </div>

                    <Typography variant="subheading" className="fmdbxa-contentMappingTitle">{t('contentMapping.filesTitle')}</Typography>
                    {!fileMixin && <Typography variant="caption" className="fmdbxa-contentMappingMuted">{t('contentMapping.fileMixinHint')}</Typography>}
                    <div className="fmdbxa-contentMappingGrid" role="table" aria-label={t('contentMapping.filesTitle')}>
                        {header}
                        {files.map(property => renderRow('file', property))}
                    </div>
                </>
            )}

            {(missing.length > 0 || brokenFields.length > 0) && (
                <div className="flexCol" data-sel-role="content-mapping-warnings">
                    {missing.length > 0 && (
                        <Typography variant="caption" className="fmdbxa-contentMappingWarning">{t('contentMapping.missingMandatory', {properties: missing.join(', ')})}</Typography>
                    )}
                    {brokenFields.map(row => (
                        <Typography key={`${row.target}-${row.property}`} variant="caption" className="fmdbxa-contentMappingError">
                            {t('contentMapping.fieldGone', {property: row.property, name: row.fieldName || row.nodeId})}
                        </Typography>
                    ))}
                </div>
            )}

            {orphans.length > 0 && (
                <div className="flexCol" data-sel-role="content-mapping-orphans">
                    <Typography variant="caption" className="fmdbxa-contentMappingError">{t('contentMapping.orphans')}</Typography>
                    {orphans.map(row => (
                        <div key={`${row.target}-${row.property}`} className="fmdbxa-contentMappingOrphan">
                            <Typography variant="caption">{row.target === 'file' ? `${t('contentMapping.filesTitle')}: ${row.property}` : row.property}</Typography>
                            <Button
                                variant="ghost"
                                icon={<Close/>}
                                title={t('contentMapping.remove')}
                                aria-label={t('contentMapping.remove')}
                                isDisabled={readOnly}
                                onClick={() => commit(without(row.target, row.property))}
                            />
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};

ContentMappingCmp.displayName = 'ContentMappingCmp';
