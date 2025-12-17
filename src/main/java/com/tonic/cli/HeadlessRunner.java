package com.tonic.cli;

import picocli.CommandLine;

public class HeadlessRunner {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JStudioCLI())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args);
        System.exit(exitCode);
    }
}
