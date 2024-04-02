package com.kroegerama.kgen.cli

import com.kroegerama.kgen.Constants
import com.kroegerama.kgen.OptionSet
import com.kroegerama.kgen.generator.FileHelper
import com.kroegerama.kgen.generator.Generator
import com.kroegerama.kgen.openapi.OpenAPIAnalyzer
import com.kroegerama.kgen.openapi.parseSpecFile
import com.kroegerama.kgen.poet.PoetGenerator
import com.github.rvesse.airline.annotations.Arguments
import com.github.rvesse.airline.annotations.Command
import com.github.rvesse.airline.annotations.Option
import java.io.File

@Command(name = "generate", description = "Generate code from the specified OpenAPI Spec.")
class Generate : Runnable {

    @Option(name = ["-p", "--package-name"], title = ["package name"])
    private val packageName: String = Constants.DEFAULT_PACKAGE_NAME

    @Option(
        name = ["-o", "--output"],
        title = ["output directory"]
    )
    private val output = ""

    @Arguments(
        title = ["spec file"],
        description = "Spec file (yaml/json). Can be a file or url."
    )
    private val specFile = ""

    @Option(
        name = ["-l", "--limit-apis"],
        title = ["limit apis"],
        description = "If set, generate only these APIs (set via tag) and their models. Comma separated list. Example: \"auth,app\""
    )
    private val limitApis = ""

    @Option(
        name = ["-v", "--verbose"],
        title = ["detailed output"]
    )
    private val verbose = true

    @Option(
        name = ["-d", "--dry-run"],
        title = ["Dry run"],
        description = "Do not create any files. Just parse and analyze."
    )
    private val dryRun = false

    @Option(
        name = ["--use-inline-class"],
        title = ["Use inline class"],
        description = "Use inline classes for named primitive types. Else use typealias."
    )
    private val useInlineClass = false

    @Option(
        name = ["--allow-parse-errors"],
        title = ["Allow parse errors"],
        description = "Try to generate classes, even if parsing errors occur in the spec."
    )
    private val allowParseErrors = false

    @Option(
        name = ["--api-method-syntax-type"],
        title = ["Type of Api Method Syntax to generate"],
        description = "Generate different types of Api Syntax (Acceptable Types: Coroutines, Observable)"
    )
    private val apiMethodSyntaxType = ""

    @Option(
        name = ["--use-companion-objects"],
        title = ["Generate Classes and Enums with Companion objects"],
        description = "Whether or not to generate all classes and enums with Companion objects"
    )
    private val useCompanionObjects = false

    @Option(
        name = ["--use-api-spec-enum-names"],
        title = ["Generate Enums using property names from openapi spec"],
        description = "Whether or not to generate Enum property names from openapi spec or using Constant format. ie: SomeEnumValue vs SOME_ENUM_VALUE"
    )
    private val useApiSpecEnumNames = false

    override fun run() {
        val options = OptionSet(
            specFile = specFile,
            packageName = packageName,
            outputDir = output,
            limitApis = limitApis.split(",").filter { it.isNotBlank() }.toSet(),
            verbose = verbose,
            dryRun = dryRun,
            useInlineClass = useInlineClass,
            outputDirIsSrcDir = false,
        )
        println("Selected options: $options")
        println()

        val output = File(output)
        if (!output.exists() || !output.isDirectory) {
            println("Output directory does not exist")
            return
        }

        if (options.verbose) println("Parsing spec file...")
        val openAPI = parseSpecFile(options.specFile, allowParseErrors)

        val isObservableApiSyntaxType = apiMethodSyntaxType != null && apiMethodSyntaxType == "Observable"

        if (options.verbose) println("Generating...")
        val analyzer = OpenAPIAnalyzer(openAPI, options)
        val poetGenerator = PoetGenerator(openAPI, options, analyzer, isObservableApiSyntaxType, useCompanionObjects, useApiSpecEnumNames)
        val fileHelper = FileHelper(options)
        val generator = Generator(options, poetGenerator, fileHelper, analyzer)

        generator.generate()
    }
}