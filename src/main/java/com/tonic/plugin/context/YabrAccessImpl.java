package com.tonic.plugin.context;

import com.tonic.analysis.DisassemblyOptions;
import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.dataflow.DataFlowGraph;
import com.tonic.analysis.source.ast.ASTPrinter;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.plugin.api.YabrAccess;
import com.tonic.ui.editor.ir.IRFormatter;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class YabrAccessImpl implements YabrAccess {

    private final ProjectModel projectModel;
    private final ClassPoolImpl classPoolWrapper;

    public YabrAccessImpl(ProjectModel projectModel) {
        this.projectModel = projectModel;
        this.classPoolWrapper = new ClassPoolImpl();
    }

    @Override
    public ClassPool getClassPool() {
        return classPoolWrapper;
    }

    @Override
    public Optional<Object> getClassFile(String name) {
        ClassEntryModel entry = projectModel.findClassByName(name);
        return entry != null ? Optional.of(entry.getClassFile()) : Optional.empty();
    }

    @Override
    public Optional<Object> liftToIR(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) return Optional.empty();

        MethodEntryModel methodEntry = classEntry.getMethod(methodName, descriptor);
        if (methodEntry == null) return Optional.empty();

        if (methodEntry.getCachedIR() != null) {
            return Optional.of(methodEntry.getCachedIR());
        }

        return Optional.empty();
    }

    @Override
    public Object buildCallGraph() {
        com.tonic.parser.ClassPool pool = projectModel.getClassPool();
        if (pool == null) return null;
        return CallGraph.build(pool);
    }

    @Override
    public Object buildDataFlowGraph(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) return null;

        MethodEntryModel methodModel = classEntry.getMethod(methodName, descriptor);
        if (methodModel == null) return null;

        MethodEntry method = methodModel.getMethodEntry();
        if (method == null || method.getCodeAttribute() == null) return null;

        try {
            SSA ssa = new SSA(
                classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(method);
            if (irMethod == null || irMethod.getEntryBlock() == null) return null;

            DataFlowGraph dfg =
                new DataFlowGraph(irMethod);
            dfg.build();
            return dfg;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object getMethodEntry(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) return null;

        MethodEntryModel methodModel = classEntry.getMethod(methodName, descriptor);
        return methodModel != null ? methodModel.getMethodEntry() : null;
    }

    @Override
    public Optional<String> disassembleMethod(String className, String methodName, String descriptor, boolean verbose) {
        MethodEntry method = resolveMethod(className, methodName, descriptor);
        if (method == null) {
            return Optional.empty();
        }
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return Optional.empty();
        }
        DisassemblyOptions options = verbose ? DisassemblyOptions.verbose() : DisassemblyOptions.terse();
        return Optional.of(code.prettyPrintCode(options));
    }

    @Override
    public Optional<String> methodSsaIr(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) {
            return Optional.empty();
        }
        MethodEntryModel methodModel = classEntry.getMethod(methodName, descriptor);
        if (methodModel == null || methodModel.getMethodEntry() == null) {
            return Optional.empty();
        }
        SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
        return Optional.of(new IRFormatter(methodModel.getMethodEntry(), ssa).format());
    }

    @Override
    public Optional<String> methodAst(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) {
            return Optional.empty();
        }
        MethodEntryModel methodModel = classEntry.getMethod(methodName, descriptor);
        if (methodModel == null || methodModel.getMethodEntry() == null
                || methodModel.getMethodEntry().getCodeAttribute() == null) {
            return Optional.empty();
        }
        try {
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod ir = ssa.lift(methodModel.getMethodEntry());
            BlockStmt body = MethodRecoverer.recoverMethod(ir, methodModel.getMethodEntry());
            return body != null ? Optional.of(ASTPrinter.format(body)) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private MethodEntry resolveMethod(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) {
            return null;
        }
        MethodEntryModel methodModel = classEntry.getMethod(methodName, descriptor);
        return methodModel != null ? methodModel.getMethodEntry() : null;
    }

    @Override
    public List<String> getLoadedClassNames() {
        List<String> names = new ArrayList<>();
        for (ClassEntryModel entry : projectModel.getAllClasses()) {
            names.add(entry.getClassName());
        }
        return names;
    }

    @Override
    public byte[] getClassBytes(String className) {
        ClassEntryModel entry = projectModel.findClassByName(className);
        if (entry == null) return new byte[0];
        try {
            ClassFile cf = entry.getClassFile();
            return cf != null ? cf.write() : new byte[0];
        } catch (Exception e) {
            return new byte[0];
        }
    }

    @Override
    public void addClass(String name, byte[] bytecode) {
        try {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(bytecode));
            projectModel.addClass(cf);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add class: " + name, e);
        }
    }

    @Override
    public void removeClass(String name) {
        com.tonic.parser.ClassPool pool = projectModel.getClassPool();
        if (pool != null) {
            pool.getClasses().removeIf(cf -> cf.getClassName().equals(name));
        }
    }

    private class ClassPoolImpl implements ClassPool {

        @Override
        public List<String> getClassNames() {
            return getLoadedClassNames();
        }

        @Override
        public boolean hasClass(String name) {
            return projectModel.findClassByName(name) != null;
        }

        @Override
        public byte[] getClassBytes(String name) {
            return YabrAccessImpl.this.getClassBytes(name);
        }

        @Override
        public Object getClassNode(String name) {
            return getClassFile(name).orElse(null);
        }

        @Override
        public void putClass(String name, byte[] bytecode) {
            addClass(name, bytecode);
        }

        @Override
        public void putClassNode(String name, Object classNode) {
            if (classNode instanceof ClassFile) {
                projectModel.addClass((ClassFile) classNode);
            }
        }

        @Override
        public void clear() {
            projectModel.clear();
        }

        @Override
        public int size() {
            return projectModel.getClassCount();
        }
    }
}
