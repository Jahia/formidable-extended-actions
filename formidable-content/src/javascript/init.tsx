import {registry} from '@jahia/ui-extender';
import i18next from 'i18next';
import {ContentMappingCmp} from './ContentMapping/ContentMappingCmp';

/**
 * The UI extension of formidable-content: registers the ContentMapping selector type, the Content Editor widget that
 * writes the `mapping` document of a Create content action. Its labels live in locales/<lang>.json, in the namespace of
 * the module (formidable-content), under `contentMapping`.
 */
export default function () {
    registry.add('callback', 'FormidableContentEditor', {
        targets: ['jahiaApp-init:20'],
        callback: () => {
            i18next.loadNamespaces('formidable-content');

            registry.add('selectorType', 'ContentMapping', {cmp: ContentMappingCmp, supportMultiple: false});

            console.debug('%c Formidable Content Extensions is activated', 'color: #00a1e0');
        }
    });
}
