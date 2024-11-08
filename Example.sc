import cs241e.assignments.{A1, LacsDbg, Reg}
import cs241e.assignments.Assembler.*
import cs241e.assignments.LacsDbg.{Breakpoint, DbgPrintLevel, Debug}
import cs241e.assignments.ProgramRepresentation.*
import cs241e.mips.Word

/**
 * This is a simple example showing how to use LacsDbg
 * It requires you have implemented all of A1 and follow the setup instructions in README.md
 */
object Example {
  def main(args: Array[String]): Unit = {

    val loop = Label("Loop")
    val break = Label("Break")
    val code = block(
      LIS(Reg(1)),
      // loop 10 times
      Word(encodeUnsigned(10)),
      LIS(Reg(2)),
      Word(encodeUnsigned(1)),
      // This breakpoint will be hit 10 times
      Debug(Breakpoint("Loop Begin")),
      Define(loop),
      beq(Reg(1), Reg(0), break),
      // don't step through here, we want to hide this
      Comment("Skipped code"),
      Debug(DbgPrintLevel(-1)),
      SUB(Reg(1), Reg(1), Reg(2)),
      LIS(Reg.scratch),
      Use(loop),
      Debug(DbgPrintLevel(1)),
      JR(Reg.scratch),
      Define(break),
      Debug(Breakpoint("After Loop")),
      JR(Reg.link),
    )

    val mtl = LacsDbg.toDebugMachineCode(code)

    val initialState = A1.setMem(mtl.words)
    LacsDbg.debug(initialState, mtl.debugTable)
  }
}
