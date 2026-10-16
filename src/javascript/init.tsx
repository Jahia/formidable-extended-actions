import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {SalesforceLeadMappingCmp} from './LeadMapping/SalesforceLeadMappingCmp';
import {HubspotContactMappingCmp} from './ContactMapping/HubspotContactMappingCmp';
import {EfficyOpportunityMappingCmp} from './OpportunityMapping/EfficyOpportunityMappingCmp';
import {ContentMappingCmp} from './ContentMapping/ContentMappingCmp';

/**
 * The one UI extension of formidable-extended-actions: registers the Content Editor selector types: one per CRM family, and the mapping of Create content.
 * The labels of the three selectors live in locales/<lang>.json, in the namespace of the module, each under its own key
 * (leadMapping, contactMapping, opportunityMapping).
 */
export default function () {
    registry.add('callback', 'FormidableExtendedActionsEditor', {
        targets: ['jahiaApp-init:20'],
        callback: () => {
            i18next.loadNamespaces('formidable-extended-actions');

            registry.add('selectorType', 'SalesforceLeadMapping', {cmp: SalesforceLeadMappingCmp, supportMultiple: true});
            registry.add('selectorType', 'HubspotContactMapping', {cmp: HubspotContactMappingCmp, supportMultiple: true});
            registry.add('selectorType', 'EfficyOpportunityMapping', {cmp: EfficyOpportunityMappingCmp, supportMultiple: true});
            registry.add('selectorType', 'ContentMapping', {cmp: ContentMappingCmp, supportMultiple: false});

            console.debug('%c Formidable Extended Actions: Salesforce, HubSpot, Efficy and content mapping selectors are activated', 'color: #00a1e0');
        }
    });
}
