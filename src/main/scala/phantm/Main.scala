package phantm

import java.io._

import scala.xml._

import phantm.util._

import phantm.phases._

import phantm.ast.Trees.Program
import phantm.ast.STToAST

object Main {
    var settings = Settings()
    var displayUsage = false
    var displayVersion = false
    var files: List[String] = List[String]()

    def main(args: Array[String]): Unit = {
        if (args.length > 0) {
            handleArgs(args.toList)

            if (displayVersion || displayUsage) {
                version

                if (displayUsage) {
                    usage
                }
            } else {
                if (files.isEmpty) {
                    println("No file provided.")
                    usage
                } else {
                    val rep = new Reporter(files)
                    Reporter.set(rep)
                    Settings.set(settings)
                    new PhasesRunner(rep).run(new PhasesContext(files = files))
                }
            }
        } else {
            usage
        }
    }

    def handleArgs(args: List[String]): Unit= {
        if (args == Nil) return;

        val dumpMatcher  = "--dump:([a-z]+)".r
        val printMatcher = "--print:([a-z]+)".r

        (args.head.toLowerCase :: args.tail) match {
            case "--help" :: xs =>
                displayUsage = true
            case printMatcher(ph) :: xs =>
                settings = settings.copy(printAfter = Set(ph))
                handleArgs(xs)
            case dumpMatcher(ph) :: xs =>
                settings = settings.copy(dumpAfter = Set(ph))
                handleArgs(xs)
            case "--showincludes" :: xs =>
                settings = settings.copy(displayIncludes = true)
                handleArgs(xs)
            case "--noincludes" :: xs =>
                settings = settings.copy(resolveIncludes = false)
                handleArgs(xs)
            case "--inline" :: mode :: xs =>
                val m = mode match {
                    case "none"   => InlineNone
                    case "manual" => InlineManual
                    case "leaves" => InlineLeaves
                    case "full"   => InlineFull
                    case _ =>
                        println("Invalid inline mode")
                        displayUsage = true
                        InlineNone
                }

                settings = settings.copy(inlineMode = m)
                handleArgs(xs)

            case "--format" :: "termbg" :: xs =>
                settings = settings.copy(format = "termbg")
                handleArgs(xs)
            case "--format" :: "term" :: xs =>
                settings = settings.copy(format = "term")
                handleArgs(xs)
            case "--format" :: "html" :: xs =>
                settings = settings.copy(format = "html")
                handleArgs(xs)
            case "--format" :: "quickfix" :: xs =>
                settings = settings.copy(format = "quickfix")
                handleArgs(xs)
            case "--format" :: "none" :: xs =>
                settings = settings.copy(format = "none")
                handleArgs(xs)
            case "--format" :: f :: xs =>
                println("Invalid format "+f)
                displayUsage = true
            case "--anyinput" :: "yes" :: xs =>
                settings = settings.copy(anyInput = true)
                handleArgs(xs)
            case "--anyInput" :: "no" :: xs =>
                settings = settings.copy(anyInput = false)
                handleArgs(xs)
            case "--anyinput" :: xs =>
                settings = settings.copy(anyInput = true)
                handleArgs(xs)
            case "--compacterrors" :: "yes" :: xs =>
                settings = settings.copy(compactErrors = true)
                handleArgs(xs)
            case "--compacterrors" :: "no" :: xs =>
                settings = settings.copy(compactErrors = false)
                handleArgs(xs)
            case "--compacterrors" :: xs =>
                settings = settings.copy(compactErrors = true)
                handleArgs(xs)
            case "--only" :: filter :: xs =>
                settings = settings.copy(typeFlowFilter = filter.replace("::", "/").split(":").map(_.replace("/", "::")).toList)
                handleArgs(xs)
            case "--noapi" :: xs =>
                settings = settings.copy(importAPI = false)
                handleArgs(xs)
            case "--tests" :: xs =>
                settings = settings.copy(testsActive = true)
                handleArgs(xs)
            case "--fixpoint" :: xs =>
                settings = settings.copy(displayFixPoint = true)
                handleArgs(xs)
            case "--debug" :: xs =>
                settings = settings.copy(displayFixPoint = true, testsActive = true, displayProgress = true, verbosity = 3)
                handleArgs(xs)
            case "--quiet" :: xs =>
                settings = settings.copy(verbosity = 0)
                handleArgs(xs)
            case "--shy" :: xs =>
                settings = settings.copy(verbosity = -1)
                handleArgs(xs)
            case "--verbose" :: xs =>
                settings = settings.copy(verbosity = 2)
                handleArgs(xs)
            case "--vverbose" :: xs =>
                settings = settings.copy(verbosity = 3)
                handleArgs(xs)
            case "--includepath" :: ip :: xs =>
                settings = settings.copy(includePaths = ip.split(":").toList)
                handleArgs(xs)
            case "--importincludes" :: paths :: xs =>
                IncludeResolver.importIncludes(paths.split(":").toList)
                handleArgs(xs)
            case "--importstate" :: paths :: xs =>
                settings = settings.copy(dumps = paths.split(":").toList)
                handleArgs(xs)
            case "--importapi" :: aps :: xs =>
                settings = settings.copy(apis = aps.split(":").toList)
                handleArgs(xs)
            case "--exportapi" :: path :: xs =>
                settings = settings.copy(exportAPIPath = Some(path))
                handleArgs(xs)
            case "--exportcg" :: path :: xs =>
                settings = settings.copy(exportCGPath = Some(path))
                handleArgs(xs)
            case "--exportmg" :: path :: xs =>
                settings = settings.copy(exportMGPath = Some(path))
                handleArgs(xs)
            case "--progress" :: xs =>
                settings = settings.copy(displayProgress = true)
                handleArgs(xs)
            case "--summary" :: xs =>
                settings = settings.copy(summaryOnly = true)
                handleArgs(xs)
            case "--version" :: xs =>
                displayVersion = true
            case "--lint" ::  xs =>
                settings = settings.copy(onlyLint = true)
                handleArgs(xs)
            case "--" ::  xs =>
                for (path <- args.tail) {
                    val f = new File(path)
                    files = files ::: f.getAbsolutePath :: Nil
                }
            case x :: xs =>
                if (x startsWith "-") {
                    println("Notice: Unknown option '"+args.head+"'. Real file? Use '--' to delimit options.")
                } else {
                    val f = new File(args.head)
                    files = files ::: f.getAbsolutePath :: Nil
                }
                handleArgs(xs)
            case Nil =>
        }
    }

