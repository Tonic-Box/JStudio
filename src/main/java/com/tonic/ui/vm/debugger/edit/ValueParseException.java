package com.tonic.ui.vm.debugger.edit;

public class ValueParseException extends Exception {

    public ValueParseException(String message) {
        super(message);
    }

    public ValueParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
