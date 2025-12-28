package com.tonic.util;

import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.Modifiers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class NativeMethodScanner {

    private static final Set<String> HIGH_PRIORITY_PACKAGES = Set.of(
        "java/lang/",
        "java/util/",
        "java/io/"
    );

    private static final Set<String> LOW_PRIORITY_PACKAGES = Set.of(
        "sun/",
        "jdk/internal/",
        "java/security/",
        "javax/crypto/"
    );

    public static void main(String[] args) {
        System.out.println("Loading JDK classes...");
        ClassPool pool = ClassPool.getDefault();
        System.out.println("Loaded " + pool.getClasses().size() + " classes");

        NativeRegistry registry = new NativeRegistry();
        registry.registerDefaults();

        List<String> highPriority = new ArrayList<>();
        List<String> medPriority = new ArrayList<>();
        List<String> lowPriority = new ArrayList<>();

        int totalNative = 0;
        int handled = 0;

        for (ClassFile cf : pool.getClasses()) {
            for (MethodEntry method : cf.getMethods()) {
                if (!Modifiers.isNative(method.getAccess())) {
                    continue;
                }
                totalNative++;

                String owner = method.getOwnerName();
                String name = method.getName();
                String desc = method.getDesc();

                if (registry.hasHandler(owner, name, desc)) {
                    handled++;
                    continue;
                }

                String signature = owner + "." + name + desc;

                if (isHighPriority(owner)) {
                    highPriority.add(signature);
                } else if (isLowPriority(owner)) {
                    lowPriority.add(signature);
                } else {
                    medPriority.add(signature);
                }
            }
        }

        highPriority.sort(Comparator.naturalOrder());
        medPriority.sort(Comparator.naturalOrder());
        lowPriority.sort(Comparator.naturalOrder());

        System.out.println("\n========================================");
        System.out.println("NATIVE METHOD SCAN RESULTS");
        System.out.println("========================================");
        System.out.println("Total native methods: " + totalNative);
        System.out.println("Handlers registered: " + handled);
        System.out.println("Missing handlers: " + (totalNative - handled));
        System.out.println();

        System.out.println("=== HIGH PRIORITY (java.lang/util/io) ===");
        for (String sig : highPriority) {
            System.out.println("  " + sig);
        }
        System.out.println("Count: " + highPriority.size());
        System.out.println();

        System.out.println("=== MEDIUM PRIORITY ===");
        for (String sig : medPriority) {
            System.out.println("  " + sig);
        }
        System.out.println("Count: " + medPriority.size());
        System.out.println();

        System.out.println("=== LOW PRIORITY (sun/jdk.internal) ===");
        for (String sig : lowPriority) {
            System.out.println("  " + sig);
        }
        System.out.println("Count: " + lowPriority.size());
    }

    private static boolean isHighPriority(String owner) {
        for (String pkg : HIGH_PRIORITY_PACKAGES) {
            if (owner.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLowPriority(String owner) {
        for (String pkg : LOW_PRIORITY_PACKAGES) {
            if (owner.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }
}
