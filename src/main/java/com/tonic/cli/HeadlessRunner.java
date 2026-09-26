package com.tonic.cli;

import picocli.CommandLine;

/** The headless command-line entry point; runs the jstudio picocli command and exits with its code. */
public class HeadlessRunner
{

    /**
     * Runs the command line and exits the JVM with its exit code.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new JStudioCLI())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}
