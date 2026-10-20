import {gql} from '@apollo/client';

/**
 * The edited node and its enclosing form: the action under <form>/actions/<action> in edit mode, the actions list in
 * create mode; the fmdb:form ancestor is the form in both.
 */
export const CURRENT_NODE_BY_PATH = gql`
    query ContentMappingCurrentNode($path: String!, $workspace: Workspace!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                uuid
                path
                primaryNodeType { name }
                ancestors(fieldFilter: {filters: [{fieldName: "primaryNodeType.name", value: "fmdb:form"}]}) {
                    uuid
                    path
                    primaryNodeType { name }
                }
            }
        }
    }
`;

/**
 * Every form element of the form, with what decides whether it submits a value, whether it is an upload and whether it
 * is a choice field in category mode (the mixin and a root category).
 */
export const FORM_FIELDS_BY_PATH = gql`
    query ContentMappingFormFields($path: String!, $workspace: Workspace!, $language: String!) {
        jcr(workspace: $workspace) {
            nodeByPath(path: $path) {
                uuid
                descendants(typesFilter: {types: ["fmdbmix:formElement"], multi: ANY}) {
                    nodes {
                        uuid
                        name
                        displayName(language: $language)
                        isNonSubmittable: isNodeType(type: {types: ["fmdbmix:nonSubmittable"]})
                        isFileField: isNodeType(type: {types: ["fmdbmix:fileField"]})
                        isCategoryField: isNodeType(type: {types: ["fmdbmix:categoryOptions"]})
                        properties(names: ["fieldKey", "optionsRootCategory"]) {
                            name
                            value
                        }
                    }
                }
            }
        }
    }
`;

/** The properties of a node type (its own and inherited), as the platform defines them. */
export const NODE_TYPE_PROPERTIES = gql`
    query ContentMappingTypeProperties($name: String!, $language: String!) {
        jcr {
            nodeTypeByName(name: $name) {
                name
                properties {
                    name
                    displayName(language: $language)
                    requiredType
                    multiple
                    mandatory
                    protected
                    hidden
                }
            }
        }
    }
`;
