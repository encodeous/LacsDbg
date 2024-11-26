package cs241e.assignments

import cs241e.assignments.Assembler.{ADD, decodeUnsigned, encodeSigned, encodeUnsigned}
import cs241e.assignments.Debugger.disassemble
import cs241e.assignments.ProgramRepresentation.*
import cs241e.assignments.Transformations.{eliminateBlocks, eliminateComments, eliminateLabels, transformCode}
import cs241e.mips.State.PC
import cs241e.mips.{CPU, State, Word}

import java.util.Scanner
import scala.collection.mutable

object LacsDbg {
  abstract class DbgSymbol
  type AdvancedDebugTable = Map[Word, Seq[DbgSymbol]]

  case class DebugMachineCode(words: Seq[Word], debugTable: AdvancedDebugTable)

  /** A generic symbol used for debugging */
  case class Debug(symbol: DbgSymbol) extends Code

  /** Used for hiding long sections of code, so that debugging is less messy
   *  Adds `level` to the current debugger's print level. Make sure to add `-level` when you are done your section of code.
   **/
  case class DbgPrintLevel(level: Int) extends DbgSymbol
  /** A debug comment.
   *  The debugger will print the comment before the CPU runs the line it is attached to
   **/
  case class DbgComment(message: String, prefix: String = "// ") extends DbgSymbol

  /** A debug Breakpoint.
   *  The debugger will break before executing the line it is attached to
   */
  case class Breakpoint(name: String) extends DbgSymbol

  /**
   * A No-op instruction, useful for debugging, i.e attaching a breakpoint after the epilogue
   */
  val NOP = ADD(Reg.zero, Reg.zero)

  // (1) You can add additional debug symbols, such as `Chunk` information to enable your inspector to view variables and params.

  /**
   * Eliminate all `Debug` from a tree of `code` by recursively transforming each node in the tree.
   *
   * Assumes that you have already implemented `Transformations.transformCode` in Assignment 2
   */
  def eliminateDebug(codes: Seq[Code]): Seq[Code] = codes.filterNot(code => code.isInstanceOf[Debug])

  def toDebugMachineCode(code: Code): DebugMachineCode = {
    val code2 = eliminateBlocks(code)
    val code3 = eliminateComments(code2)
    val code4 = eliminateDebug(code3)
    val code5 = eliminateLabels(code4)
    val debugTable = createAdvancedDebugTable(code2)
    DebugMachineCode(code5, debugTable)
  }

  /** Given a sequence `codes` of `Code`, generates a table suitable for use by the `LacsDbg`. The table
   * associates with each memory address the comments appearing just before the code at that address,
   * and the labels defined at that address. The debugger uses this table to show labels, comments, and pauses on Breakpoints
   * when it traces through the execution of machine language code.
   *
   * Note: this method assumes that the `codes` contain only `CodeWord`, `Define`, `Use`, `BeqBne`,
   * `Comment`, or `Debug`.
   */
  def createAdvancedDebugTable(codes: Seq[Code]): AdvancedDebugTable = {
    val ret = mutable.Map[Word, Seq[DbgSymbol]]()
    var location = 0

    def locationWord = Word(encodeUnsigned(location * 4))

    def add(sym: DbgSymbol): Unit = {
      val existing = ret.getOrElse(locationWord, Seq())
      ret(locationWord) = existing :+ sym
    }

    for (code <- codes) code match {
      case _: CodeWord | _: Use | _: BeqBne => location += 1
      case Comment(msg) => add(DbgComment(msg))
      case Define(label) => add(DbgComment(label.toString, "[Label]: "))
      case Debug(symbol) => add(symbol)
      case _ => require(false, s"Encountered unsupported code $code.")
    }
    Map() ++ ret
  }

  def debug(initialState: State, debugTable: AdvancedDebugTable = Map.empty, debugLevel: Int = 0): State = {
    var state = initialState;
    var frozen = false
    val scanner = new Scanner(System.in)
    var level = 0

    /**
     * The inspector tool when the debugger is in a frozen state
     *
     * Addresses and numbers are represented in base 10, if you prefer hex, you can freely modify the code to your liking.
     */
    def debugInspector(state: State): Unit = {
      val cmd = scanner.next()

      def printMemory(addr: Long) = {
        require(addr % 4 == 0)
        val memVal = decodeUnsigned(state.mem(Word(encodeSigned(addr))));
        println("MEM: %08x %08x".format(addr, memVal))
      }

      def printRegister(register: Int) = {
        require(0 <= register && register <= 31)
        val regVal = decodeUnsigned(state.reg(register))
        println("$%2s %08x".format(register, regVal))
      }

      // if you implement (1), you can use the Chunk offsets to get your parameters & current frame

      val helpMsg =
        """Commands:
          | 'h' - Help
          | 'c' - Continue
          | 's' - Step forward 1 instruction
          | 'r <#>' - Print register
          | 'dr <#>' - Dereference register and print
          | 'm <addr>' - Print print value at memory addr
          | 'mr <addr> <len>' Print memory starting from addr, for len bytes
          |""".stripMargin

      try{
        cmd match {
          case "c" => {
            frozen = false
            return
          }
          case "s" => {
            frozen = true
            return
          }
          case "h" => println(helpMsg)
          case "r" => printRegister(scanner.nextInt())
          case "dr" => printMemory(decodeUnsigned(state.reg(scanner.nextInt())))
          case "m" => printMemory(scanner.nextInt(16))
          case "mr" => {
            val start = scanner.nextInt(16)
            val len = scanner.nextInt(16)
            if (start % 4 != 0 || len % 4 != 0) {
              println("Addresses must be multiples of 4")
            }
            else {
              for (addr <- start until (start + len) by 4) {
                printMemory(addr)
              }
            }
          }
        }
      }catch{
        case ex => ex.printStackTrace()
      }
      debugInspector(state)
    }

    while(state.reg(PC) != CPU.terminationPC){
      if (debugTable.isDefinedAt(state.reg(PC))) {
        for (dbg <- debugTable(state.reg(PC))) {
          dbg match {
            case DbgComment(msg, prefix) => if (level >= debugLevel) println(prefix + msg)
            case DbgPrintLevel(lvl) => level += lvl
            case Breakpoint(name) => {
              println(s"[BREAKPOINT]: ${name}")
              frozen = true
            }
          }
        }
      }

      if(level >= debugLevel){
        var disasmed = disassemble(state.mem(state.reg(PC)))

        // For a lis instruction, also print the following word.
        if (disasmed.startsWith("lis ")) {
          val constant = state.mem(CPU.incrementAddress(state.reg(PC)))
          disasmed += "; " + decodeUnsigned(constant)
        }
        else if(disasmed.equals("add $0, $0, $0")){
          disasmed = "NOP"
        }

        println(s"${"%08x".format(decodeUnsigned(state.reg(PC)))} ${disasmed}")

        if (frozen) {
          debugInspector(state)
        }
      }
      state = CPU.step(state)
    }
    state
  }
}
