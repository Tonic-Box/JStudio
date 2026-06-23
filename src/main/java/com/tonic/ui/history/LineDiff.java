package com.tonic.ui.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal line diff producing aligned rows for a side-by-side view. Common prefix/suffix are trimmed first (cheap),
 * then an LCS aligns the differing middle; adjacent deletes/inserts are paired into CHANGE rows so a modified line
 * shows old vs new on the same row. A size cap on the LCS keeps huge files from blowing up (the middle then degrades
 * to plain delete+insert blocks).
 */
public final class LineDiff {

    /** EQUAL = unchanged; DELETE = left only; INSERT = right only; CHANGE = paired modification (both sides). */
    public enum Type {
        EQUAL, DELETE, INSERT, CHANGE
    }

    /** One aligned row. {@code left}/{@code right} are null on the filler side of a DELETE/INSERT. */
    public static final class Row {
        public final Type type;
        public final String left;
        public final String right;

        Row(Type type, String left, String right) {
            this.type = type;
            this.left = left;
            this.right = right;
        }
    }

    private static final long LCS_CELL_CAP = 4_000_000L;

    private LineDiff() {
    }

    public static List<Row> diff(List<String> left, List<String> right) {
        int n = left.size();
        int m = right.size();
        int start = 0;
        while (start < n && start < m && left.get(start).equals(right.get(start))) {
            start++;
        }
        int endL = n;
        int endR = m;
        while (endL > start && endR > start && left.get(endL - 1).equals(right.get(endR - 1))) {
            endL--;
            endR--;
        }

        List<Row> rows = new ArrayList<>();
        for (int k = 0; k < start; k++) {
            rows.add(new Row(Type.EQUAL, left.get(k), right.get(k)));
        }
        rows.addAll(diffMiddle(left.subList(start, endL), right.subList(start, endR)));
        // The common suffix is left[endL..n) paired with right[endR..m) - same length, but different start indices.
        int suffix = n - endL;
        for (int k = 0; k < suffix; k++) {
            rows.add(new Row(Type.EQUAL, left.get(endL + k), right.get(endR + k)));
        }
        return rows;
    }

    private static List<Row> diffMiddle(List<String> left, List<String> right) {
        int n = left.size();
        int m = right.size();
        List<Row> rows = new ArrayList<>();
        if (n == 0 && m == 0) {
            return rows;
        }
        if ((long) n * m > LCS_CELL_CAP) {
            flush(rows, left, right);
            return rows;
        }

        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = left.get(i).equals(right.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<String> dels = new ArrayList<>();
        List<String> inss = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (left.get(i).equals(right.get(j))) {
                flush(rows, dels, inss);
                rows.add(new Row(Type.EQUAL, left.get(i), right.get(j)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                dels.add(left.get(i++));
            } else {
                inss.add(right.get(j++));
            }
        }
        while (i < n) {
            dels.add(left.get(i++));
        }
        while (j < m) {
            inss.add(right.get(j++));
        }
        flush(rows, dels, inss);
        return rows;
    }

    /** Emits pending deletes/inserts, pairing overlap as CHANGE rows and the remainder as DELETE/INSERT rows. */
    private static void flush(List<Row> rows, List<String> dels, List<String> inss) {
        int paired = Math.min(dels.size(), inss.size());
        for (int k = 0; k < paired; k++) {
            rows.add(new Row(Type.CHANGE, dels.get(k), inss.get(k)));
        }
        for (int k = paired; k < dels.size(); k++) {
            rows.add(new Row(Type.DELETE, dels.get(k), null));
        }
        for (int k = paired; k < inss.size(); k++) {
            rows.add(new Row(Type.INSERT, null, inss.get(k)));
        }
        dels.clear();
        inss.clear();
    }
}
