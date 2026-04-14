# fizzylint

`fizzylint` is a minimal JavaScript and TypeScript lint CLI with a clean terminal interface, safe autofixes, and practical rules for day-to-day code review.

Hosted docs: https://fizlint.vercel.app

## What It Checks

- loose equality (`==`, `!=`)
- `var` declarations
- `console.log`, `console.debug`, `console.info`
- `debugger` statements
- duplicate imports
- trailing whitespace
- empty blocks
- TODO / FIXME comments
- `let` declarations that can be `const`
- explicit `any` in TypeScript

## Safe Fixes

`fizzylint fix` currently autofixes:

- trailing whitespace
- loose equality to strict equality
- `let` to `const` when the variable is never reassigned

## Usage

```bash
fizzylint lint src
fizzylint fix src
fizzylint lint src --json
fizzylint lint --stdin --stdin-filename src/example.ts
```

## Options

```bash
--json
--quiet
--no-color
--ext .js,.jsx,.ts,.tsx
--stdin
--stdin-filename <name>
--max-warnings <count>
```

## Notes

The CLI is intentionally lightweight and heuristic-driven, so it stays fast without requiring a heavy parser pipeline.
