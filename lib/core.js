const fs = require('fs');
const path = require('path');

const DEFAULT_EXTENSIONS = new Set(['.js', '.jsx', '.ts', '.tsx', '.mjs', '.cjs', '.mts', '.cts']);
const DEFAULT_IGNORED_DIRS = new Set([
  '.git',
  'node_modules',
  '.next',
  'dist',
  'build',
  'coverage',
  'out',
  'target',
]);

const COLORS = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  slate: '\x1b[90m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
  white: '\x1b[37m',
};

function colorize(enabled, color, value) {
  if (!enabled) {
    return value;
  }
  return `${COLORS[color] || ''}${value}${COLORS.reset}`;
}

function parseArgs(argv) {
  const options = {
    command: 'lint',
    target: '.',
    extensions: [...DEFAULT_EXTENSIONS],
    format: 'pretty',
    quiet: false,
    color: process.stdout.isTTY,
    maxWarnings: null,
    stdin: false,
    stdinFilename: 'stdin.ts',
  };

  const args = [...argv];
  if (args[0] && !args[0].startsWith('-')) {
    options.command = args.shift();
  }
  if (!['lint', 'fix', 'check', 'help', '--help', '-h'].includes(options.command)) {
    options.target = options.command;
    options.command = 'lint';
  }

  while (args.length > 0) {
    const arg = args.shift();
    if (!arg) {
      continue;
    }
    if (arg === '--help' || arg === '-h') {
      options.command = 'help';
    } else if (arg === '--quiet') {
      options.quiet = true;
    } else if (arg === '--json') {
      options.format = 'json';
      options.color = false;
    } else if (arg === '--no-color') {
      options.color = false;
    } else if (arg === '--stdin') {
      options.stdin = true;
    } else if (arg === '--stdin-filename') {
      options.stdinFilename = args.shift() || options.stdinFilename;
    } else if (arg === '--ext') {
      const value = args.shift() || '';
      options.extensions = value.split(',').map(part => part.trim()).filter(Boolean).map(normalizeExtension);
    } else if (arg === '--max-warnings') {
      const value = Number(args.shift());
      options.maxWarnings = Number.isFinite(value) ? value : null;
    } else if (!arg.startsWith('-') && options.target === '.') {
      options.target = arg;
    }
  }

  if (options.command === 'check') {
    options.command = 'lint';
  }
  return options;
}

function normalizeExtension(extension) {
  return extension.startsWith('.') ? extension : `.${extension}`;
}

function collectFiles(target, options) {
  const resolved = path.resolve(target);
  const stat = fs.statSync(resolved);
  if (stat.isFile()) {
    return shouldLintFile(resolved, options.extensions) ? [resolved] : [];
  }

  const files = [];
  walkDirectory(resolved, options, files);
  return files.sort();
}

function walkDirectory(directory, options, files) {
  const entries = fs.readdirSync(directory, { withFileTypes: true });
  for (const entry of entries) {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (DEFAULT_IGNORED_DIRS.has(entry.name)) {
        continue;
      }
      walkDirectory(entryPath, options, files);
      continue;
    }
    if (shouldLintFile(entryPath, options.extensions)) {
      files.push(entryPath);
    }
  }
}

function shouldLintFile(filePath, extensions) {
  const extension = path.extname(filePath).toLowerCase();
  return extensions.includes(extension);
}

function lintText(text, filename, options = {}) {
  const normalizedOptions = {
    extensions: [...DEFAULT_EXTENSIONS],
    ...options,
  };
  const extension = path.extname(filename || normalizedOptions.stdinFilename || '').toLowerCase();
  const isTypeScript = ['.ts', '.tsx', '.mts', '.cts'].includes(extension);
  const lines = splitLines(text);
  const issues = [];
  const importMap = new Map();

  for (const line of lines) {
    issues.push(...findTrailingWhitespace(line, text));
    issues.push(...findLooseEquality(line, text));
    issues.push(...findVarDeclarations(line, text));
    issues.push(...findConsoleCalls(line, text));
    issues.push(...findDebuggerStatements(line, text));
    issues.push(...findEmptyBlocks(line, text));
    issues.push(...findTodoComments(line, text));
    issues.push(...findDuplicateImports(line, text, importMap));
    if (isTypeScript) {
      issues.push(...findExplicitAny(line, text));
    }
  }

  issues.push(...findPreferConstIssues(text, lines));

  return issues.sort(compareIssues).map(issue => enrichIssue(issue, text, filename));
}

function compareIssues(left, right) {
  return left.start - right.start || left.ruleId.localeCompare(right.ruleId);
}

