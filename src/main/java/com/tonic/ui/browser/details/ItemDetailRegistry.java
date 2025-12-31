package com.tonic.ui.browser.details;

import com.tonic.parser.constpool.*;

import java.util.HashMap;
import java.util.Map;

public class ItemDetailRegistry {

    private static final Map<Class<? extends Item<?>>, ItemFormatter<? extends Item<?>>> formatters = new HashMap<>();
    private static final Map<Class<? extends Item<?>>, String> typeNames = new HashMap<>();

    static {
        registerFormatters();
        registerTypeNames();
    }

    private static void registerFormatters() {
        register(Utf8Item.class, (item, sb, ctx) -> {
            String val = item.getValue();
            sb.append("Length: ").append(val.length()).append(" characters\n\n");
            sb.append("Value:\n").append(val).append("\n");
        });

        register(IntegerItem.class, (item, sb, ctx) -> {
            int val = item.getValue();
            sb.append("Decimal: ").append(val).append("\n");
            sb.append("Hex:     0x").append(Integer.toHexString(val)).append("\n");
            sb.append("Binary:  ").append(Integer.toBinaryString(val)).append("\n");
        });

        register(LongItem.class, (item, sb, ctx) -> {
            long val = item.getValue();
            sb.append("Decimal: ").append(val).append("L\n");
            sb.append("Hex:     0x").append(Long.toHexString(val)).append("\n");
        });

        register(FloatItem.class, (item, sb, ctx) -> {
            float val = item.getValue();
            sb.append("Value: ").append(val).append("f\n");
            sb.append("Raw bits: 0x").append(Integer.toHexString(Float.floatToRawIntBits(val))).append("\n");
        });

        register(DoubleItem.class, (item, sb, ctx) -> {
            double val = item.getValue();
            sb.append("Value: ").append(val).append("d\n");
            sb.append("Raw bits: 0x").append(Long.toHexString(Double.doubleToRawLongBits(val))).append("\n");
        });

        register(ClassRefItem.class, (item, sb, ctx) -> {
            int nameIdx = item.getNameIndex();
            sb.append("Name index: #").append(nameIdx).append("\n");
            sb.append("Resolved:   ").append(ctx.getUtf8(nameIdx)).append("\n");
        });

        register(StringRefItem.class, (item, sb, ctx) -> {
            int utf8Idx = item.getValue();
            sb.append("UTF8 index: #").append(utf8Idx).append("\n");
            sb.append("Value:      \"").append(ctx.getUtf8(utf8Idx)).append("\"\n");
        });

        register(FieldRefItem.class, (item, sb, ctx) -> appendMemberRefDetails(sb, "Field", item.getValue().getClassIndex(),
                item.getValue().getNameAndTypeIndex(), ctx));

        register(MethodRefItem.class, (item, sb, ctx) -> appendMemberRefDetails(sb, "Method", item.getValue().getClassIndex(),
                item.getValue().getNameAndTypeIndex(), ctx));

        register(InterfaceRefItem.class, (item, sb, ctx) -> appendMemberRefDetails(sb, "Interface Method", item.getValue().getClassIndex(),
                item.getValue().getNameAndTypeIndex(), ctx));

        register(NameAndTypeRefItem.class, (item, sb, ctx) -> {
            int nameIdx = item.getValue().getNameIndex();
            int descIdx = item.getValue().getDescriptorIndex();
            sb.append("Name index:       #").append(nameIdx).append("\n");
            sb.append("Descriptor index: #").append(descIdx).append("\n\n");
            sb.append("Name:       ").append(ctx.getUtf8(nameIdx)).append("\n");
            sb.append("Descriptor: ").append(ctx.getUtf8(descIdx)).append("\n");
        });

        register(MethodHandleItem.class, (item, sb, ctx) -> {
            int kind = item.getValue().getReferenceKind();
            int refIdx = item.getValue().getReferenceIndex();
            sb.append("Reference kind:  ").append(kind).append(" (").append(getHandleKindName(kind)).append(")\n");
            sb.append("Reference index: #").append(refIdx).append("\n");
        });

        register(MethodTypeItem.class, (item, sb, ctx) -> {
            int descIdx = item.getValue();
            sb.append("Descriptor index: #").append(descIdx).append("\n");
            sb.append("Descriptor:       ").append(ctx.getUtf8(descIdx)).append("\n");
        });

        register(InvokeDynamicItem.class, (item, sb, ctx) -> {
            int bsmIdx = item.getValue().getBootstrapMethodAttrIndex();
            int natIdx = item.getValue().getNameAndTypeIndex();
            sb.append("Bootstrap method index: ").append(bsmIdx).append("\n");
            sb.append("NameAndType index:      #").append(natIdx).append("\n");
            appendNameAndType(sb, natIdx, ctx);
        });

        register(ConstantDynamicItem.class, (item, sb, ctx) -> {
            int bsmIdx = item.getValue().getBootstrapMethodAttrIndex();
            int natIdx = item.getValue().getNameAndTypeIndex();
            sb.append("Bootstrap method index: ").append(bsmIdx).append("\n");
            sb.append("NameAndType index:      #").append(natIdx).append("\n");
            appendNameAndType(sb, natIdx, ctx);
        });

        register(PackageItem.class, (item, sb, ctx) -> {
            int nameIdx = item.getValue();
            sb.append("Name index: #").append(nameIdx).append("\n");
            sb.append("Package:    ").append(ctx.getUtf8(nameIdx)).append("\n");
        });

        register(ModuleItem.class, (item, sb, ctx) -> {
            int nameIdx = item.getValue();
            sb.append("Name index: #").append(nameIdx).append("\n");
            sb.append("Module:     ").append(ctx.getUtf8(nameIdx)).append("\n");
        });
    }

