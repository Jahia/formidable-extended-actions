import {JahiaNode, NodeProperty} from './types';
/**
 * Fixture builders of the accounts and content families (the action nodes of a form), extracted from the Formidable
 * repository's tests/cypress/support/fixtures/actions.ts. Reference material: see tests/README.md.
 */

export interface CreateAccountActionData {
	name?: string;
	emailField?: string;
	passwordField?: string;
	firstNameField?: string;
	lastNameField?: string;
	group?: string;
	signIn?: boolean;
	redirectPageUuid?: string;
	redirectParameter?: string;
	passwordPattern?: string;
	activationRequired?: boolean;
	activationHours?: number;
}

/** The Create account action of formidable-accounts (fmdbacc:createAccountAction); fields are named by their node name. */
export function getCreateAccountActionNode(data: CreateAccountActionData = {}): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'emailField', value: data.emailField ?? 'email'},
		{name: 'passwordField', value: data.passwordField ?? 'password'}
	];
	if (data.firstNameField) properties.push({name: 'firstNameField', value: data.firstNameField});
	if (data.lastNameField) properties.push({name: 'lastNameField', value: data.lastNameField});
	if (data.group) properties.push({name: 'group', value: data.group});
	if (data.signIn !== undefined) properties.push({name: 'signIn', value: String(data.signIn), type: 'BOOLEAN'});
	if (data.redirectPageUuid) properties.push({name: 'redirectPage', value: data.redirectPageUuid, type: 'WEAKREFERENCE'});
	if (data.redirectParameter) properties.push({name: 'redirectParameter', value: data.redirectParameter});
	if (data.passwordPattern) properties.push({name: 'passwordPattern', value: data.passwordPattern});
	if (data.activationRequired !== undefined) properties.push({name: 'activationRequired', value: String(data.activationRequired), type: 'BOOLEAN'});
	if (data.activationHours !== undefined) properties.push({name: 'activationHours', value: String(data.activationHours), type: 'LONG'});

	return {
		name: data.name || 'createAccount',
		primaryNodeType: 'fmdbacc:createAccountAction',
		properties
	};
}

/** The Sign in action of formidable-accounts (fmdbacc:signInAction). */
export function getSignInActionNode(data: {name?: string; loginField?: string; passwordField?: string; redirectParameter?: string} = {}): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'loginField', value: data.loginField ?? 'login'},
		{name: 'passwordField', value: data.passwordField ?? 'password'}
	];
	if (data.redirectParameter) properties.push({name: 'redirectParameter', value: data.redirectParameter});

	return {
		name: data.name || 'signIn',
		primaryNodeType: 'fmdbacc:signInAction',
		properties
	};
}

export interface CreateContentActionData {
	name?: string;
	contentType: string;
	parentFolderUuid: string;
	filesFolderUuid?: string;
	groupPrefix?: string;
	titleField?: string;
	fieldMappings?: string[];
	fileMappings?: string[];
	fileMixin?: string;
	fileProperties?: string[];
	fixedValues?: string[];
	submitterProperty?: string;
	organizationProperty?: string;
	pageProperty?: string;
	requiredPairs?: string[];
	pairRefusalMessage?: string;
	publish?: boolean;
}

/**
 * The Create content action of formidable-content (fmdbcnt:createContentAction). The mapping settings are lists of
 * lines ("formField=property"); the folders are weak references by UUID.
 */
export function getCreateContentActionNode(data: CreateContentActionData): JahiaNode {
	const properties: NodeProperty[] = [
		{name: 'contentType', value: data.contentType},
		{name: 'parentFolder', value: data.parentFolderUuid, type: 'WEAKREFERENCE'}
	];
	if (data.filesFolderUuid) properties.push({name: 'filesFolder', value: data.filesFolderUuid, type: 'WEAKREFERENCE'});
	for (const [name, value] of [
		['groupPrefix', data.groupPrefix],
		['titleField', data.titleField],
		['fileMixin', data.fileMixin],
		['submitterProperty', data.submitterProperty],
		['organizationProperty', data.organizationProperty],
		['pageProperty', data.pageProperty]
	] as Array<[string, string | undefined]>) {
		if (value) properties.push({name, value});
	}
	for (const [name, values] of [
		['fieldMappings', data.fieldMappings],
		['fileMappings', data.fileMappings],
		['fileProperties', data.fileProperties],
		['fixedValues', data.fixedValues],
		['requiredPairs', data.requiredPairs]
	] as Array<[string, string[] | undefined]>) {
		if (values && values.length > 0) properties.push({name, values});
	}
	if (data.pairRefusalMessage) properties.push({name: 'pairRefusalMessage', value: data.pairRefusalMessage, language: 'en'});
	if (data.publish !== undefined) properties.push({name: 'publish', value: String(data.publish), type: 'BOOLEAN'});

	return {
		name: data.name || 'createContent',
		primaryNodeType: 'fmdbcnt:createContentAction',
		properties
	};
}
