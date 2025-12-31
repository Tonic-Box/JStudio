package com.tonic.cli.output;

import lombok.Getter;

@Getter
public enum OutputFormat {
    TEXT("txt"),
    JSON("json"),
    CSV("csv");

    private final String extension;

    OutputFormat(String extension) {
        this.extension = extension;
    }

}
