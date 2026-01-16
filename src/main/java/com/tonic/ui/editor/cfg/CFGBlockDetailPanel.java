package com.tonic.ui.editor.cfg;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.ui.core.component.ThemedJPanel;
import com.tonic.ui.editor.bytecode.BytecodeTokenMaker;
import com.tonic.ui.editor.ir.IRTokenMaker;
import com.tonic.ui.theme.JStudioTheme;
import com.tonic.ui.theme.SyntaxColors;
import com.tonic.ui.theme.Theme;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;

public class CFGBlockDetailPanel extends ThemedJPanel {

    private static final String SYNTAX_STYLE_BYTECODE = "text/bytecode";
    private static final String SYNTAX_STYLE_IR = "text/ir";

    static {
        AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance();
        atmf.putMapping(SYNTAX_STYLE_BYTECODE, "com.tonic.ui.editor.bytecode.BytecodeTokenMaker");
        atmf.putMapping(SYNTAX_STYLE_IR, "com.tonic.ui.editor.ir.IRTokenMaker");
    }

    private final RSyntaxTextArea textArea;
    private final RTextScrollPane scrollPane;
    private final JLabel headerLabel;

    private boolean currentShowIR = false;

    public CFGBlockDetailPanel() {
        super(BackgroundStyle.TERTIARY, new BorderLayout());

        headerLabel = new JLabel();
        headerLabel.setForeground(JStudioTheme.getTextPrimary());
        headerLabel.setFont(JStudioTheme.getUIFont(12).deriveFont(java.awt.Font.BOLD));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(headerLabel, BorderLayout.NORTH);

        textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SYNTAX_STYLE_BYTECODE);
        textArea.setEditable(false);
        textArea.setAntiAliasingEnabled(true);
        textArea.setFont(JStudioTheme.getCodeFont(12));
        textArea.setCodeFoldingEnabled(false);
        textArea.setBracketMatchingEnabled(false);

        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void showBlock(CFGBlockVertex vertex) {
        CFGBlock block = vertex.getBlock();
        boolean showIR = vertex.isShowIR();

        headerLabel.setText(String.format("Block %d (offset: %d - %d)",
                block.getId(), block.getStartOffset(), block.getEndOffset()));

        if (showIR != currentShowIR) {
            currentShowIR = showIR;
            textArea.setSyntaxEditingStyle(showIR ? SYNTAX_STYLE_IR : SYNTAX_STYLE_BYTECODE);
        }

        String content;
        if (showIR) {
            content = generateIRContent(vertex);
        } else {
            content = generateBytecodeContent(vertex);
        }

        textArea.setText(content);
        textArea.setCaretPosition(0);
        applyThemeColors();
    }

    private String generateBytecodeContent(CFGBlockVertex vertex) {
        StringBuilder sb = new StringBuilder();
        CFGBlock block = vertex.getBlock();

        if (block.isExceptionHandler()) {
            sb.append("// Exception handler: catch (").append(block.getHandlerType()).append(")\n\n");
        }

        if (block.getStartOffset() == 0) {
            sb.append("// Entry block\n\n");
        }

        for (Instruction instr : block.getInstructions()) {
            sb.append(String.format("%04d: %s\n", instr.getOffset(), instr));
        }

        return sb.toString();
    }

    private String generateIRContent(CFGBlockVertex vertex) {
        StringBuilder sb = new StringBuilder();
        CFGBlock block = vertex.getBlock();

        if (block.isExceptionHandler()) {
            sb.append("// Exception handler: catch (").append(block.getHandlerType()).append(")\n\n");
        }

        if (block.getStartOffset() == 0) {
            sb.append("// Entry block\n\n");
        }

        IRBlock irBlock = findMatchingIRBlock(vertex);
        if (irBlock == null) {
            sb.append("// No IR available for this block\n");
            return sb.toString();
        }

        sb.append("BLOCK ").append(irBlock.getId()).append(":\n");

        for (IRInstruction phi : irBlock.getPhiInstructions()) {
            sb.append("  ").append(phi.toString()).append("\n");
        }

        for (IRInstruction instr : irBlock.getInstructions()) {
            sb.append("  ").append(instr.toString()).append("\n");
        }

        return sb.toString();
    }

