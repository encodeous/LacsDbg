# LacsDbg
A more user-friendly debugging toolkit for developing the Lacs programming language in the course CS 241e

## `Setup`

To setup LacsDbg, you need to modify the handout code:

ProgramRepresentation.scala:
```diff
-sealed abstract class Code
+abstract class Code
```

Appropriate changes should be made to your code to call `LacsDbg.debug(initialState, mtl.debugTable)` and `LacsDbg.toDebugMachineCode(code)`

Copy the `LacsDbg.sc` source file to the `src/cs241e/assignments` folder (where the other source files are located)