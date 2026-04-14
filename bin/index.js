#!/usr/bin/env node

const fs = require('fs');
const {
  collectFiles,
  fixFiles,
  formatJsonReport,
  formatPrettyReport,
  lintFiles,
  lintStdin,
  parseArgs,
  printHelp,
  summarizeResults,
} = require('../lib/core');

function main() {
  const options = parseArgs(process.argv.slice(2));

  if (options.command === 'help') {
    process.stdout.write(`${printHelp()}\n`);
    return;
  }

  let results;

  if (options.stdin) {
    const stdin = fs.readFileSync(0, 'utf8');
    const linted = lintStdin(stdin, options);
    results = linted.results;
    if (options.command === 'fix' && linted.stdout !== null) {
      process.stdout.write(linted.stdout);
      if (!linted.stdout.endsWith('\n')) {
        process.stdout.write('\n');
      }
    }
  } else {
    const files = collectFiles(options.target, options);
    results = options.command === 'fix' ? fixFiles(files, options) : lintFiles(files, options);
    const report = options.format === 'json' ? formatJsonReport(results) : formatPrettyReport(results, options);
    process.stdout.write(`${report}\n`);
  }

  if (options.stdin && options.format !== 'json') {
    process.stderr.write(`${formatPrettyReport(results, options)}\n`);
  } else if (options.stdin && options.format === 'json') {
    process.stderr.write(`${formatJsonReport(results)}\n`);
  }

  const summary = summarizeResults(results);
  const exitCode = summary.errors > 0 || (options.maxWarnings !== null && summary.warnings > options.maxWarnings) ? 1 : 0;
  process.exitCode = exitCode;
}

main();
