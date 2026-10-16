/// <reference types="vite/client" />
import {describe, expect, it} from 'vitest';
import en from '../main/resources/javascript/locales/en.json';
import fr from '../main/resources/javascript/locales/fr.json';

/**
 * The selector of formidable-salesforce reads its labels from the i18n namespace of the module (formidable-salesforce), under one key:
 * `leadMapping`. This test holds the locale files together: the same keys in every language, no blank text, and every
 * key a component asks for exists.
 */

const FAMILY = 'leadMapping';

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
    });

    it('have the same keys in English and in French', () => {
        expect(leafPaths(fr).sort()).toEqual(leafPaths(en).sort());
    });

    it('have no blank text', () => {
        for (const locale of [en, fr]) {
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

    it('exist in English and in French', () => {
        for (const key of literalKeys) {
            expect(typeof resolve(en, key), `${key} in en`).toBe('string');
            expect(typeof resolve(fr, key), `${key} in fr`).toBe('string');
        }
    });

    it('have an object behind every computed key', () => {
        for (const prefix of dynamicPrefixes) {
            expect(typeof resolve(en, prefix), `${prefix} in en`).toBe('object');
            expect(typeof resolve(fr, prefix), `${prefix} in fr`).toBe('object');
        }
    });

    it('use the namespace of the module, and no other', () => {
        for (const code of Object.values(sources)) {
            expect(code).not.toMatch(/formidable-extended-actions/);
            for (const namespace of code.matchAll(/['"`](formidable-[a-z]+)['"`]/g)) {
                expect(namespace[1]).toBe('formidable-salesforce');
            }
        }
        expect(Object.values(sources).join('\n')).toMatch(/formidable-salesforce/);
    });
});
