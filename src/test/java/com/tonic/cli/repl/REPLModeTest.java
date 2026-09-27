package com.tonic.cli.repl;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("the REPL stops at the end of its input")
class REPLModeTest
{

    @Test
    @DisplayName("end of input ends the loop instead of spinning on it")
    void endOfInputExits() throws Exception
    {
        Terminal terminal = TerminalBuilder.builder().streams(new ByteArrayInputStream(":help\n".getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream()).dumb(true).build();
        REPLMode repl = new REPLMode(terminal);

        assertTimeoutPreemptively(Duration.ofSeconds(20), repl::run);
    }
}