    private IRBlock findMatchingIRBlock(CFGBlockVertex vertex) {
        IRMethod irMethod = vertex.getIrMethod();
        if (irMethod == null) return null;

        CFGBlock block = vertex.getBlock();
        int targetOffset = block.getStartOffset();

        for (IRBlock irBlock : irMethod.getBlocks()) {
            if (irBlock.getBytecodeOffset() == targetOffset) {
                return irBlock;
            }
        }

        for (IRBlock irBlock : irMethod.getBlocks()) {
            int blockOffset = irBlock.getBytecodeOffset();
            if (blockOffset >= block.getStartOffset() && blockOffset < block.getEndOffset()) {
                return irBlock;
            }
        }

        return null;
    }

    @Override
    public void onThemeChanged(Theme newTheme) {
        super.onThemeChanged(newTheme);
        SwingUtilities.invokeLater(this::applyThemeColors);
    }

    @Override
    protected void applyChildThemes() {
        applyThemeColors();
    }

    private void applyThemeColors() {
        headerLabel.setForeground(JStudioTheme.getTextPrimary());

        textArea.setBackground(JStudioTheme.getBgTertiary());
        textArea.setForeground(JStudioTheme.getTextPrimary());
        textArea.setCaretColor(JStudioTheme.getTextPrimary());
        textArea.setSelectionColor(JStudioTheme.getSelection());
        textArea.setCurrentLineHighlightColor(JStudioTheme.getLineHighlight());
        textArea.setFadeCurrentLineHighlight(true);

        scrollPane.getGutter().setBackground(JStudioTheme.getBgSecondary());
        scrollPane.getGutter().setLineNumberColor(JStudioTheme.getTextSecondary());
        scrollPane.getGutter().setBorderColor(JStudioTheme.getBorder());

        SyntaxScheme scheme = textArea.getSyntaxScheme();

        setTokenStyle(scheme, Token.IDENTIFIER, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.WHITESPACE, JStudioTheme.getTextPrimary());
        setTokenStyle(scheme, Token.SEPARATOR, JStudioTheme.getTextSecondary());

        if (currentShowIR) {
            applyIRTheme(scheme);
        } else {
            applyBytecodeTheme(scheme);
        }

        repaint();
    }

    private void applyBytecodeTheme(SyntaxScheme scheme) {
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_COMMENT, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_DIVIDER, JStudioTheme.getAccentSecondary());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_HEADER, JStudioTheme.getAccent());

        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OFFSET, SyntaxColors.getBcOffset());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_LOAD, SyntaxColors.getBcLoad());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_STORE, SyntaxColors.getBcStore());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_INVOKE, SyntaxColors.getBcInvoke());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_FIELD, SyntaxColors.getBcField());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_BRANCH, SyntaxColors.getBcBranch());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_STACK, SyntaxColors.getBcStack());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_CONST, SyntaxColors.getBcConst());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_RETURN, SyntaxColors.getBcReturn());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_NEW, SyntaxColors.getBcNew());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_ARITHMETIC, SyntaxColors.getBcArithmetic());
        setTokenStyle(scheme, BytecodeTokenMaker.TOKEN_OPCODE_TYPE, SyntaxColors.getBcType());
    }

    private void applyIRTheme(SyntaxScheme scheme) {
        setTokenStyle(scheme, Token.OPERATOR, SyntaxColors.getIrOperator());
        setTokenStyle(scheme, Token.LITERAL_STRING_DOUBLE_QUOTE, SyntaxColors.getJavaString());
        setTokenStyle(scheme, Token.LITERAL_CHAR, SyntaxColors.getJavaString());

        setTokenStyle(scheme, IRTokenMaker.TOKEN_COMMENT, JStudioTheme.getTextSecondary());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_DIVIDER, JStudioTheme.getAccentSecondary());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_HEADER, JStudioTheme.getAccent());

        setTokenStyle(scheme, IRTokenMaker.TOKEN_BLOCK, SyntaxColors.getIrBlock());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_PHI, SyntaxColors.getIrPhi());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_VALUE, SyntaxColors.getIrValue());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_OPERATOR, SyntaxColors.getIrOperator());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_CONTROL, SyntaxColors.getIrControl());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_INVOKE, SyntaxColors.getIrInvoke());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_FIELD, SyntaxColors.getIrGetField());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_CONST, SyntaxColors.getIrConstant());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_NEW, SyntaxColors.getIrNew());
        setTokenStyle(scheme, IRTokenMaker.TOKEN_CAST, SyntaxColors.getIrCast());
    }

    private void setTokenStyle(SyntaxScheme scheme, int tokenType, Color color) {
        if (scheme.getStyle(tokenType) != null) {
            scheme.getStyle(tokenType).foreground = color;
        }
    }
}