    def version = {
        val data = XML.load(getClass().getClassLoader().getResourceAsStream("build.xml"))

        println("phantm "+(data \ "version").text.trim+" (built: "+(data \ "date").text.trim+")")
    }

    def usage = {
        println("Usage:   phantm [..options..] <files ...>")
        println("Options:");
        println
        println("  - General settings:")
        println("         --maindir <maindir>    Specify main directory of the tool")
        println("         --includepath <paths>  Define paths for compile time include resolution (.:a:bb:c:..)")
        println("         --only <symbols>       Only perform analysis on the specified")
        println("                                        symbols (main:func1:class1::method1:...)")
        println
        println("  - Error control:")
        println("         --format <mode>        Change the way errors are displayed:")
        println("                                Mode: none     : no colors")
        println("                                      termbg   : ANSI colors inside the code (default)")
        println("                                      term     : ANSI colors below the code")
        println("                                      html     : HTML colors below the code")
        println("                                      quickfix : quickfix error style")
        println("         --quiet                Mute some errors such as uninitialized variables")
        println("         --shy                  Psscht")
        println("         --verbose              Display more notices")
        println("         --compactErrors yes|no Group errors per line. Useful when inlining")
        println("         --vverbose             Be nitpicking and display even more notices")
        println
        println("  - Additional features/infos:")
        println("         --noapi                Do not load the main API")
        println("         --noincludes           Disables includes resolutions")
        println("         --fixpoint             Display fixpoints")
        println("         --showincludes         Display the list of included files")
        println("         --importAPI <paths>    Import additional APIs (a.xml:b.xml:...)")
        println("         --importState <paths>  Import state files (i.e. last.dump)")
        println("         --exportAPI <path>     Use the type analysis to output a likely API")
        println("         --exportCG <path>      Export the call graph in dot format to <path>")
        println("         --exportMG <path>      Export the method inheritence graph in dot format to <path>")
        println("         --progress             Display analysis progress")
        println("         --inline <mode>        Perform function/method inlining, default is 'manual'")
        println("                                Mode: none     : no inlining")
        println("                                      manual   : Inline methods specified in the code")
        println("                                      leaves   : Inline leaves in the callgraph")
        println("                                      full     : Inline acyclic functions")
        println("         --anyError yes|[no]    Assume correct inputs")
        println
        println("  - Misc.:")
        println("         --tests                Enable internal consistency checks")
        println("         --lint                 Stop the analysis after the parsing")
        println("         --debug                Display all kind of debug information")
        println("         --help                 This help")
        println("         --                     Separate options and files, allowing files starting with '-'.")
    }
}
