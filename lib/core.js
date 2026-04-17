const fs = require('fs');
const path = require('path');

const EXTENSIONS = ['.js', '.jsx', '.ts', '.tsx'];
const IGNORED_DIRS = new Set(['.git', 'node_modules', '.next', 'dist', 'build', 'coverage']);

const COLORS = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  gray: '\x1b[90m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
};

function paint(enabled, color, text) {
  if (!enabled) {
    return text;
  }
  return `${COLORS[color] || ''}${text}${COLORS.reset}`;
}

function parseArgs(argv) {
  const options = {
    command: 'lint',
    target: '.',
    color: process.stdout.isTTY,
  };

  const args = [...argv];
  if (args[0] && !args[0].startsWith('-')) {
    options.command = args.shift();
  }

  if (!['lint', 'fix', 'help', '--help', '-h'].includes(options.command)) {
    options.target = options.command;
    options.command = 'lint';
  }

  while (args.length > 0) {
    const arg = args.shift();
    if (arg === '--help' || arg === '-h') {
      options.command = 'help';
    } else if (arg === '--no-color') {
      options.color = false;
    } else if (!arg.startsWith('-') && options.target === '.') {
      options.target = arg;
    }
  }

  return options;
}

function collectFiles(target) {
  const resolved = path.resolve(target);
  const stat = fs.statSync(resolved);

  if (stat.isFile()) {
    return shouldLintFile(resolved) ? [resolved] : [];
  }

  const files = [];
  walk(resolved, files);
  return files.sort();
}

function walk(directory, files) {
  const entries = fs.readdirSync(directory, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);

    if (entry.isDirectory()) {
      if (!IGNORED_DIRS.has(entry.name)) {
        walk(fullPath, files);
      }
      continue;
    }

    if (shouldLintFile(fullPath)) {
      files.push(fullPath);
    }
  }
}

function shouldLintFile(filePath) {
  return EXTENSIONS.includes(path.extname(filePath).toLowerCase());
}

function splitLines(text) {
  const lines = [];
  let offset = 0;

  for (const rawLine of text.split('\n')) {
    const lineText = rawLine.replace(/\r$/, '');
    lines.push({
      text: lineText,
      start: offset,
      end: offset + lineText.length,
    });
    offset += rawLine.length + 1;
  }

  return lines;
}

function makeIssue(rule, level, message, lineNumber, column, start, end, fix) {
  return { rule, level, message, line: lineNumber, column, start, end, fix };
}

function lintText(text, filename) {
  const issues = [];
  const lines = splitLines(text);

  lines.forEach((line, index) => {
    const lineNumber = index + 1;
    issues.push(...findTrailingWhitespace(line, lineNumber));
    issues.push(...findLooseEquality(line, lineNumber));
    issues.push(...findVarUsage(line, lineNumber));
    issues.push(...findConsoleUsage(line, lineNumber));
    issues.push(...findDebuggerUsage(line, lineNumber));
  });

  return issues
    .sort((a, b) => a.start - b.start || a.rule.localeCompare(b.rule))
    .map(issue => addPreview(issue, text, filename));
}

function findTrailingWhitespace(line, lineNumber) {
  const match = /[\t ]+$/.exec(line.text);
  if (!match) {
    return [];
  }

  const start = line.start + match.index;
  const end = line.start + line.text.length;
  return [makeIssue(
    'trailing-whitespace',
    'info',
    'Remove the trailing whitespace.',
    lineNumber,
    match.index + 1,
    start,
    end,
    { start, end, text: '' },
  )];
}

function findLooseEquality(line, lineNumber) {
  const issues = [];

  for (let i = 0; i < line.text.length - 1; i += 1) {
    const pair = line.text.slice(i, i + 2);
    if (pair !== '==' && pair !== '!=') {
      continue;
    }

    const before = line.text[i - 1] || '';
    const after = line.text[i + 2] || '';
    if (before === '=' || after === '=') {
      continue;
    }

    const replacement = pair === '==' ? '===' : '!==';
    const start = line.start + i;
    issues.push(makeIssue(
      'eqeqeq',
      'warning',
      `Use '${replacement}' instead of '${pair}'.`,
      lineNumber,
      i + 1,
      start,
      start + 2,
      { start, end: start + 2, text: replacement },
    ));
    i += 1;
  }

  return issues;
}

function findVarUsage(line, lineNumber) {
  const issues = [];
  const regex = /\bvar\b/g;
  let match;

  while ((match = regex.exec(line.text)) !== null) {
    const start = line.start + match.index;
    issues.push(makeIssue(
      'no-var',
      'warning',
      "Avoid 'var'. Prefer 'let' or 'const'.",
      lineNumber,
      match.index + 1,
      start,
      start + 3,
      null,
    ));
  }

  return issues;
}