function splitLines(text) {
  const lines = [];
  let offset = 0;
  const rawLines = text.split(/\n/);
  for (let index = 0; index < rawLines.length; index += 1) {
    const rawLine = rawLines[index];
    const content = rawLine.replace(/\r$/, '');
    lines.push({
      number: index + 1,
      text: content,
      start: offset,
      end: offset + content.length,
    });
    offset += rawLine.length + 1;
  }
  // If the file ends without a newline, keep the last span honest.
  if (text.length > 0 && !text.endsWith('\n')) {
    const last = lines[lines.length - 1];
    last.end = text.length;
  }
  return lines;
}

function createIssue({ ruleId, severity, message, start, end, line, column, fix }) {
  return { ruleId, severity, message, start, end, line, column, fix };
}

function findTrailingWhitespace(line, text) {
  const match = /[\t ]+$/.exec(line.text);
  if (!match) {
    return [];
  }
  const start = line.start + match.index;
  const end = line.start + line.text.length;
  return [createIssue({
    ruleId: 'style.trailing-whitespace',
    severity: 'info',
    message: 'Remove trailing whitespace.',
    start,
    end,
    line: line.number,
    column: match.index + 1,
    fix: { start, end, text: '' },
  })];
}

function findLooseEquality(line) {
  const issues = [];
  for (let index = 0; index < line.text.length - 1; index += 1) {
    const pair = line.text.slice(index, index + 2);
    if (pair !== '==' && pair !== '!=') {
      continue;
    }
    const previous = line.text[index - 1] || '';
    const next = line.text[index + 2] || '';
    if (previous === '=' || next === '=' || previous === '<' || previous === '>' || next === '>') {
      continue;
    }
    const replacement = pair === '==' ? '===' : '!==' ;
    issues.push(createIssue({
      ruleId: 'correctness.eqeqeq',
      severity: 'warning',
      message: `Use '${replacement}' instead of '${pair}'.`,
      start: line.start + index,
      end: line.start + index + 2,
      line: line.number,
      column: index + 1,
      fix: { start: line.start + index, end: line.start + index + 2, text: replacement },
    }));
    index += 1;
  }
  return issues;
}

function findVarDeclarations(line) {
  const issues = [];
  const regex = /\bvar\s+([A-Za-z_$][\w$]*)/g;
  let match;
  while ((match = regex.exec(line.text)) !== null) {
    issues.push(createIssue({
      ruleId: 'style.no-var',
      severity: 'warning',
      message: `Avoid 'var' for '${match[1]}'; prefer 'let' or 'const'.`,
      start: line.start + match.index,
      end: line.start + match.index + 3,
      line: line.number,
      column: match.index + 1,
    }));
  }
  return issues;
}

function findConsoleCalls(line) {
  const issues = [];
  const regex = /\bconsole\.(log|debug|info)\b/g;
  let match;
  while ((match = regex.exec(line.text)) !== null) {
    issues.push(createIssue({
      ruleId: 'best-practices.no-console',
      severity: 'warning',
      message: `Avoid console.${match[1]} in production-facing code.`,
      start: line.start + match.index,
      end: line.start + match.index + match[0].length,
      line: line.number,
      column: match.index + 1,
    }));
  }
  return issues;
}

function findDebuggerStatements(line) {
  const issues = [];
  const regex = /\bdebugger\b/g;
  let match;
  while ((match = regex.exec(line.text)) !== null) {
    issues.push(createIssue({
      ruleId: 'best-practices.no-debugger',
      severity: 'error',
      message: 'Remove debugger statements before shipping code.',
      start: line.start + match.index,
      end: line.start + match.index + match[0].length,
      line: line.number,
      column: match.index + 1,
    }));
  }
  return issues;
}

function findEmptyBlocks(line) {
  const issues = [];
  const regex = /\{\s*\}/g;
  let match;
  while ((match = regex.exec(line.text)) !== null) {
    issues.push(createIssue({
      ruleId: 'logic.no-empty-block',
      severity: 'info',
      message: 'Empty blocks usually hide unfinished logic or unnecessary control flow.',
      start: line.start + match.index,
      end: line.start + match.index + match[0].length,
      line: line.number,
      column: match.index + 1,
    }));
  }
  return issues;
}

function findTodoComments(line) {
  const issues = [];
  const regex = /\b(TODO|FIXME)\b/g;
  let match;
  while ((match = regex.exec(line.text)) !== null) {
    issues.push(createIssue({
      ruleId: 'maintainability.todo-comment',
      severity: 'info',
      message: `Track ${match[1]} comments so they do not become stale.`,
      start: line.start + match.index,
      end: line.start + match.index + match[1].length,
      line: line.number,
      column: match.index + 1,
    }));
  }
  return issues;
}