    private static void registerTypeNames() {
        typeNames.put(Utf8Item.class, "CONSTANT_Utf8");
        typeNames.put(IntegerItem.class, "CONSTANT_Integer");
        typeNames.put(FloatItem.class, "CONSTANT_Float");
        typeNames.put(LongItem.class, "CONSTANT_Long");
        typeNames.put(DoubleItem.class, "CONSTANT_Double");
        typeNames.put(ClassRefItem.class, "CONSTANT_Class");
        typeNames.put(StringRefItem.class, "CONSTANT_String");
        typeNames.put(FieldRefItem.class, "CONSTANT_Fieldref");
        typeNames.put(MethodRefItem.class, "CONSTANT_Methodref");
        typeNames.put(InterfaceRefItem.class, "CONSTANT_InterfaceMethodref");
        typeNames.put(NameAndTypeRefItem.class, "CONSTANT_NameAndType");
        typeNames.put(MethodHandleItem.class, "CONSTANT_MethodHandle");
        typeNames.put(MethodTypeItem.class, "CONSTANT_MethodType");
        typeNames.put(ConstantDynamicItem.class, "CONSTANT_Dynamic");
        typeNames.put(InvokeDynamicItem.class, "CONSTANT_InvokeDynamic");
        typeNames.put(PackageItem.class, "CONSTANT_Package");
        typeNames.put(ModuleItem.class, "CONSTANT_Module");
    }

    private static <T extends Item<?>> void register(Class<T> type, ItemFormatter<T> formatter) {
        formatters.put(type, formatter);
    }

    @SuppressWarnings("unchecked")
    public static void format(Item<?> item, StringBuilder sb, DetailContext ctx) {
        try {
            ItemFormatter<Item<?>> formatter = (ItemFormatter<Item<?>>) formatters.get(item.getClass());
            if (formatter != null) {
                formatter.format(item, sb, ctx);
            } else {
                sb.append("Value: ").append(item.getValue()).append("\n");
            }
        } catch (Exception e) {
            sb.append("Error reading item: ").append(e.getMessage()).append("\n");
        }
    }

    public static String getTypeName(Item<?> item) {
        return typeNames.getOrDefault(item.getClass(), "Unknown");
    }

    private static void appendMemberRefDetails(StringBuilder sb, String kind, int classIdx, int natIdx, DetailContext ctx) {
        sb.append(kind).append(" Reference\n\n");
        sb.append("Class index:       #").append(classIdx).append("\n");
        sb.append("NameAndType index: #").append(natIdx).append("\n\n");

        try {
            Item<?> classItem = ctx.getItem(classIdx);
            if (classItem instanceof ClassRefItem) {
                int nameIdx = ((ClassRefItem) classItem).getNameIndex();
                sb.append("Class: ").append(ctx.getUtf8(nameIdx)).append("\n");
            }
            appendNameAndType(sb, natIdx, ctx);
        } catch (Exception e) {
            sb.append("Error resolving: ").append(e.getMessage()).append("\n");
        }
    }

    private static void appendNameAndType(StringBuilder sb, int natIdx, DetailContext ctx) {
        try {
            Item<?> natItem = ctx.getItem(natIdx);
            if (natItem instanceof NameAndTypeRefItem) {
                NameAndTypeRefItem nat = (NameAndTypeRefItem) natItem;
                sb.append("Name:       ").append(ctx.getUtf8(nat.getValue().getNameIndex())).append("\n");
                sb.append("Descriptor: ").append(ctx.getUtf8(nat.getValue().getDescriptorIndex())).append("\n");
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }

    private static String getHandleKindName(int kind) {
        switch (kind) {
            case 1: return "REF_getField";
            case 2: return "REF_getStatic";
            case 3: return "REF_putField";
            case 4: return "REF_putStatic";
            case 5: return "REF_invokeVirtual";
            case 6: return "REF_invokeStatic";
            case 7: return "REF_invokeSpecial";
            case 8: return "REF_newInvokeSpecial";
            case 9: return "REF_invokeInterface";
            default: return "Unknown";
        }
    }
}
