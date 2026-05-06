const fs = require('fs');
const path = require('path');

const EXTENSIONS = ['.js', '.jsx', '.ts', '.tsx'];
const IGNORED_DIRS = ['.git', 'node_modules', '.next', 'dist', 'build', 'coverage'];

const COLORS = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  gray: '\x1b[90m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
};

const MASCOT = [
  '   ▄████▄',
  '  ████████',
  ' ███  ██  █',
  ' ██████████',
  ' ██████████',
  ' ▀███▀▀███▀',
];

// for coloring lines only
function paint(enabled, color, text)
{
  if (!enabled)
  {
    return text;
  }

  return `${COLORS[color] || ''}${text}${COLORS.reset}`;
}

// parsing cli args
function parseArgs(argv)
{
  const options = {
    command: 'lint',
    target: '.',
    color: process.stdout.isTTY,
  };

  const args = [...argv];

  if (args[0] && !args[0].startsWith('-'))
  {
    options.command = args.shift();
  }

  if (!['lint', 'fix', 'help', '--help', '-h'].includes(options.command))
  {
    options.target = options.command;
    options.command = 'lint';
  }

  while (args.length > 0)
  {
    const arg = args.shift();

    if (arg === '--help' || arg === '-h')
    {
      options.command = 'help';
    }
    else if (arg === '--no-color')
    {
      options.color = false;
    }
    else if (!arg.startsWith('-') && options.target === '.')
    {
      options.target = arg;
    }
  }

  return options;
}

// file collection checking for folder or file 
function collectFiles(target)
{
  const resolvedPath = path.resolve(target);
  const stats = fs.statSync(resolvedPath);

  if (stats.isFile())
  {
    if (shouldLintFile(resolvedPath))
    {
      return [resolvedPath];
    }

    return [];
  }

  const files = [];
  walkFolder(resolvedPath, files);
  return files.sort();
}

// checking if files are relevant for linting (checks .js or .ts)
function walkFolder(folderPath, files)
{
  const entries = fs.readdirSync(folderPath, { withFileTypes: true });

  for (const entry of entries)
  {
    const fullPath = path.join(folderPath, entry.name);

    if (entry.isDirectory())
    {
      if (!IGNORED_DIRS.includes(entry.name))
      {
        walkFolder(fullPath, files);
      }

      continue;
    }

    if (shouldLintFile(fullPath))
    {
      files.push(fullPath);
    }
  }
}

// bool return here for if to lint or not
function shouldLintFile(filePath)
{
  const extension = path.extname(filePath).toLowerCase();
  return EXTENSIONS.includes(extension);
}

function splitLines(text)
{
  const lines = [];
  let currentOffset = 0;
  const rawLines = text.split('\n');

  for (let i = 0; i < rawLines.length; i += 1)
  {
    const lineText = rawLines[i].replace(/\r$/, '');

    lines.push({
      text: lineText,
      start: currentOffset,
      end: currentOffset + lineText.length,
    });

    currentOffset += rawLines[i].length + 1;
  }

  return lines;
}


function makeIssue(rule, level, message, line, column, start, end, fix)
{
  return {
    rule,
    level,
    message,
    line,
    column,
    start,
    end,
    fix,
  };
}

// main lint function yahi hai, baaki sab helpers isko support karte hain
function lintText(text, filename)
{
  const issues = [];
  const lines = splitLines(text);

  for (let i = 0; i < lines.length; i += 1)
  {
    const line = lines[i];
    const lineNumber = i + 1;

    issues.push(...findTrailingWhitespace(line, lineNumber));
    issues.push(...findLooseEquality(line, lineNumber));
    issues.push(...findVarUsage(line, lineNumber));
    issues.push(...findConsoleUsage(line, lineNumber));
    issues.push(...findDebuggerUsage(line, lineNumber));
  }

  issues.sort(function(a, b)
  {
    if (a.start !== b.start)
    {
      return a.start - b.start;
    }

    return a.rule.localeCompare(b.rule);
  });

  return issues.map(function(issue)
  {
    return addPreview(issue, text, filename);
  });
}

