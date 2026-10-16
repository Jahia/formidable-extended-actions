/** What every selector type of the module reads from the Content Editor: the edited node, its language and workspace. */
export interface EditorContextLike {
    path?: string;
    lang?: string;
    language?: string;
    uilang?: string;
    workspace?: string;
    nodeData?: {path?: string; lang?: string; language?: string; workspace?: string};
}

/** Props the Content Editor hands to a selectorType component. */
export interface SelectorProps {
    field: {name?: string; readOnly?: boolean; node?: {path?: string}; path?: string; nodePath?: string};
    id: string;
    value?: string;
    readOnly?: boolean;
    onChange: (value: string) => void;
    editorContext?: EditorContextLike;
    context?: EditorContextLike;
    form?: {values?: Record<string, unknown>};
}

const editorContextOf = (props: SelectorProps): EditorContextLike | undefined => props.editorContext ?? props.context;

/** The edited node: the action in edit mode, the actions list it is created in otherwise. */
export const currentNodePathOf = (props: SelectorProps): string | undefined => {
    const context = editorContextOf(props);
    return props.field.node?.path ?? props.field.nodePath ?? props.field.path ?? context?.nodeData?.path ?? context?.path;
};

/** The UI language, for labels. */
export const languageOf = (props: SelectorProps): string => {
    const context = editorContextOf(props);
    const fromWindow = (globalThis as unknown as {contextJsParameters?: {uilang?: string}}).contextJsParameters?.uilang;
    return context?.uilang ?? fromWindow ?? context?.nodeData?.language ?? context?.nodeData?.lang ?? context?.language ?? context?.lang ?? 'en';
};

/** The workspace of the edited node (EDIT unless the editor says otherwise). */
export const workspaceOf = (props: SelectorProps): string => {
    const context = editorContextOf(props);
    return context?.nodeData?.workspace ?? context?.workspace ?? 'EDIT';
};

interface FormNode {
    path: string;
    primaryNodeType?: {name?: string | null} | null;
    ancestors?: FormNode[] | null;
}

/** The enclosing form: the fmdb:form ancestor of the edited node, or the node itself. */
export const formPathOf = (node?: FormNode | null): string | undefined => {
    if (node?.primaryNodeType?.name === 'fmdb:form') {
        return node.path;
    }

    return node?.ancestors?.find(ancestor => ancestor.primaryNodeType?.name === 'fmdb:form')?.path;
};
