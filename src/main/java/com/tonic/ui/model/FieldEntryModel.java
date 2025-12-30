package com.tonic.ui.model;

import com.tonic.parser.FieldEntry;
import com.tonic.ui.theme.Icons;
import lombok.Getter;
import lombok.Setter;

import javax.swing.Icon;

@Getter
public class FieldEntryModel {

    private final FieldEntry fieldEntry;
    private final ClassEntryModel owner;

    // UI state
    @Setter
    private boolean selected;
    @Setter
    private String userNotes;

    // Display data
    private String displayType;
    private Icon icon;

    public FieldEntryModel(FieldEntry fieldEntry, ClassEntryModel owner) {
        this.fieldEntry = fieldEntry;
        this.owner = owner;
        buildDisplayData();
    }

    private void buildDisplayData() {
        this.displayType = formatDescriptor(fieldEntry.getDesc());
        this.icon = Icons.getIcon("field");
    }

    private String formatDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "?";
        }

        StringBuilder result = new StringBuilder();
        int i = 0;

        // Count array dimensions
        int arrayDim = 0;
        while (i < desc.length() && desc.charAt(i) == '[') {
            arrayDim++;
            i++;
        }

        if (i < desc.length()) {
            char c = desc.charAt(i);
            switch (c) {
                case 'B': result.append("byte"); break;
                case 'C': result.append("char"); break;
                case 'D': result.append("double"); break;
                case 'F': result.append("float"); break;
                case 'I': result.append("int"); break;
                case 'J': result.append("long"); break;
                case 'S': result.append("short"); break;
                case 'Z': result.append("boolean"); break;
                case 'V': result.append("void"); break;
                case 'L':
                    int semicolon = desc.indexOf(';', i);
                    if (semicolon > i) {
                        String className = desc.substring(i + 1, semicolon);
                        int lastSlash = className.lastIndexOf('/');
                        if (lastSlash >= 0) {
                            result.append(className.substring(lastSlash + 1));
                        } else {
                            result.append(className);
                        }
                    }
                    break;
                default:
                    result.append(desc);
                    break;
            }
        }

        // Add array brackets
        for (int d = 0; d < arrayDim; d++) {
            result.append("[]");
        }

        return result.toString();
    }

    // FieldEntry delegated methods

    public String getName() {
        return fieldEntry.getName();
    }

    public String getDescriptor() {
        return fieldEntry.getDesc();
    }

    public int getAccessFlags() {
        return fieldEntry.getAccess();
    }

    public boolean isStatic() {
        return (fieldEntry.getAccess() & 0x0008) != 0;
    }

    public boolean isFinal() {
        return (fieldEntry.getAccess() & 0x0010) != 0;
    }

    public boolean isPublic() {
        return (fieldEntry.getAccess() & 0x0001) != 0;
    }

    public boolean isPrivate() {
        return (fieldEntry.getAccess() & 0x0002) != 0;
    }

    public boolean isProtected() {
        return (fieldEntry.getAccess() & 0x0004) != 0;
    }

    public boolean isVolatile() {
        return (fieldEntry.getAccess() & 0x0040) != 0;
    }

    public boolean isTransient() {
        return (fieldEntry.getAccess() & 0x0080) != 0;
    }

    @Override
    public String toString() {
        return displayType + " " + fieldEntry.getName();
    }
}
