package com.tonic.ui.theme;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.function.Supplier;

/**
 * Icon provider for JStudio.
 * Uses programmatically drawn icons that fetch colors at paint time for theme support.
 */
public class Icons {

    private static final int DEFAULT_SIZE = 16;

    public static Icon getIcon(String name) {
        return getIcon(name, DEFAULT_SIZE);
    }

    public static Icon getIcon(String name, int size) {
        return createIcon(name, size);
    }

    private static Icon createIcon(String name, int size) {
        switch (name) {
            case "open":
                return new FolderOpenIcon(size);
            case "save":
                return new SaveIcon(size);
            case "close":
                return new CloseIcon(size);
            case "refresh":
                return new RefreshIcon(size);
            case "back":
                return new ArrowIcon(size, true);
            case "forward":
                return new ArrowIcon(size, false);
            case "search":
                return new SearchIcon(size);
            case "home":
                return new HomeIcon(size);
            case "folder":
                return new FolderIcon(size);
            case "settings":
                return new SettingsIcon(size);
            case "source":
                return new CodeIcon(size);
            case "bytecode":
                return new BinaryIcon(size);
            case "ir":
                return new FlowIcon(size);
            case "class":
                return new LetterIcon(size, "C", JStudioTheme::getAccent);
            case "interface":
                return new LetterIcon(size, "I", JStudioTheme::getAccentSecondary);
            case "enum":
                return new LetterIcon(size, "E", JStudioTheme::getWarning);
            case "annotation":
                return new LetterIcon(size, "@", JStudioTheme::getWarning);
            case "method_public":
                return new MethodIcon(size, JStudioTheme::getSuccess);
            case "method_private":
                return new MethodIcon(size, JStudioTheme::getError);
            case "method_protected":
                return new MethodIcon(size, JStudioTheme::getWarning);
            case "method_package":
                return new MethodIcon(size, JStudioTheme::getInfo);
            case "field":
                return new LetterIcon(size, "F", JStudioTheme::getInfo);
            case "constructor":
                return new LetterIcon(size, "C", JStudioTheme::getSuccess);
            case "package":
                return new PackageIcon(size);
            case "analyze":
                return new PlayIcon(size);
            case "play":
            case "run":
                return new PlayIcon(size);
            case "console":
                return new ConsoleIcon(size);
            case "debug":
                return new DebugIcon(size);
            case "callgraph":
                return new GraphIcon(size);
            case "dependency":
                return new DependencyIcon(size);
            case "transform":
                return new TransformIcon(size);
            case "success":
                return new CheckIcon(size, JStudioTheme::getSuccess);
            case "warning":
                return new WarningTriangleIcon(size);
            case "error":
                return new ErrorCircleIcon(size);
            case "info":
                return new InfoIcon(size);
            case "bookmark":
                return new BookmarkIcon(size);
            case "comment":
                return new CommentIcon(size);
            case "delete":
                return new DeleteIcon(size);
            case "edit":
                return new EditIcon(size);
            case "add":
                return new AddIcon(size);
            case "copy":
                return new CopyIcon(size);
            case "opaque":
                return new OpaquePredicateIcon(size);
            case "dead_code":
                return new DeadCodeIcon(size);
            case "browser":
                return new BrowserIcon(size);
            case "constpool":
                return new ConstPoolIcon(size);
            case "heap":
                return new HeapIcon(size);
            default:
                return new PlaceholderIcon(size);
        }
    }

    private static abstract class BaseIcon implements Icon {
        protected final int size;

        BaseIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            paintIconContent(g2);
            g2.dispose();
        }

        protected abstract void paintIconContent(Graphics2D g2);

        protected BasicStroke getStroke() {
            return new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
    }

    private static class LetterIcon extends BaseIcon {
        private final String letter;
        private final Supplier<Color> colorSupplier;

        LetterIcon(int size, String letter, Supplier<Color> colorSupplier) {
            super(size);
            this.letter = letter;
            this.colorSupplier = colorSupplier;
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            int inset = 2;
            int rectSize = size - inset * 2;

            g2.setColor(colorSupplier.get());
            g2.setStroke(getStroke());
            g2.drawRoundRect(inset, inset, rectSize, rectSize, 4, 4);

            g2.setFont(JStudioTheme.getUIFont(size - 4).deriveFont(java.awt.Font.BOLD));
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int textX = (size - fm.stringWidth(letter)) / 2;
            int textY = (size + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(letter, textX, textY);
        }
    }

    private static class MethodIcon extends BaseIcon {
        private final Supplier<Color> colorSupplier;

