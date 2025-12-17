package com.tonic.cli.commands;

import com.tonic.cli.repl.REPLMode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
    name = "repl",
    description = "Start interactive REPL mode for bytecode exploration",
    mixinStandardHelpOptions = true
)
public class ReplCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Optional: JAR or class file to preload")
    private File target;

    @Option(names = {"-s", "--script"}, description = "Script file to execute before starting REPL")
    private File initScript;

    @Option(names = {"--no-banner"}, description = "Skip the startup banner")
    private boolean noBanner;

    @Override
    public Integer call() {
        try {
            REPLMode repl = new REPLMode();

            if (!noBanner) {
                repl.printBanner();
            }

            if (target != null && target.exists()) {
                repl.loadTarget(target);
            }

            if (initScript != null && initScript.exists()) {
                repl.executeScript(initScript);
            }

            repl.run();
            return 0;
        } catch (Exception e) {
            System.err.println("Error starting REPL: " + e.getMessage());
            return 1;
        }
    }
}
