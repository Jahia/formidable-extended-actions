// @ts-check
// Same rule set as the Formidable repository (JS/TS recommended, @eslint-react recommended-typescript), reduced to what
// this module lints: src/javascript. The generated federation bundle under src/main/resources/javascript/apps is ignored.
import {defineConfig} from 'eslint/config';
import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';
import globals from 'globals';
import eslintReact from '@eslint-react/eslint-plugin';

export default defineConfig(
    {
        languageOptions: {
            globals: {...globals.browser, ...globals.node}
        }
    },
    {
        ignores: ['src/main/resources/javascript/apps/**', 'node_modules/**', 'target/**', 'node/**', 'tests/**']
    },

    // JS/TS recommended
    eslint.configs.recommended,
    {files: ['**/*.ts', '**/*.tsx'], extends: tseslint.configs.recommended},

    // React
    eslintReact.configs['recommended-typescript'],
    {
        rules: {
            // We know what we're doing
            '@eslint-react/dom/no-dangerously-set-innerhtml': 'off'
        }
    }
);