        MethodIcon(int size, Supplier<Color> colorSupplier) {
            super(size);
            this.colorSupplier = colorSupplier;
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            int inset = 3;
            g2.setColor(colorSupplier.get());
            g2.fillOval(inset, inset, size - inset * 2, size - inset * 2);
        }
    }

    private static class FolderOpenIcon extends BaseIcon {
        FolderOpenIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            Path2D path = new Path2D.Float();
            path.moveTo(2, 4);
            path.lineTo(2, 13);
            path.lineTo(14, 13);
            path.lineTo(14, 6);
            path.lineTo(8, 6);
            path.lineTo(6, 4);
            path.closePath();
            g2.draw(path);
            g2.drawLine(2, 6, 6, 6);
        }
    }

    private static class SaveIcon extends BaseIcon {
        SaveIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawRoundRect(2, 2, 12, 12, 2, 2);
            g2.drawRect(5, 2, 6, 4);
            g2.drawRect(4, 9, 8, 5);
        }
    }

    private static class CloseIcon extends BaseIcon {
        CloseIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            int inset = 4;
            g2.drawLine(inset, inset, size - inset, size - inset);
            g2.drawLine(size - inset, inset, inset, size - inset);
        }
    }

    private static class RefreshIcon extends BaseIcon {
        RefreshIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            int inset = 2;
            g2.drawArc(inset, inset, size - inset * 2, size - inset * 2, 45, 270);
            g2.drawLine(size - 3, 3, size - 3, 6);
            g2.drawLine(size - 3, 3, size - 6, 3);
        }
    }

    private static class ArrowIcon extends BaseIcon {
        private final boolean left;

        ArrowIcon(int size, boolean left) {
            super(size);
            this.left = left;
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            int mid = size / 2;
            if (left) {
                g2.drawLine(10, mid, 4, mid);
                g2.drawLine(4, mid, 7, mid - 3);
                g2.drawLine(4, mid, 7, mid + 3);
            } else {
                g2.drawLine(6, mid, 12, mid);
                g2.drawLine(12, mid, 9, mid - 3);
                g2.drawLine(12, mid, 9, mid + 3);
            }
        }
    }

    private static class SearchIcon extends BaseIcon {
        SearchIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawOval(3, 3, 8, 8);
            g2.drawLine(10, 10, 13, 13);
        }
    }

    private static class CodeIcon extends BaseIcon {
        CodeIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawLine(5, 4, 3, 8);
            g2.drawLine(3, 8, 5, 12);
            g2.drawLine(11, 4, 13, 8);
            g2.drawLine(13, 8, 11, 12);
        }
    }

    private static class BinaryIcon extends BaseIcon {
        BinaryIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setFont(JStudioTheme.getCodeFont(9));
            g2.drawString("01", 3, 10);
        }
    }

    private static class FlowIcon extends BaseIcon {
        FlowIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawRoundRect(5, 2, 6, 3, 1, 1);
            g2.drawRoundRect(2, 11, 5, 3, 1, 1);
            g2.drawRoundRect(9, 11, 5, 3, 1, 1);
            g2.drawLine(8, 5, 8, 8);
            g2.drawLine(8, 8, 4, 11);
            g2.drawLine(8, 8, 12, 11);
        }
    }

    private static class PackageIcon extends BaseIcon {
        PackageIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getWarning());
            g2.setStroke(getStroke());
            Path2D path = new Path2D.Float();
            path.moveTo(2, 5);
            path.lineTo(2, 13);
            path.lineTo(14, 13);
            path.lineTo(14, 7);
            path.lineTo(8, 7);
            path.lineTo(6, 5);
            path.closePath();
            g2.draw(path);
        }
    }

    private static class PlayIcon extends BaseIcon {
        PlayIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getSuccess());
            Path2D path = new Path2D.Float();
            path.moveTo(4, 3);
            path.lineTo(4, 13);
            path.lineTo(13, 8);
            path.closePath();
            g2.fill(path);
        }
    }

    private static class GraphIcon extends BaseIcon {
        GraphIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.fillOval(2, 6, 4, 4);
            g2.fillOval(6, 2, 4, 4);
            g2.fillOval(6, 10, 4, 4);
            g2.fillOval(10, 6, 4, 4);
            g2.drawLine(5, 8, 7, 5);
            g2.drawLine(5, 8, 7, 11);
            g2.drawLine(9, 5, 11, 7);
            g2.drawLine(9, 11, 11, 9);
        }
    }

    private static class DependencyIcon extends BaseIcon {
        DependencyIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawRect(2, 2, 5, 5);
            g2.drawRect(9, 9, 5, 5);
            g2.drawLine(7, 5, 9, 9);
        }
    }

    private static class TransformIcon extends BaseIcon {
        TransformIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getAccentSecondary());
            g2.setStroke(getStroke());
            g2.drawLine(3, 13, 13, 3);
            g2.fillOval(11, 1, 4, 4);
            g2.drawLine(2, 2, 4, 4);
            g2.drawLine(6, 2, 6, 4);
            g2.drawLine(2, 6, 4, 6);
        }
    }

    private static class CheckIcon extends BaseIcon {
        private final Supplier<Color> colorSupplier;

        CheckIcon(int size, Supplier<Color> colorSupplier) {
            super(size);
            this.colorSupplier = colorSupplier;
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(colorSupplier.get());
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(3, 8, 6, 11);
            g2.drawLine(6, 11, 13, 4);
        }
    }

    private static class WarningTriangleIcon extends BaseIcon {
        WarningTriangleIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getWarning());
            Path2D path = new Path2D.Float();
            path.moveTo(8, 2);
            path.lineTo(14, 13);
            path.lineTo(2, 13);
            path.closePath();
            g2.fill(path);
            g2.setColor(JStudioTheme.getBgPrimary());
            g2.setFont(JStudioTheme.getUIFont(10).deriveFont(java.awt.Font.BOLD));
            g2.drawString("!", 6, 12);
        }
    }

    private static class ErrorCircleIcon extends BaseIcon {
        ErrorCircleIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getError());
            g2.fillOval(2, 2, 12, 12);
            g2.setColor(JStudioTheme.getBgPrimary());
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(5, 5, 11, 11);
            g2.drawLine(11, 5, 5, 11);
        }
    }

    private static class InfoIcon extends BaseIcon {
        InfoIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getInfo());
            g2.fillOval(2, 2, 12, 12);
            g2.setColor(JStudioTheme.getBgPrimary());
            g2.setFont(JStudioTheme.getUIFont(10).deriveFont(java.awt.Font.BOLD));
            g2.drawString("i", 7, 12);
        }
    }

    private static class HomeIcon extends BaseIcon {
        HomeIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            Path2D roof = new Path2D.Float();
            roof.moveTo(8, 2);
            roof.lineTo(2, 7);
            roof.lineTo(14, 7);
            roof.closePath();
            g2.draw(roof);
            g2.drawRect(3, 7, 10, 7);
            g2.drawRect(6, 9, 4, 5);
        }
    }

    private static class FolderIcon extends BaseIcon {
        FolderIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getWarning());
            g2.setStroke(getStroke());
            Path2D path = new Path2D.Float();
            path.moveTo(2, 4);
            path.lineTo(2, 13);
            path.lineTo(14, 13);
            path.lineTo(14, 6);
            path.lineTo(8, 6);
            path.lineTo(6, 4);
            path.closePath();
            g2.draw(path);
        }
    }

    private static class SettingsIcon extends BaseIcon {
        SettingsIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            int cx = size / 2;
            int cy = size / 2;
            int outerR = 6;
            int innerR = 3;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                int x1 = cx + (int)(Math.cos(angle) * innerR);
                int y1 = cy + (int)(Math.sin(angle) * innerR);
                int x2 = cx + (int)(Math.cos(angle) * outerR);
                int y2 = cy + (int)(Math.sin(angle) * outerR);
                g2.drawLine(x1, y1, x2, y2);
            }
            g2.drawOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
        }
    }

    private static class BookmarkIcon extends BaseIcon {
        BookmarkIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getWarning());
            Path2D path = new Path2D.Float();
            path.moveTo(3, 2);
            path.lineTo(3, 14);
            path.lineTo(8, 10);
            path.lineTo(13, 14);
            path.lineTo(13, 2);
            path.closePath();
            g2.fill(path);
        }
    }

    private static class CommentIcon extends BaseIcon {
        CommentIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getInfo());
            g2.setStroke(getStroke());
            g2.drawRoundRect(2, 2, 12, 9, 3, 3);
            g2.drawLine(5, 11, 3, 14);
            g2.drawLine(3, 5, 13, 5);
            g2.drawLine(3, 8, 10, 8);
        }
    }

    private static class DeleteIcon extends BaseIcon {
        DeleteIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getError());
            g2.setStroke(getStroke());
            g2.drawLine(3, 4, 13, 4);
            g2.drawLine(5, 4, 5, 2);
            g2.drawLine(11, 4, 11, 2);
            g2.drawLine(5, 2, 11, 2);
            g2.drawRect(4, 4, 8, 10);
            g2.drawLine(6, 7, 6, 12);
            g2.drawLine(8, 7, 8, 12);
            g2.drawLine(10, 7, 10, 12);
        }
    }

    private static class EditIcon extends BaseIcon {
        EditIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getAccent());
            g2.setStroke(getStroke());
            g2.drawLine(3, 13, 13, 3);
            g2.drawLine(11, 3, 13, 3);
            g2.drawLine(13, 3, 13, 5);
            g2.drawLine(3, 13, 5, 13);
            g2.drawLine(3, 13, 3, 11);
        }
    }

    private static class AddIcon extends BaseIcon {
        AddIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getSuccess());
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int mid = size / 2;
            g2.drawLine(mid, 3, mid, 13);
            g2.drawLine(3, mid, 13, mid);
        }
    }

    private static class CopyIcon extends BaseIcon {
        CopyIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawRoundRect(4, 1, 9, 11, 2, 2);
            g2.drawRoundRect(2, 4, 9, 11, 2, 2);
        }
    }

    private static class PlaceholderIcon extends BaseIcon {
        PlaceholderIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.setStroke(getStroke());
            g2.drawRect(2, 2, size - 4, size - 4);
            g2.drawLine(2, 2, size - 2, size - 2);
            g2.drawLine(size - 2, 2, 2, size - 2);
        }
    }

    private static class OpaquePredicateIcon extends BaseIcon {
        OpaquePredicateIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(new Color(255, 180, 100));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval(2, 2, 12, 12);
            g2.drawLine(8, 4, 8, 9);
            g2.fillOval(7, 11, 2, 2);
        }
    }

    private static class DeadCodeIcon extends BaseIcon {
        DeadCodeIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(new Color(128, 128, 128));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(3, 3, 13, 13);
            g2.drawLine(13, 3, 3, 13);
        }
    }

    private static class BrowserIcon extends BaseIcon {
        BrowserIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getAccent());
            g2.setStroke(getStroke());
            g2.drawRoundRect(2, 2, 12, 12, 2, 2);
            g2.drawLine(2, 6, 14, 6);
            g2.drawLine(7, 6, 7, 14);
            g2.setColor(JStudioTheme.getAccentSecondary());
            g2.fillRect(3, 3, 4, 2);
        }
    }

    private static class ConstPoolIcon extends BaseIcon {
        ConstPoolIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getInfo());
            g2.setStroke(getStroke());
            g2.drawOval(3, 2, 10, 4);
            g2.drawLine(3, 4, 3, 12);
            g2.drawLine(13, 4, 13, 12);
            g2.drawArc(3, 10, 10, 4, 180, 180);
            g2.setColor(JStudioTheme.getAccent());
            g2.drawLine(6, 6, 6, 10);
            g2.drawLine(10, 6, 10, 10);
        }
    }

    private static class ConsoleIcon extends BaseIcon {
        ConsoleIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawRoundRect(2, 3, 12, 10, 2, 2);
            g2.setColor(JStudioTheme.getSuccess());
            g2.drawLine(4, 7, 6, 9);
            g2.drawLine(6, 9, 4, 11);
            g2.setColor(JStudioTheme.getTextSecondary());
            g2.drawLine(8, 11, 12, 11);
        }
    }

    private static class DebugIcon extends BaseIcon {
        DebugIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getError());
            g2.fillOval(5, 5, 6, 6);
            g2.setColor(JStudioTheme.getTextPrimary());
            g2.setStroke(getStroke());
            g2.drawOval(3, 3, 10, 10);
            g2.drawLine(8, 1, 8, 3);
            g2.drawLine(8, 13, 8, 15);
            g2.drawLine(1, 8, 3, 8);
            g2.drawLine(13, 8, 15, 8);
            g2.drawLine(3, 3, 5, 5);
            g2.drawLine(11, 11, 13, 13);
        }
    }

    private static class HeapIcon extends BaseIcon {
        HeapIcon(int size) {
            super(size);
        }

        @Override
        protected void paintIconContent(Graphics2D g2) {
            g2.setColor(JStudioTheme.getAccentSecondary());
            g2.setStroke(getStroke());
            g2.drawRect(2, 2, 5, 4);
            g2.drawRect(9, 2, 5, 4);
            g2.drawRect(2, 8, 5, 4);
            g2.drawRect(9, 8, 5, 4);
            g2.setColor(JStudioTheme.getAccent());
            g2.drawLine(4, 6, 4, 8);
            g2.drawLine(12, 6, 12, 8);
            g2.drawLine(7, 4, 9, 4);
            g2.drawLine(7, 10, 9, 10);
        }
    }
}