function findExplicitAny(line) {
  const issues = [];
  const patterns = [
    /:\s*any\b/g,
    /\bas\s+any\b/g,
    /<\s*any\s*>/g,
  ];
  for (const regex of patterns) {
    let match;
    while ((match = regex.exec(line.text)) !== null) {
      issues.push(createIssue({
        ruleId: 'typescript.no-explicit-any',
        severity: 'warning',
        message: 'Avoid explicit any; prefer a specific type or unknown.',
        start: line.start + match.index,
        end: line.start + match.index + match[0].length,
        line: line.number,
        column: match.index + 1,
      }));
    }
  }
  return issues;
}

function findDuplicateImports(line, _text, importMap) {
  const issues = [];
  const importMatch = line.text.match(/^\s*import(?:.+?from\s+)?['"]([^'"]+)['"]/);
  if (!importMatch) {
    return issues;
  }
  const source = importMatch[1];
  const first = importMap.get(source);
  if (!first) {
    importMap.set(source, line.number);
    return issues;
  }
  const sourceIndex = line.text.indexOf(source);
  issues.push(createIssue({
    ruleId: 'imports.no-duplicate-imports',
    severity: 'warning',
    message: `Duplicate import from '${source}' (first seen on line ${first}).`,
    start: line.start + sourceIndex,
    end: line.start + sourceIndex + source.length,
    line: line.number,
    column: sourceIndex + 1,
  }));
  return issues;
}

