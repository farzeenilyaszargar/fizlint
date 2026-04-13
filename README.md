## fizlint - Live Linting Project 

Everything Is Done Here From The Basics!

fizlint --> **f**arzeen **i**lyas **z**argar **lint**

hosted at https://fizlint.vercel.app

### Flow:

Editor ----(lsp)----> server --------(plain text)--------> parser -----> linting engine ----> diagnostics --------(lsp message)-------> Editor


### Topics Learnt:
- AST
- CLI Packaging & Building Format
- LSP
- Parser


### Plan
I need several components to do this:

- First I need a file reader that takes input from my editor (say VS Code) and recounstructs the document for the server using LSP protocol

```
while (running)
    message = nextMessage(stdin)
    dispatch(message)
```






- Basic Website For Helping In Downloading of CLI (using npm or curl)


