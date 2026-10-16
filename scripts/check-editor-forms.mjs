// Every property of an action whose editor this module lays out is placed exactly once.
// A Content Editor override places only the fields it lists; a property it forgets stays where the editor generated it,
// in silence (Formidable core, docs/architecture/content-editor-layout.md). So each forms/*.json declaring the
// actionSettings section must list every property its node type declares in definitions.cnd, but jcr:title (kept first
// in Content) and the hidden ones, and nothing else. Run by the build: `node scripts/check-editor-forms.mjs`.
import {readFileSync, readdirSync} from 'node:fs';
import {join} from 'node:path';

const META = 'src/main/resources/META-INF';
const SECTION = 'actionSettings';
const own = new Map();
let current = null;
for (const raw of readFileSync(join(META, 'definitions.cnd'), 'utf8').split('\n')) {
    const line = raw.replace(/\/\/.*$/, '');
    const header = line.match(/^\[([\w]+:[\w]+)]/);
    if (header) {
        current = header[1];
        own.set(current, []);
        continue;
    }
    const property = current && line.match(/^\s*-\s*([\w:]+)\s*\((.*)$/);
    if (property) {
        const attributes = property[2].slice(property[2].indexOf(')') + 1).replace(/'[^']*'/g, '');
        if (!/\bhidden\b/.test(attributes) && property[1] !== 'jcr:title') {
            own.get(current).push(property[1]);
        }
    }
}

const errors = [];
const formsDir = join(META, 'jahia-content-editor-forms', 'forms');
for (const file of readdirSync(formsDir).filter(name => name.endsWith('.json'))) {
    const form = JSON.parse(readFileSync(join(formsDir, file), 'utf8'));
    if (!(form.sections ?? []).some(section => section.name === SECTION)) {
        continue;
    }
    const expected = own.get(form.nodeType);
    if (!expected) {
        errors.push(`${file}: ${form.nodeType} is not declared in definitions.cnd`);
        continue;
    }
    const listed = new Map();
    for (const section of form.sections) {
        for (const fieldSet of section.fieldSets ?? []) {
            for (const field of fieldSet.fields ?? []) {
                listed.set(field.name, (listed.get(field.name) ?? 0) + 1);
            }
        }
    }
    for (const name of expected) {
        if (!listed.has(name)) errors.push(`${file}: ${name} is not placed`);
    }
    for (const [name, count] of listed) {
        if (!expected.includes(name)) errors.push(`${file}: ${name} is not a property of ${form.nodeType}`);
        if (count > 1) errors.push(`${file}: ${name} is placed ${count} times`);
    }
}
if (errors.length) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log('every property of the laid-out actions is placed once');