function findPreferConstIssues(text, lines) {
  const issues = [];
  for (const line of lines) {
    const regex = /\blet\s+([A-Za-z_$][\w$]*)\b/g;
    let match;
    while ((match = regex.exec(line.text)) !== null) {
      const name = match[1];
      const declarationStart = line.start + match.index;
      if (isReassignedLater(text, name, declarationStart + match[0].length)) {
        continue;
      }
      issues.push(createIssue({
        ruleId: 'style.prefer-const',
        severity: 'warning',
        message: `Use 'const' for '${name}' because it is never reassigned.`,
        start: declarationStart,
        end: declarationStart + 3,
        line: line.number,
        column: match.index + 1,
        fix: { start: declarationStart, end: declarationStart + 3, text: 'const' },
      }));
    }
  }
  return issues;
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function isReassignedLater(text, identifier, offset) {
  const rest = text.slice(offset);
  const name = escapeRegExp(identifier);
  // Plain "=" gets a tiny guard so "value == 1" does not look like a write.
  const assignment = new RegExp(`\\b${name}\\b\\s*(?:=(?!=)|\\+=|-=|\\*=|/=|%=|&&=|\\|\\|=|\\?\\?=|&=|\\|=|\\^=|<<=|>>=|>>>=)`);
  const update = new RegExp(`(?:\\+\\+|--)\\s*\\b${name}\\b|\\b${name}\\b\\s*(?:\\+\\+|--)`);
  return assignment.test(rest) || update.test(rest);
}

function enrichIssue(issue, text, filename) {
  const lineText = getLineTextAtOffset(text, issue.start);
  const pointerLength = Math.max(1, issue.end - issue.start);
  return {
    ...issue,
    file: filename,
    excerpt: lineText,
    pointer: `${' '.repeat(Math.max(0, issue.column - 1))}${'^'.repeat(pointerLength)}`,
  };
}

function getLineTextAtOffset(text, offset) {
  const start = text.lastIndexOf('\n', offset - 1) + 1;
  const nextNewline = text.indexOf('\n', offset);
  const end = nextNewline === -1 ? text.length : nextNewline;
  return text.slice(start, end).replace(/\r$/, '');
}

function applyFixes(text, issues) {
  const fixes = issues
    .filter(issue => issue.fix)
    .map(issue => issue.fix)
    .sort((left, right) => left.start - right.start || left.end - right.end);

  if (fixes.length === 0) {
    return { output: text, applied: 0 };
  }

  const merged = [];
  for (const fix of fixes) {
    const previous = merged[merged.length - 1];
    // Overlapping fixes usually mean two rules touched the same spot.
    // Keeping the first one makes the output way less surprising.
    if (previous && fix.start < previous.end) {
      continue;
    }
    merged.push(fix);
  }

  let cursor = 0;
  let output = '';
  for (const fix of merged) {
    output += text.slice(cursor, fix.start);
    output += fix.text;
    cursor = fix.end;
  }
  output += text.slice(cursor);
  return { output, applied: merged.length };
}

function summarizeResults(results) {
  const summary = {
    files: results.length,
    errors: 0,
    warnings: 0,
    info: 0,
    fixesApplied: 0,
  };
  for (const result of results) {
    summary.fixesApplied += result.fixesApplied || 0;
    for (const issue of result.issues) {
      if (issue.severity === 'error') {
        summary.errors += 1;
      } else if (issue.severity === 'warning') {
        summary.warnings += 1;
      } else {
        summary.info += 1;
      }
    }
  }
  return summary;
}

function formatPrettyReport(results, options) {
  const summary = summarizeResults(results);
  const lines = [];
  lines.push(colorize(options.color, 'bold', 'fizzylint'));
  lines.push(colorize(options.color, 'slate', `  files  ${summary.files}    errors  ${summary.errors}    warnings  ${summary.warnings}    info  ${summary.info}`));
  lines.push('');

  const visibleResults = options.quiet ? results.filter(result => result.issues.length > 0) : results;
  for (const result of visibleResults) {
    if (result.issues.length === 0) {
      lines.push(`${colorize(options.color, 'slate', relativeToCwd(result.file))}  ${colorize(options.color, 'blue', 'clean')}`);
      continue;
    }
    lines.push(colorize(options.color, 'white', relativeToCwd(result.file)));
    for (const issue of result.issues) {
      const severityColor = issue.severity === 'error' ? 'red' : issue.severity === 'warning' ? 'yellow' : 'cyan';
      lines.push(`  ${String(issue.line).padStart(4)}:${String(issue.column).padEnd(3)}  ${colorize(options.color, severityColor, issue.severity.padEnd(7))}  ${issue.ruleId}`);
      lines.push(colorize(options.color, 'slate', `         ${issue.message}`));
      lines.push(`         ${issue.excerpt}`);
      lines.push(colorize(options.color, severityColor, `         ${issue.pointer}`));
    }
    lines.push('');
  }

  if (summary.fixesApplied > 0) {
    lines.push(colorize(options.color, 'blue', `applied fixes: ${summary.fixesApplied}`));
  }

  return lines.join('\n').trimEnd();
}

function formatJsonReport(results) {
  return JSON.stringify({
    summary: summarizeResults(results),
    results,
  }, null, 2);
}

function relativeToCwd(filePath) {
  return path.relative(process.cwd(), filePath) || path.basename(filePath);
}

function lintFiles(files, options) {
  return files.map(file => {
    const text = fs.readFileSync(file, 'utf8');
    const issues = lintText(text, file, options);
    return { file, issues, fixesApplied: 0 };
  });
}

function fixFiles(files, options) {
  return files.map(file => {
    const text = fs.readFileSync(file, 'utf8');
    const issues = lintText(text, file, options);
    const fixResult = applyFixes(text, issues);
    if (fixResult.applied > 0 && fixResult.output !== text) {
      fs.writeFileSync(file, fixResult.output, 'utf8');
    }
    const postFixIssues = lintText(fixResult.output, file, options);
    return { file, issues: postFixIssues, fixesApplied: fixResult.applied };
  });
}

function lintStdin(text, options) {
  const filename = path.resolve(options.stdinFilename);
  const issues = lintText(text, filename, options);
  if (options.command === 'fix') {
    const fixResult = applyFixes(text, issues);
    return {
      stdout: fixResult.output,
      results: [{ file: filename, issues: lintText(fixResult.output, filename, options), fixesApplied: fixResult.applied }],
    };
  }
  return { stdout: null, results: [{ file: filename, issues, fixesApplied: 0 }] };
}

function printHelp() {
  return [
    'fizzylint',
    '',
    'Usage',
    '  fizzylint lint [target] [--json] [--quiet] [--ext .js,.ts,.tsx]',
    '  fizzylint fix [target] [--ext .js,.ts,.tsx]',
    '  fizzylint lint --stdin --stdin-filename src/example.ts',
    '',
    'Commands',
    '  lint     scan files and report issues',
    '  fix      apply safe fixes and print the remaining issues',
    '  help     show this message',
    '',
    'Flags',
    '  --json            emit machine-readable output',
    '  --quiet           only print files with issues',
    '  --no-color        disable ANSI colors',
    '  --ext             override file extensions to scan',
    '  --stdin           lint content from stdin',
    '  --stdin-filename  treat stdin as this filename for rule selection',
    '  --max-warnings    fail when warnings exceed this count',
  ].join('\n');
}

module.exports = {
  DEFAULT_EXTENSIONS,
  applyFixes,
  collectFiles,
  fixFiles,
  formatJsonReport,
  formatPrettyReport,
  lintFiles,
  lintStdin,
  lintText,
  parseArgs,
  printHelp,
  summarizeResults,
};
