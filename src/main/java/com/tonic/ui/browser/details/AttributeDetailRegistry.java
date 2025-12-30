package com.tonic.ui.browser.details;

import com.tonic.parser.attribute.*;

import java.util.HashMap;
import java.util.Map;

public class AttributeDetailRegistry {

    private static final Map<Class<? extends Attribute>, AttributeFormatter<? extends Attribute>> formatters = new HashMap<>();
    private static final Map<Class<? extends Attribute>, String> typeNames = new HashMap<>();

    static {
        registerFormatters();
        registerTypeNames();
    }

    @SuppressWarnings("unchecked")
    private static void registerFormatters() {
        register(CodeAttribute.class, (attr, sb, ctx) -> {
            sb.append("Max stack:  ").append(attr.getMaxStack()).append("\n");
            sb.append("Max locals: ").append(attr.getMaxLocals()).append("\n");
            sb.append("Code length: ").append(attr.getCode() != null ? attr.getCode().length : 0).append(" bytes\n");
            sb.append("Exception handlers: ").append(attr.getExceptionTable() != null ? attr.getExceptionTable().size() : 0).append("\n");
            if (attr.getAttributes() != null) {
                sb.append("Nested attributes: ").append(attr.getAttributes().size()).append("\n");
                for (Attribute nested : attr.getAttributes()) {
                    sb.append("  - ").append(getTypeName(nested)).append("\n");
                }
            }
        });

        register(SourceFileAttribute.class, (attr, sb, ctx) -> {
            sb.append("Source file index: #").append(attr.getSourceFileIndex()).append("\n");
            sb.append("Source file: ").append(ctx.getUtf8(attr.getSourceFileIndex())).append("\n");
        });

        register(ConstantValueAttribute.class, (attr, sb, ctx) -> {
            sb.append("Value index: #").append(attr.getConstantValueIndex()).append("\n");
        });

        register(ExceptionsAttribute.class, (attr, sb, ctx) -> {
            sb.append("Throws ").append(attr.getExceptionIndexTable().size()).append(" exception(s):\n");
            for (int idx : attr.getExceptionIndexTable()) {
                sb.append("  - #").append(idx).append("\n");
            }
        });

        register(InnerClassesAttribute.class, (attr, sb, ctx) -> {
            sb.append("Inner classes: ").append(attr.getClasses().size()).append("\n");
        });

        register(LineNumberTableAttribute.class, (attr, sb, ctx) -> {
            sb.append("Entries: ").append(attr.getLineNumberTable().size()).append("\n");
            int count = 0;
            for (var entry : attr.getLineNumberTable()) {
                sb.append("  pc=").append(entry.getStartPc()).append(" → line ").append(entry.getLineNumber()).append("\n");
                if (++count >= 20) {
                    sb.append("  ... (").append(attr.getLineNumberTable().size() - 20).append(" more)\n");
                    break;
                }
            }
        });

        register(LocalVariableTableAttribute.class, (attr, sb, ctx) -> {
            sb.append("Variables: ").append(attr.getLocalVariableTable().size()).append("\n");
            int count = 0;
            for (var entry : attr.getLocalVariableTable()) {
                String name = ctx.getUtf8(entry.getNameIndex());
                String desc = ctx.getUtf8(entry.getDescriptorIndex());
                sb.append("  slot ").append(entry.getIndex()).append(": ").append(name).append(" : ").append(desc).append("\n");
                if (++count >= 15) {
                    sb.append("  ... (").append(attr.getLocalVariableTable().size() - 15).append(" more)\n");
                    break;
                }
            }
        });

        register(SignatureAttribute.class, (attr, sb, ctx) -> {
            sb.append("Signature index: #").append(attr.getSignatureIndex()).append("\n");
            sb.append("Signature: ").append(ctx.getUtf8(attr.getSignatureIndex())).append("\n");
        });

        register(DeprecatedAttribute.class, (attr, sb, ctx) -> {
            sb.append("This element is deprecated.\n");
        });

        register(SyntheticAttribute.class, (attr, sb, ctx) -> {
            sb.append("This element is synthetic (compiler-generated).\n");
        });
    }

    private static void registerTypeNames() {
        typeNames.put(CodeAttribute.class, "Code");
        typeNames.put(ConstantValueAttribute.class, "ConstantValue");
        typeNames.put(StackMapTableAttribute.class, "StackMapTable");
        typeNames.put(ExceptionsAttribute.class, "Exceptions");
        typeNames.put(InnerClassesAttribute.class, "InnerClasses");
        typeNames.put(EnclosingMethodAttribute.class, "EnclosingMethod");
        typeNames.put(SyntheticAttribute.class, "Synthetic");
        typeNames.put(SignatureAttribute.class, "Signature");
        typeNames.put(SourceFileAttribute.class, "SourceFile");
        typeNames.put(SourceDebugExtensionAttribute.class, "SourceDebugExtension");
        typeNames.put(LineNumberTableAttribute.class, "LineNumberTable");
        typeNames.put(LocalVariableTableAttribute.class, "LocalVariableTable");
        typeNames.put(LocalVariableTypeTableAttribute.class, "LocalVariableTypeTable");
        typeNames.put(DeprecatedAttribute.class, "Deprecated");
        typeNames.put(RuntimeVisibleAnnotationsAttribute.class, "RuntimeVisibleAnnotations");
        typeNames.put(RuntimeInvisibleAnnotationsAttribute.class, "RuntimeInvisibleAnnotations");
        typeNames.put(RuntimeVisibleParameterAnnotationsAttribute.class, "RuntimeVisibleParameterAnnotations");
        typeNames.put(AnnotationDefaultAttribute.class, "AnnotationDefault");
        typeNames.put(MethodParametersAttribute.class, "MethodParameters");
        typeNames.put(BootstrapMethodsAttribute.class, "BootstrapMethods");
        typeNames.put(ModuleAttribute.class, "Module");
        typeNames.put(NestHostAttribute.class, "NestHost");
        typeNames.put(NestMembersAttribute.class, "NestMembers");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Attribute> void register(Class<T> type, AttributeFormatter<T> formatter) {
        formatters.put(type, formatter);
    }

    @SuppressWarnings("unchecked")
    public static void format(Attribute attr, StringBuilder sb, DetailContext ctx) {
        AttributeFormatter<Attribute> formatter = (AttributeFormatter<Attribute>) formatters.get(attr.getClass());
        if (formatter != null) {
            formatter.format(attr, sb, ctx);
        } else {
            sb.append("(Generic attribute - no detailed view available)\n");
        }
    }

    public static String getTypeName(Attribute attr) {
        return typeNames.getOrDefault(attr.getClass(), "Attribute");
    }
}