// end line whitespaces to be e removed
function findTrailingWhitespace(line, lineNumber)
{
  const match = /[\t ]+$/.exec(line.text);

  if (!match)
  {
    return [];
  }

  const start = line.start + match.index;
  const end = line.start + line.text.length;

  return [
    makeIssue(
      'trailing-whitespace',
      'info',
      'Remove the trailing whitespace.',
      lineNumber,
      match.index + 1,
      start,
      end,
      { start: start, end: end, text: '' },
    ),
  ];
}

// == aur != detect kar rahe hain
function findLooseEquality(line, lineNumber)
{
  const issues = [];

  for (let i = 0; i < line.text.length - 1; i += 1)
  {
    const pair = line.text.slice(i, i + 2);

    if (pair !== '==' && pair !== '!=')
    {
      continue;
    }

    const before = line.text[i - 1] || '';
    const after = line.text[i + 2] || '';

    if (before === '=' || after === '=')
    {
      continue;
    }

    const replacement = pair === '==' ? '===' : '!==';
    const start = line.start + i;

    issues.push(
      makeIssue(
        'eqeqeq',
        'warning',
        `Use '${replacement}' instead of '${pair}'.`,
        lineNumber,
        i + 1,
        start,
        start + 2,
        { start: start, end: start + 2, text: replacement },
      ),
    );

    i += 1;
  }

  return issues;
}

// var avoid karne ka simple warning
function findVarUsage(line, lineNumber)
{
  const issues = [];
  const regex = /\bvar\b/g;
  let match = regex.exec(line.text);

  while (match)
  {
    const start = line.start + match.index;

    issues.push(
      makeIssue(
        'no-var',
        'warning',
        "Avoid 'var'. Prefer 'let' or 'const'.",
        lineNumber,
        match.index + 1,
        start,
        start + 3,
        null,
      ),
    );

    match = regex.exec(line.text);
  }

  return issues;
}

// avoiding console usage
function findConsoleUsage(line, lineNumber)
{
  const issues = [];
  const regex = /\bconsole\.(log|debug|info)\b/g;
  let match = regex.exec(line.text);

  while (match)
  {
    const start = line.start + match.index;

    issues.push(
      makeIssue(
        'no-console',
        'warning',
        `Avoid console.${match[1]} in final code.`,
        lineNumber,
        match.index + 1,
        start,
        start + match[0].length,
        null,
      ),
    );

    match = regex.exec(line.text);
  }

  return issues;
}

// debugger is left by mistake at times
function findDebuggerUsage(line, lineNumber)
{
  const issues = [];
  const regex = /\bdebugger\b/g;
  let match = regex.exec(line.text);

  while (match)
  {
    const start = line.start + match.index;

    issues.push(
      makeIssue(
        'no-debugger',
        'error',
        'Remove debugger before shipping.',
        lineNumber,
        match.index + 1,
        start,
        start + match[0].length,
        null,
      ),
    );

    match = regex.exec(line.text);
  }

  return issues;
}


// preview fpr clean output
function addPreview(issue, text, filename)
{
  const lineStart = text.lastIndexOf('\n', issue.start - 1) + 1;
  const nextBreak = text.indexOf('\n', issue.start);
  const lineEnd = nextBreak === -1 ? text.length : nextBreak;
  const preview = text.slice(lineStart, lineEnd).replace(/\r$/, '');
  const markerLength = Math.max(1, issue.end - issue.start);

  return {
    rule: issue.rule,
    level: issue.level,
    message: issue.message,
    line: issue.line,
    column: issue.column,
    start: issue.start,
    end: issue.end,
    fix: issue.fix,
    file: filename,
    preview: preview,
    marker: `${' '.repeat(Math.max(0, issue.column - 1))}${'^'.repeat(markerLength)}`,
  };
}

// auto-fix 
function applyFixes(text, issues)
{
  const fixes = issues
    .filter(function(issue)
    {
      return Boolean(issue.fix);
    })
    .map(function(issue)
    {
      return issue.fix;
    })
    .sort(function(a, b)
    {
      return a.start - b.start;
    });

  let output = '';
  let cursor = 0;
  let applied = 0;

  for (let i = 0; i < fixes.length; i += 1)
  {
    const fix = fixes[i];

    if (fix.start < cursor)
    {
      continue;
    }

    output += text.slice(cursor, fix.start);
    output += fix.text;
    cursor = fix.end;
    applied += 1;
  }

  output += text.slice(cursor);

  return {
    output,
    applied,
  };
}

