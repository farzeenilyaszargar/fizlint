#!/usr/bin/env node

const {
  collectFiles,
  fixFiles,
  formatReport,
  lintFiles,
  parseArgs,
  printHelp,
  summarizeResults,
} = require('../lib/core');

// ye bas CLI ka starting point hai, actual logic core file me hai
function main()
{
  const options = parseArgs(process.argv.slice(2));

  if (options.command === 'help')
  {
    process.stdout.write(`${printHelp()}\n`);
    return;
  }

  const files = collectFiles(options.target);
  let results = [];

  if (options.command === 'fix')
  {
    results = fixFiles(files);
  }
  else
  {
    results = lintFiles(files);
  }

  process.stdout.write(`${formatReport(results, options)}\n`);

  const summary = summarizeResults(results);
  process.exitCode = summary.errors > 0 ? 1 : 0;
}

main();
