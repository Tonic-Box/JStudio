package com.tonic.cli;

import com.tonic.cli.commands.BatchCommand;
import com.tonic.cli.commands.InfoCommand;
import com.tonic.cli.commands.ReplCommand;
import com.tonic.cli.commands.RunCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "jstudio",
    mixinStandardHelpOptions = true,
    version = "JStudio CLI 1.0",
    description = "JStudio - Java bytecode analysis and transformation tool",
    subcommands = {
        RunCommand.class,
        ReplCommand.class,
        BatchCommand.class,
        InfoCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class JStudioCLI implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @Option(names = {"-q", "--quiet"}, description = "Quiet mode, minimal output")
    boolean quiet;

    @Override
    public void run() {
        System.out.println("JStudio CLI - Java bytecode analysis and transformation");
        System.out.println();
        System.out.println("Usage: jstudio <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  run      Execute a plugin or script on target files");
        System.out.println("  repl     Start interactive REPL mode");
        System.out.println("  batch    Batch process multiple targets");
        System.out.println("  info     Display information about target files");
        System.out.println("  help     Show help for commands");
        System.out.println();
        System.out.println("Use 'jstudio <command> --help' for more information.");
    }
}
