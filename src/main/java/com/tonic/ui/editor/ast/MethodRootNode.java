package com.tonic.ui.editor.ast;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.parser.MethodEntry;
import com.tonic.ui.theme.Icons;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

public class MethodRootNode extends DefaultMutableTreeNode {

    private final String methodName;
    private final String methodDesc;
    private final int accessFlags;
    private final BlockStmt body;

    public MethodRootNode(MethodEntry method, BlockStmt body) {
        super(method.getName() + method.getDesc());
        this.methodName = method.getName();
        this.methodDesc = method.getDesc();
        this.accessFlags = method.getAccess();
        this.body = body;

        if (body != null) {
            add(ASTTreeNode.createNodeFor(body, null));
        }
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public BlockStmt getBody() {
        return body;
    }

    public boolean hasBody() {
        return body != null;
    }

    public String getDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append(formatAccessFlags());
        sb.append(methodName);
        sb.append(formatMethodDesc());
        return sb.toString();
    }

    private String formatAccessFlags() {
        StringBuilder sb = new StringBuilder();
        if ((accessFlags & 0x0001) != 0) sb.append("public ");
        if ((accessFlags & 0x0002) != 0) sb.append("private ");
        if ((accessFlags & 0x0004) != 0) sb.append("protected ");
        if ((accessFlags & 0x0008) != 0) sb.append("static ");
        if ((accessFlags & 0x0010) != 0) sb.append("final ");
        if ((accessFlags & 0x0020) != 0) sb.append("synchronized ");
        if ((accessFlags & 0x0100) != 0) sb.append("native ");
        if ((accessFlags & 0x0400) != 0) sb.append("abstract ");
        return sb.toString();
    }

    private String formatMethodDesc() {
        String desc = methodDesc;
        StringBuilder sb = new StringBuilder("(");

        int idx = 1;
        int argCount = 0;
        while (desc.charAt(idx) != ')') {
            if (argCount > 0) sb.append(", ");
            idx = appendType(desc, idx, sb);
            argCount++;
        }
        sb.append(")");

        idx++;
        sb.append(" : ");
        appendType(desc, idx, sb);

        return sb.toString();
    }

    private int appendType(String desc, int idx, StringBuilder sb) {
        int arrayDim = 0;
        while (desc.charAt(idx) == '[') {
            arrayDim++;
            idx++;
        }

        char c = desc.charAt(idx);
        switch (c) {
            case 'B': sb.append("byte"); idx++; break;
            case 'C': sb.append("char"); idx++; break;
            case 'D': sb.append("double"); idx++; break;
            case 'F': sb.append("float"); idx++; break;
            case 'I': sb.append("int"); idx++; break;
            case 'J': sb.append("long"); idx++; break;
            case 'S': sb.append("short"); idx++; break;
            case 'Z': sb.append("boolean"); idx++; break;
            case 'V': sb.append("void"); idx++; break;
            case 'L':
                int end = desc.indexOf(';', idx);
                String className = desc.substring(idx + 1, end);
                int lastSlash = className.lastIndexOf('/');
                sb.append(lastSlash >= 0 ? className.substring(lastSlash + 1) : className);
                idx = end + 1;
                break;
            default:
                sb.append("?");
                idx++;
        }

        for (int i = 0; i < arrayDim; i++) {
            sb.append("[]");
        }

        return idx;
    }

    public Icon getIcon() {
        if ((accessFlags & 0x0002) != 0) {
            return Icons.getIcon("method_private", 14);
        }
        if ((accessFlags & 0x0004) != 0) {
            return Icons.getIcon("method_protected", 14);
        }
        if ((accessFlags & 0x0001) != 0) {
            return Icons.getIcon("method_public", 14);
        }
        return Icons.getIcon("method_package", 14);
    }

    @Override
    public String toString() {
        return getDisplayText();
    }
}
