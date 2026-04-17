# fizzylint CLI

`fizzylint` is a lightweight JavaScript and TypeScript linter for terminal use.

Docs website: https://fizlint.vercel.app

## What It Does

The CLI scans `.js`, `.jsx`, `.ts`, and `.tsx` files and reports:

- trailing whitespace
- loose equality (`==`, `!=`)
- `var` usage
- `console.log`, `console.debug`, `console.info`
- `debugger` statements

It ignores common build/output folders like `.git`, `node_modules`, `.next`, `dist`, `build`, and `coverage`.

## Auto-fix Support

`fizzylint fix` applies only safe built-in fixes:

- removes trailing whitespace
- converts `==` to `===`
- converts `!=` to `!==`

## Commands

```bash
fizzylint
fizzylint lint [target]
fizzylint fix [target]
fizzylint help
```

- `target` defaults to the current directory (`.`)
- `target` can be a file path or directory path

## Options

```bash
--no-color
--help
-h
```

## Install / Run

```bash
npx fizzylint lint .
npx fizzylint fix .
```

or install globally:

```bash
npm i -g fizzylint
fizzylint lint .
```

## Notes

This is a small heuristic linter and does not use a full AST/parser pipeline.