// normal lint 
function lintFiles(files)
{
  const results = [];

  for (let i = 0; i < files.length; i += 1)
  {
    const file = files[i];
    const text = fs.readFileSync(file, 'utf8');

    results.push({
      file: file,
      issues: lintText(text, file),
      fixed: 0,
    });
  }

  return results;
}

// fix mode me pehle lint, phir safe fixes, phir dubara lint
function fixFiles(files)
{
  const results = [];

  for (let i = 0; i < files.length; i += 1)
  {
    const file = files[i];
    const text = fs.readFileSync(file, 'utf8');
    const issues = lintText(text, file);
    const fixedResult = applyFixes(text, issues);

    if (fixedResult.applied > 0 && fixedResult.output !== text)
    {
      fs.writeFileSync(file, fixedResult.output, 'utf8');
    }

    results.push({
      file: file,
      issues: lintText(fixedResult.output, file),
      fixed: fixedResult.applied,
    });
  }

  return results;
}

// final summary report for everything
function summarizeResults(results)
{
  const summary = {
    files: results.length,
    errors: 0,
    warnings: 0,
    info: 0,
    fixed: 0,
  };

  for (let i = 0; i < results.length; i += 1)
  {
    const result = results[i];
    summary.fixed += result.fixed || 0;

    for (let j = 0; j < result.issues.length; j += 1)
    {
      const issue = result.issues[j];

      if (issue.level === 'error')
      {
        summary.errors += 1;
      }
      else if (issue.level === 'warning')
      {
        summary.warnings += 1;
      }
      else
      {
        summary.info += 1;
      }
    }
  }

  return summary;
}

// pretty report bana rahe hain, thoda sa clean terminal output dene ke liye
function formatReport(results, options)
{
  const summary = summarizeResults(results);
  const lines = [];

  lines.push(...MASCOT.map(function(line)
  {
    return paint(options.color, 'blue', line);
  }));
  lines.push('');
  lines.push(paint(options.color, 'bold', 'fizzylint'));
  lines.push(paint(options.color, 'gray', `files ${summary.files}  errors ${summary.errors}  warnings ${summary.warnings}  info ${summary.info}`));
  lines.push('');

  for (let i = 0; i < results.length; i += 1)
  {
    const result = results[i];

    if (result.issues.length === 0)
    {
      lines.push(`${paint(options.color, 'blue', relative(result.file))}  clean`);
      continue;
    }

    lines.push(paint(options.color, 'blue', relative(result.file)));

    for (let j = 0; j < result.issues.length; j += 1)
    {
      const issue = result.issues[j];
      let tone = 'gray';

      if (issue.level === 'error')
      {
        tone = 'red';
      }
      else if (issue.level === 'warning')
      {
        tone = 'yellow';
      }

      lines.push(`  ${issue.line}:${issue.column}  ${paint(options.color, tone, issue.level)}  ${issue.rule}`);
      lines.push(`  ${issue.message}`);
      lines.push(`  ${issue.preview}`);
      lines.push(`  ${paint(options.color, tone, issue.marker)}`);
    }

    lines.push('');
  }

  if (summary.fixed > 0)
  {
    lines.push(paint(options.color, 'gray', `fixed ${summary.fixed} issue(s)`));
  }

  return lines.join('\n').trimEnd();
}

// absolute path ki jagah relative dikhana better lagta hai terminal me
function relative(filePath)
{
  return path.relative(process.cwd(), filePath) || path.basename(filePath);
}

// for help arg
function printHelp()
{
  return [
    ...MASCOT,
    '',
    'fizzylint',
    '',
    'Usage',
    '  fizzylint',
    '  fizzylint lint [target]',
    '  fizzylint fix [target]',
    '  fizzylint help',
    '',
  ].join('\n');
}

module.exports = {
  collectFiles,
  fixFiles,
  formatReport,
  lintFiles,
  lintText,
  parseArgs,
  printHelp,
  summarizeResults,
};