function findConsoleUsage(line, lineNumber) {
  const issues = [];
  const regex = /\bconsole\.(log|debug|info)\b/g;
  let match;

  while ((match = regex.exec(line.text)) !== null) {
    const start = line.start + match.index;
    issues.push(makeIssue(
      'no-console',
      'warning',
      `Avoid console.${match[1]} in final code.`,
      lineNumber,
      match.index + 1,
      start,
      start + match[0].length,
      null,
    ));
  }

  return issues;
}

function findDebuggerUsage(line, lineNumber) {
  const issues = [];
  const regex = /\bdebugger\b/g;
  let match;

  while ((match = regex.exec(line.text)) !== null) {
    const start = line.start + match.index;
    issues.push(makeIssue(
      'no-debugger',
      'error',
      'Remove debugger before shipping.',
      lineNumber,
      match.index + 1,
      start,
      start + match[0].length,
      null,
    ));
  }

  return issues;
}

function addPreview(issue, text, filename) {
  const lineStart = text.lastIndexOf('\n', issue.start - 1) + 1;
  const nextBreak = text.indexOf('\n', issue.start);
  const lineEnd = nextBreak === -1 ? text.length : nextBreak;
  const preview = text.slice(lineStart, lineEnd).replace(/\r$/, '');
  const width = Math.max(1, issue.end - issue.start);

  return {
    ...issue,
    file: filename,
    preview,
    marker: `${' '.repeat(Math.max(0, issue.column - 1))}${'^'.repeat(width)}`,
  };
}

function applyFixes(text, issues) {
  const fixes = issues
    .filter(issue => issue.fix)
    .map(issue => issue.fix)
    .sort((a, b) => a.start - b.start);

  let output = '';
  let cursor = 0;
  let applied = 0;

  for (const fix of fixes) {
    if (fix.start < cursor) {
      continue;
    }
    output += text.slice(cursor, fix.start);
    output += fix.text;
    cursor = fix.end;
    applied += 1;
  }

  output += text.slice(cursor);
  return { output, applied };
}

function lintFiles(files) {
  return files.map(file => {
    const text = fs.readFileSync(file, 'utf8');
    return { file, issues: lintText(text, file), fixed: 0 };
  });
}

function fixFiles(files) {
  return files.map(file => {
    const text = fs.readFileSync(file, 'utf8');
    const issues = lintText(text, file);
    const fixed = applyFixes(text, issues);

    if (fixed.applied > 0 && fixed.output !== text) {
      fs.writeFileSync(file, fixed.output, 'utf8');
    }

    return { file, issues: lintText(fixed.output, file), fixed: fixed.applied };
  });
}

function summarizeResults(results) {
  const summary = { files: results.length, errors: 0, warnings: 0, info: 0, fixed: 0 };

  for (const result of results) {
    summary.fixed += result.fixed || 0;
    for (const issue of result.issues) {
      if (issue.level === 'error') {
        summary.errors += 1;
      } else if (issue.level === 'warning') {
        summary.warnings += 1;
      } else {
        summary.info += 1;
      }
    }
  }

  return summary;
}

function formatReport(results, options) {
  const summary = summarizeResults(results);
  const lines = [];

  lines.push(paint(options.color, 'bold', 'fizzylint'));
  lines.push(paint(options.color, 'gray', `files ${summary.files}  errors ${summary.errors}  warnings ${summary.warnings}  info ${summary.info}`));
  lines.push('');

  for (const result of results) {
    if (result.issues.length === 0) {
      lines.push(`${paint(options.color, 'blue', relative(result.file))}  clean`);
      continue;
    }

    lines.push(paint(options.color, 'blue', relative(result.file)));
    for (const issue of result.issues) {
      const tone = issue.level === 'error' ? 'red' : issue.level === 'warning' ? 'yellow' : 'gray';
      lines.push(`  ${issue.line}:${issue.column}  ${paint(options.color, tone, issue.level)}  ${issue.rule}`);
      lines.push(`  ${issue.message}`);
      lines.push(`  ${issue.preview}`);
      lines.push(`  ${paint(options.color, tone, issue.marker)}`);
    }
    lines.push('');
  }

  if (summary.fixed > 0) {
    lines.push(paint(options.color, 'gray', `fixed ${summary.fixed} issue(s)`));
  }

  return lines.join('\n').trimEnd();
}

function relative(filePath) {
  return path.relative(process.cwd(), filePath) || path.basename(filePath);
}

function printHelp() {
  return [
    'fizzylint',
    '',
    'Usage',
    '  fizzylint',
    '  fizzylint lint [target]',
    '  fizzylint fix [target]',
    '  fizzylint help',
    '',
    'Flags',
    '  --no-color',
  ].join('\n');
}

module.exports = {
  applyFixes,
  collectFiles,
  fixFiles,
  formatReport,
  lintFiles,
  lintText,
  parseArgs,
  printHelp,
  summarizeResults,
};
