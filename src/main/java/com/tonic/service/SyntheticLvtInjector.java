package com.tonic.service;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;

import java.io.ByteArrayInputStream;

/**
 * Builds class bytes augmented with synthetic LocalVariableTables for methods that have none, so a live
 * debugger reading the redefined class sees named locals on stripped/obfuscated targets. The recovered names
 * match what the decompiler renders; only the debug attribute is added, the bytecode itself is unchanged, so
 * offsets, frames, and breakpoints stay valid.
 */
public final class SyntheticLvtInjector {

    private SyntheticLvtInjector() {
    }

    /**
     * Returns {@code original}'s bytes with a synthetic LVT added to every method that lacks one, or null when
     * nothing was added (every method already had an LVT, or none was recoverable). Operates on a fresh parse,
     * so the caller's {@link ClassFile} is never mutated.
     */
    public static byte[] augment(ClassFile original) {
        if (original == null) {
            return null;
        }
        try {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(original.write()));
            ClassDecompiler decompiler = new ClassDecompiler(cf);
            boolean changed = false;
            for (MethodEntry method : cf.getMethods()) {
                CodeAttribute code = method.getCodeAttribute();
                if (code == null || hasLocalVariableTable(code)) {
                    continue;
                }
                LocalVariableTableAttribute lvt = decompiler.localVariableTableFor(method);
                if (lvt != null) {
                    code.getAttributes().add(lvt);
                    changed = true;
                }
            }
            return changed ? cf.write() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasLocalVariableTable(CodeAttribute code) {
        for (Attribute a : code.getAttributes()) {
            if (a instanceof LocalVariableTableAttribute) {
                return true;
            }
        }
        return false;
    }
}
