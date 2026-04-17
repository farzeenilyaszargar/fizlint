const test = require('node:test');
const assert = require('node:assert/strict');

const {
  applyFixes,
  formatReport,
  lintText,
  parseArgs,
  summarizeResults,
} = require('../lib/core');

test('parseArgs keeps the CLI really small', () => {
  assert.deepEqual(parseArgs([]), { command: 'lint', target: '.', color: process.stdout.isTTY });
  assert.equal(parseArgs(['fix', 'src']).command, 'fix');
  assert.equal(parseArgs(['src']).target, 'src');
  assert.equal(parseArgs(['help']).command, 'help');
});

test('lintText catches the simple classroom-size rules', () => {
  const source = [
    'var total = 1;  ',
    'if (total == 1) { console.log(total); }',
    'debugger;',
  ].join('\n');

  const rules = lintText(source, 'demo.js').map(issue => issue.rule);

  assert.ok(rules.includes('no-var'));
  assert.ok(rules.includes('trailing-whitespace'));
  assert.ok(rules.includes('eqeqeq'));
  assert.ok(rules.includes('no-console'));
  assert.ok(rules.includes('no-debugger'));
});

test('applyFixes only changes the safe fix rules', () => {
  const source = 'if (count == 1) {  \n  return count;\n}\n';
  const fixed = applyFixes(source, lintText(source, 'demo.js'));

  assert.equal(fixed.applied, 2);
  assert.equal(fixed.output.includes('==='), true);
  assert.equal(fixed.output.includes('{  \n'), false);
});

test('formatReport prints a compact summary', () => {
  const report = formatReport([{ file: 'clean.js', issues: [], fixed: 0 }], { color: false });
  assert.ok(report.includes('fizzylint'));
  assert.ok(report.includes('clean'));
});

test('summarizeResults counts levels', () => {
  const summary = summarizeResults([
    { file: 'demo.js', issues: lintText('debugger;\n', 'demo.js'), fixed: 1 },
  ]);

  assert.equal(summary.errors, 1);
  assert.equal(summary.fixed, 1);
});
