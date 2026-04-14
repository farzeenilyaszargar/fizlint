const test = require('node:test');
const assert = require('node:assert/strict');

const {
  applyFixes,
  formatPrettyReport,
  lintText,
  parseArgs,
  summarizeResults,
} = require('../lib/core');

test('parseArgs supports fix mode and common flags', () => {
  const options = parseArgs(['fix', 'src', '--quiet', '--ext', '.js,.ts', '--max-warnings', '3']);
  assert.equal(options.command, 'fix');
  assert.equal(options.target, 'src');
  assert.equal(options.quiet, true);
  assert.deepEqual(options.extensions, ['.js', '.ts']);
  assert.equal(options.maxWarnings, 3);
});

test('lintText reports JS and TS focused rules', () => {
  const source = [
    "import { a } from './x';",
    "import { b } from './x';",
    'let count = 1;  ',
    'if (count == 1) { console.log(count); }',
    'debugger;',
    'const payload: any = count;',
    '// TODO tighten this flow',
    '',
  ].join('\n');

  const issues = lintText(source, 'sample.ts');
  const ids = issues.map(issue => issue.ruleId);

  assert.ok(ids.includes('imports.no-duplicate-imports'));
  assert.ok(ids.includes('style.prefer-const'));
  assert.ok(ids.includes('style.trailing-whitespace'));
  assert.ok(ids.includes('correctness.eqeqeq'));
  assert.ok(ids.includes('best-practices.no-console'));
  assert.ok(ids.includes('best-practices.no-debugger'));
  assert.ok(ids.includes('typescript.no-explicit-any'));
  assert.ok(ids.includes('maintainability.todo-comment'));
});

test('applyFixes upgrades loose equality, trailing whitespace, and let to const', () => {
  const source = 'let count = 1;  \nif (count == 1) {\n  return count;\n}\n';
  const issues = lintText(source, 'demo.ts');
  const fixed = applyFixes(source, issues);

  assert.equal(fixed.applied > 0, true);
  assert.equal(fixed.output.includes('const count = 1;'), true);
  assert.equal(fixed.output.includes('count === 1'), true);
  assert.equal(fixed.output.includes('1;  '), false);
});

test('pretty formatter prints a minimal summary', () => {
  const issues = lintText('const value = 1;\n', 'clean.js');
  const report = formatPrettyReport([{ file: 'clean.js', issues, fixesApplied: 0 }], { color: false, quiet: false });
  assert.ok(report.includes('fizzylint'));
  assert.ok(report.includes('files'));
});

test('summarizeResults counts severities and fixes', () => {
  const issues = lintText('debugger;\n', 'broken.js');
  const summary = summarizeResults([{ file: 'broken.js', issues, fixesApplied: 2 }]);
  assert.equal(summary.errors, 1);
  assert.equal(summary.fixesApplied, 2);
});
