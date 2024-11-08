# LacsDbg
A more user-friendly debugging toolkit for developing the Lacs programming language in the course CS 241e

Features:
- Line-by-line debugging with Breakpoints
- Debugging Code Hider (reduce clutter when debugging)
- Memory & Register Printing
- Disassembly (feature from the original debugger)
- Extendable! You can add your own functionality

*The functionality to print stack frames is not included by default, this is for you to implement in A5 and A6*


## `Setup`

To setup LacsDbg, you need to modify the handout code:

ProgramRepresentation.scala:
```diff
-sealed abstract class Code
+abstract class Code
```

Appropriate changes should be made to your code to call `LacsDbg.debug(initialState, mtl.debugTable)` and `LacsDbg.toDebugMachineCode(code)`

Copy the `LacsDbg.sc` source file to the `src/cs241e/assignments` folder (where the other source files are located)