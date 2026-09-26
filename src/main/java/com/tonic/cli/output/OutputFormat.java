package com.tonic.cli.output;

import lombok.Getter;

/** A command-line output format and the file extension its result files use. */
@Getter
public enum OutputFormat
{
    TEXT("txt"),
    JSON("json"),
    CSV("csv");

    private final String extension;

    OutputFormat(String extension)
    {
        this.extension = extension;
    }

}
