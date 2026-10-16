// Every property of an action whose editor a module lays out is placed exactly once.
// A Content Editor override places only the fields it lists; a property it forgets stays where the editor generated it,
// in silence (Formidable core, docs/architecture/content-editor-layout.md). So each forms/*.json declaring the
// actionSettings section must list every property its node type declares in THE SAME MODULE's definitions.cnd, but
// jcr:title (kept first in Content) and the hidden ones, and nothing else.
//
//   node scripts/check-editor-forms.mjs                       every module of the repository (formidable-*/)
//   node scripts/check-editor-forms.mjs formidable-accounts   one module, by directory name or path
//
// Maven runs it for each module (validate phase), so a module is built and checked on its own.
import {existsSync, readFileSync, readdirSync} from 'node:fs';
import {basename, dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const REPOSITORY = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SECTION = 'actionSettings';

/** The properties of each node type declared by a definitions.cnd, without jcr:title and the hidden ones. */
function declaredProperties(cndFile) {
    const own = new Map();
    let current = null;
    for (const raw of readFileSync(cndFile, 'utf8').split('\n')) {
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
    return own;
}

/** The problems of one module, each prefixed with the module's name. Returns the number of forms it checked, too. */
function checkModule(moduleDir) {
    const name = basename(moduleDir);
    const meta = join(moduleDir, 'src', 'main', 'resources', 'META-INF');
    const formsDir = join(meta, 'jahia-content-editor-forms', 'forms');
    const errors = [];
    let checked = 0;
    if (!existsSync(formsDir)) {
        return {errors, checked};
    }
    const cnd = join(meta, 'definitions.cnd');
    if (!existsSync(cnd)) {
        return {errors: [`${name}: has forms but no META-INF/definitions.cnd`], checked};
    }
    const own = declaredProperties(cnd);
    for (const file of readdirSync(formsDir).filter(entry => entry.endsWith('.json'))) {
        const form = JSON.parse(readFileSync(join(formsDir, file), 'utf8'));
        if (!(form.sections ?? []).some(section => section.name === SECTION)) {
            continue;
        }
        checked++;
        const expected = own.get(form.nodeType);
        if (!expected) {
            errors.push(`${name}/${file}: ${form.nodeType} is not declared in the definitions.cnd of ${name}`);
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
        for (const property of expected) {
            if (!listed.has(property)) errors.push(`${name}/${file}: ${property} is not placed`);
        }
        for (const [property, count] of listed) {
            if (!expected.includes(property)) errors.push(`${name}/${file}: ${property} is not a property of ${form.nodeType}`);
            if (count > 1) errors.push(`${name}/${file}: ${property} is placed ${count} times`);
        }
    }
    return {errors, checked};
}

const arguments_ = process.argv.slice(2);
const modules = arguments_.length > 0
    ? arguments_.map(argument => resolve(process.cwd(), argument))
    : readdirSync(REPOSITORY, {withFileTypes: true})
        .filter(entry => entry.isDirectory() && /^formidable-/.test(entry.name))
        .map(entry => join(REPOSITORY, entry.name));

const errors = [];
let checked = 0;
for (const moduleDir of modules) {
    const result = checkModule(moduleDir);
    errors.push(...result.errors);
    checked += result.checked;
}
if (errors.length) {
    console.error(errors.join('\n'));
    process.exit(1);
}
console.log(`every property of the ${checked} laid-out action(s) of ${modules.map(moduleDir => basename(moduleDir)).join(', ')} is placed once`);
