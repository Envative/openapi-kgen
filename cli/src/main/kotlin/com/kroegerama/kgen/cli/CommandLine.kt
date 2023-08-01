package com.kroegerama.kgen.cli

import com.github.rvesse.airline.annotations.Cli
import com.github.rvesse.airline.help.Help
import com.kroegerama.kgen.Constants

@Cli(
    name = Constants.CLI_NAME,
    description = "", //Util.generatorInfo,
    defaultCommand = Help::class,
    commands = [Help::class, Generate::class]
)
object CommandLine {
    @JvmStatic
    fun main(args: Array<String>) {
        val cli = com.github.rvesse.airline.Cli<Runnable>(
            CommandLine::class.java
        )
        val cmd = cli.parse(*args)
        cmd.run()
    }
}