package com.tonic.ui.editor.source;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.TypeDecl;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.lower.LoweringException;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.source.parser.ParseErrorListener;
import com.tonic.analysis.source.parser.ParseException;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SourceCompiler {

    public CompilationResult compile(String source, ClassFile originalClass, ClassPool classPool) {
        long startTime = System.currentTimeMillis();
        List<CompilationError> errors = new ArrayList<>();

        ParseErrorListener.CollectingErrorListener listener = ParseErrorListener.collecting();
        JavaParser parser = JavaParser.withErrorListener(listener);

        CompilationUnit cu;
        try {
            cu = parser.parse(source);
        } catch (ParseException e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(createErrorFromException(e, source));
            }
            return CompilationResult.failure(errors, source, elapsed(startTime));
        } catch (Exception e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(CompilationError.error(1, 1, 0, 1, "Parse error: " + e.getMessage()));
            }
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }

        if (listener.hasErrors()) {
            collectParseErrors(listener, source, errors);
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }

        try {
            ClassFile newClass = lowerToClassFile(cu, originalClass, classPool);
            return CompilationResult.success(newClass, source, elapsed(startTime));
        } catch (LoweringException e) {
            errors.add(CompilationError.error(1, 1, 0, 1, "Lowering error: " + e.getMessage()));
            return CompilationResult.failure(errors, source, elapsed(startTime));
        } catch (Exception e) {
            errors.add(CompilationError.error(1, 1, 0, 1, "Compilation error: " + e.getMessage()));
            return CompilationResult.failure(errors, source, elapsed(startTime));
        }
    }

    public List<CompilationError> parseOnly(String source) {
        List<CompilationError> errors = new ArrayList<>();

        ParseErrorListener.CollectingErrorListener listener = ParseErrorListener.collecting();
        JavaParser parser = JavaParser.withErrorListener(listener);

        try {
            parser.parse(source);
        } catch (ParseException e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(createErrorFromException(e, source));
            }
        } catch (Exception e) {
            collectParseErrors(listener, source, errors);
            if (errors.isEmpty()) {
                errors.add(CompilationError.error(1, 1, 0, 1, "Parse error: " + e.getMessage()));
            }
        }

        if (listener.hasErrors()) {
            collectParseErrors(listener, source, errors);
        }

        return errors;
    }

    private ClassFile lowerToClassFile(CompilationUnit cu, ClassFile original, ClassPool classPool) {
        TypeDecl primaryType = cu.getPrimaryType();
        if (primaryType == null) {
            throw new LoweringException("No type declaration found in source");
        }

        if (!(primaryType instanceof ClassDecl)) {
            throw new LoweringException("Only class types are currently supported for recompilation");
        }

        ClassDecl classDecl = (ClassDecl) primaryType;
        String ownerClass = original.getClassName();

        ASTLowerer lowerer = new ASTLowerer(original.getConstPool(), classPool);
        lowerer.setCurrentClassDecl(classDecl);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(original.getConstPool());

        for (MethodDecl methodDecl : classDecl.getMethods()) {
            if (methodDecl.getBody() == null) {
                continue;
            }

            String methodName = methodDecl.getName();
            String descriptor = buildDescriptor(methodDecl);

            MethodEntry targetMethod = findMethod(original, methodName, descriptor);
            if (targetMethod == null) {
                continue;
            }

            try {
                IRMethod irMethod = lowerer.lower(methodDecl, ownerClass);
                ssa.lower(irMethod, targetMethod);
            } catch (Exception e) {
                throw new LoweringException("Failed to lower method " + methodName + ": " + e.getMessage(), e);
            }
        }

        try {
            original.rebuild();
        } catch (IOException e) {
            throw new LoweringException("Failed to rebuild class file: " + e.getMessage(), e);
        }
        return original;
    }

    private MethodEntry findMethod(ClassFile classFile, String name, String descriptor) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name) && method.getDesc().equals(descriptor)) {
                return method;
            }
        }
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private String buildDescriptor(MethodDecl methodDecl) {
        StringBuilder sb = new StringBuilder("(");
        for (var param : methodDecl.getParameters()) {
            sb.append(param.getType().toIRType().getDescriptor());
        }
        sb.append(")");
        sb.append(methodDecl.getReturnType().toIRType().getDescriptor());
        return sb.toString();
    }

    private void collectParseErrors(ParseErrorListener.CollectingErrorListener listener,
                                     String source, List<CompilationError> errors) {
        for (ParseException pe : listener.getErrors()) {
            errors.add(createErrorFromException(pe, source));
        }
    }

    private CompilationError createErrorFromException(ParseException e, String source) {
        int line = e.getLine();
        int column = e.getColumn();
        int offset = calculateOffset(source, line, column);
        int length = calculateErrorLength(source, line, column);
        return CompilationError.error(line, column, offset, length, e.getMessage());
    }

    private int calculateOffset(String source, int line, int column) {
        if (line <= 0 || column <= 0) {
            return 0;
        }

        String[] lines = source.split("\n", -1);
        int offset = 0;

        for (int i = 0; i < line - 1 && i < lines.length; i++) {
            offset += lines[i].length() + 1;
        }

        if (line - 1 < lines.length) {
            offset += Math.min(column - 1, lines[line - 1].length());
        }

        return offset;
    }

    private int calculateErrorLength(String source, int line, int column) {
        if (line <= 0) {
            return 1;
        }

        String[] lines = source.split("\n", -1);
        if (line - 1 >= lines.length) {
            return 1;
        }

        String errorLine = lines[line - 1];
        int startCol = Math.max(0, column - 1);

        if (startCol >= errorLine.length()) {
            return 1;
        }

        int endCol = startCol;
        while (endCol < errorLine.length() && !Character.isWhitespace(errorLine.charAt(endCol))) {
            endCol++;
        }

        return Math.max(1, endCol - startCol);
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
