package com.tonic.cli.output;

public enum OutputFormat {
    TEXT("txt"),
    JSON("json"),
    CSV("csv");

    private final String extension;

    OutputFormat(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}
