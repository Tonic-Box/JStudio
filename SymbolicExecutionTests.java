public class SymbolicExecutionTests {

    /**
     * Main entry point for symbolic execution testing.
     * Contains loops, branches, and calls to helper methods.
     */
    public static int analyze(int x, int y, int mode) {
        // Input validation
        if (x < 0 || x > 100) {
            return -1;
        }
        if (y < 0 || y > 100) {
            return -2;
        }
        if (mode < 0 || mode > 3) {
            return -3;
        }

        int result = 0;

        // Phase 1: Mode-based dispatch with method calls
        switch (mode) {
            case 0:
                result = computeSum(x, y);
                break;
            case 1:
                result = computeProduct(x, y);
                break;
            case 2:
                result = computeCombined(x, y);
                break;
            case 3:
                result = computeRecursive(x, y);
                break;
        }

        // Phase 2: Loop with accumulation and method calls
        int accumulated = 0;
        for (int i = 0; i < x; i++) {
            if (i % 2 == 0) {
                accumulated = safeAdd(accumulated, transform(i, mode));
            } else {
                accumulated = safeAdd(accumulated, i);
            }

            // Early exit condition
            if (accumulated > 10000) {
                return -4;
            }
        }

        // Phase 3: Nested loops with method calls
        int matrix = 0;
        for (int i = 0; i < min(x, 10); i++) {
            for (int j = 0; j < min(y, 10); j++) {
                int cell = computeCell(i, j, mode);
                matrix = safeAdd(matrix, cell);

                if (cell < 0) {
                    // UNREACHABLE - computeCell always returns >= 0
                    return -5;
                }
            }
        }

        // Phase 4: Conditional chains with constraints
        int final_result = safeAdd(result, safeAdd(accumulated, matrix));

        if (x == 10 && y == 10 && mode == 0) {
            // Deterministic path - can compute exact values
            // result = computeSum(10, 10) = 20
            // accumulated = sum of transform(i, 0) for even i in [0,9] + odd i
            //             = transform(0,0) + 1 + transform(2,0) + 3 + transform(4,0) + 5 + transform(6,0) + 7 + transform(8,0) + 9
            //             = (0+1) + 1 + (4+1) + 3 + (8+1) + 5 + (12+1) + 7 + (16+1) + 9
            //             = 1 + 1 + 5 + 3 + 9 + 5 + 13 + 7 + 17 + 9 = 70
            // matrix = sum of computeCell(i,j,0) for i,j in [0,9]
            //        = sum of (i + j) = 10 * (0+1+...+9) * 2... actually:
            //        = sum over i of (sum over j of (i+j))
            //        = sum over i of (10*i + 45) = 10*45 + 10*45 = 900

            int expected = safeAdd(20, safeAdd(70, 900)); // 990

            if (final_result != expected) {
                // Should be UNREACHABLE if symbolic execution is precise
                return -6;
            }

            return 1;
        }

        if (x == y) {
            // Symmetric case
            int symmetric = computeSymmetric(x);

            if (mode == 1 && x == 5) {
                // result = computeProduct(5, 5) = 25
                // symmetric = computeSymmetric(5) = 5 * 5 + 5 = 30
                if (symmetric != 30) {
                    // UNREACHABLE
                    return -7;
                }
                return 2;
            }

            result = safeAdd(result, symmetric);
        }

        // Phase 5: Boundary analysis
        if (x == 0) {
            // accumulated must be 0 (loop doesn't execute)
            if (accumulated != 0) {
                // UNREACHABLE
                return -8;
            }
            return 3;
        }

        if (y == 0) {
            // matrix must be 0 (inner loop doesn't execute)
            if (matrix != 0) {
                // UNREACHABLE
                return -9;
            }
            return 4;
        }

        // Phase 6: Complex condition with multiple method calls
        int check1 = isPositive(result);
        int check2 = isPositive(accumulated);
        int check3 = isPositive(matrix);

        if (check1 == 1 && check2 == 1 && check3 == 1) {
            // All components are positive
            int combined = combineChecks(check1, check2, check3);

            if (combined != 3) {
                // UNREACHABLE - combineChecks(1,1,1) = 3
                return -10;
            }

            // Verify relationship between inputs and results
            if (x > 0 && y > 0) {
                int ratio = safeDivide(final_result, safeAdd(x, y));

                if (ratio < 0) {
                    // UNREACHABLE - all values positive, division result >= 0
                    return -11;
                }

                return classify(ratio);
            }
        }

        // Default return
        return 0;
    }

    // ==================== ARITHMETIC HELPERS ====================

    private static int safeAdd(int a, int b) {
        long result = (long) a + (long) b;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    private static int safeDivide(int a, int b) {
        if (b == 0) {
            return 0;
        }
        return a / b;
    }

    private static int min(int a, int b) {
        if (a < b) {
            return a;
        }
        return b;
    }

    private static int abs(int x) {
        if (x < 0) {
            return -x;
        }
        return x;
    }

    // ==================== COMPUTE METHODS ====================

    private static int computeSum(int a, int b) {
        return safeAdd(a, b);
    }

    private static int computeProduct(int a, int b) {
        int result = 0;
        int absB = abs(b);
        for (int i = 0; i < absB; i++) {
            result = safeAdd(result, a);
        }
        if (b < 0) {
            result = -result;
        }
        return result;
    }

    private static int computeCombined(int a, int b) {
        int sum = computeSum(a, b);
        int diff = safeAdd(a, -b);
        return safeAdd(sum, abs(diff));
    }

    private static int computeRecursive(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        if (a == 1) {
            return b;
        }
        if (b == 1) {
            return a;
        }
        // GCD-like computation
        if (a > b) {
            return computeRecursive(safeAdd(a, -b), b);
        } else if (b > a) {
            return computeRecursive(a, safeAdd(b, -a));
        } else {
            return a; // a == b
        }
    }

    private static int computeSymmetric(int n) {
        return safeAdd(computeProduct(n, n), n); // n^2 + n
    }

    // ==================== TRANSFORM METHODS ====================

    private static int transform(int value, int mode) {
        switch (mode) {
            case 0:
                return safeAdd(value * 2, 1);  // 2v + 1
            case 1:
                return safeAdd(value * 3, -1); // 3v - 1
            case 2:
                return computeProduct(value, value); // v^2
            case 3:
                return triangular(value); // v*(v+1)/2
            default:
                return value;
        }
    }

    private static int triangular(int n) {
        if (n <= 0) {
            return 0;
        }
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum = safeAdd(sum, i);
        }
        return sum;
    }

    // ==================== CELL/MATRIX HELPERS ====================

    private static int computeCell(int row, int col, int mode) {
        int base = safeAdd(row, col);

        switch (mode) {
            case 0:
                return base;
            case 1:
                return computeProduct(row + 1, col + 1);
            case 2:
                return safeAdd(base, computeProduct(row, col));
            case 3:
                if (row == col) {
                    return computeProduct(base, 2);
                }
                return base;
            default:
                return 0;
        }
    }

    // ==================== CHECK/CLASSIFY METHODS ====================

    private static int isPositive(int x) {
        if (x > 0) {
            return 1;
        }
        return 0;
    }

    private static int combineChecks(int a, int b, int c) {
        return safeAdd(a, safeAdd(b, c));
    }

    private static int classify(int value) {
        if (value < 0) {
            return -100;
        }
        if (value == 0) {
            return 100;
        }
        if (value < 10) {
            return 101;
        }
        if (value < 50) {
            return 102;
        }
        if (value < 100) {
            return 103;
        }
        return 104;
    }
}