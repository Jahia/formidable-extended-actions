/// <reference types="vite/client" />
import {describe, expect, it} from 'vitest';
import en from '../main/resources/javascript/locales/en.json';
import fr from '../main/resources/javascript/locales/fr.json';
import pt from '../main/resources/javascript/locales/pt.json';

/**
 * The selector of formidable-content reads its labels from the i18n namespace of the module (formidable-content), under one key:
 * `contentMapping`. This test holds the locale files together: the same keys in every language, no blank text, and every
 * key a component asks for exists.
 */

const FAMILY = 'contentMapping';

const sources = import.meta.glob('./**/*.tsx', {query: '?raw', import: 'default', eager: true}) as Record<string, string>;

function leafPaths(node: unknown, prefix = ''): string[] {
    if (node !== null && typeof node === 'object') {
        return Object.entries(node).flatMap(([key, value]) => leafPaths(value, prefix ? `${prefix}.${key}` : key));
    }

    return [prefix];
}

function resolve(node: unknown, path: string): unknown {
    return path.split('.').reduce<unknown>((current, key) => (current !== null && typeof current === 'object' ? (current as Record<string, unknown>)[key] : undefined), node);
}

describe('locales of the UI extension', () => {
    it('hold the one key of the module and nothing else', () => {
        expect(Object.keys(en)).toEqual([FAMILY]);
        expect(Object.keys(fr)).toEqual([FAMILY]);
        expect(Object.keys(pt)).toEqual([FAMILY]);
    });

    it('have the same keys in English, in French and in Portuguese', () => {
        expect(leafPaths(fr).sort()).toEqual(leafPaths(en).sort());
        expect(leafPaths(pt).sort()).toEqual(leafPaths(en).sort());
    });

    it('translate the labels of the story sources in French and in Portuguese, not copy them', () => {
        for (const path of ['sources.pageCategories', 'sources.submitterProfile', 'categories', 'selectCategoryField', 'notCategoryField']) {
            expect(resolve(fr, `${FAMILY}.${path}`), `${path} in fr`).not.toBe(resolve(en, `${FAMILY}.${path}`));
            expect(resolve(pt, `${FAMILY}.${path}`), `${path} in pt`).not.toBe(resolve(en, `${FAMILY}.${path}`));
        }

        expect(resolve(en, `${FAMILY}.categories`)).toBe('Categories');
        expect(resolve(fr, `${FAMILY}.categories`)).toBe('Catégories');
        expect(resolve(pt, `${FAMILY}.categories`)).toBe('Categorias');
        expect(resolve(en, `${FAMILY}.sources.submitterProfile`)).toBe("The submitter's profile");
        expect(resolve(fr, `${FAMILY}.sources.submitterProfile`)).toBe("Le profil de l'auteur de l'envoi");
        expect(resolve(pt, `${FAMILY}.sources.submitterProfile`)).toBe('O perfil de quem envia');
    });

    it('have no blank text', () => {
        for (const locale of [en, fr, pt]) {
            for (const path of leafPaths(locale)) {
                expect(String(resolve(locale, path)).trim(), path).not.toBe('');
            }
        }
    });
});

describe('the keys the components ask for', () => {
    const literalKeys = new Set<string>();
    const dynamicPrefixes = new Set<string>();
    for (const code of Object.values(sources)) {
        for (const match of code.matchAll(/\bt\(\s*'([A-Za-z0-9_.]+)'/g)) {
            literalKeys.add(match[1]);
        }

        for (const match of code.matchAll(/\bt\(\s*`([A-Za-z0-9_.]+)\.\$\{/g)) {
            dynamicPrefixes.add(match[1]);
        }
    }

    it('are found in the sources', () => {
        expect(literalKeys.size).toBeGreaterThan(15);
    });

    it('exist in English, in French and in Portuguese', () => {
        for (const key of literalKeys) {
            expect(typeof resolve(en, key), `${key} in en`).toBe('string');
            expect(typeof resolve(fr, key), `${key} in fr`).toBe('string');
            expect(typeof resolve(pt, key), `${key} in pt`).toBe('string');
        }
    });

    it('have an object behind every computed key', () => {
        for (const prefix of dynamicPrefixes) {
            expect(typeof resolve(en, prefix), `${prefix} in en`).toBe('object');
            expect(typeof resolve(fr, prefix), `${prefix} in fr`).toBe('object');
            expect(typeof resolve(pt, prefix), `${prefix} in pt`).toBe('object');
        }
    });

    it('include a label and a description for every source the editor can offer', () => {
        for (const source of ['field', 'files', 'constant', 'submitter', 'organization', 'page', 'pageCategories', 'submitterProfile']) {
            for (const locale of [en, fr, pt]) {
                expect(typeof resolve(locale, `${FAMILY}.sources.${source}`), source).toBe('string');
            }
        }

        for (const source of ['submitter', 'organization', 'page', 'pageCategories', 'submitterProfile']) {
            for (const locale of [en, fr, pt]) {
                expect(typeof resolve(locale, `${FAMILY}.filled.${source}`), source).toBe('string');
            }
        }
    });

    it('use the namespace of the module, and no other', () => {
        for (const code of Object.values(sources)) {
            expect(code).not.toMatch(/formidable-extended-actions/);
            for (const namespace of code.matchAll(/['"`](formidable-[a-z]+)['"`]/g)) {
                expect(namespace[1]).toBe('formidable-content');
            }
        }
        expect(Object.values(sources).join('\n')).toMatch(/formidable-content/);
    });
});
