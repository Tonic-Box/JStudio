package com.tonic.service;

import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.Renamer;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.util.AccessFlags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Renames a project's classes, methods and fields to generic names, giving each override family one name and leaving members a library or the JVM finds by name untouched. */
public final class NameDeobfuscator
{

    private static final Set<String> REFLECTIVE_METHODS = Set.of("<init>", "<clinit>", "main", "readObject", "writeObject", "readObjectNoData", "readResolve", "writeReplace");
    private static final Set<String> REFLECTIVE_FIELDS = Set.of("serialVersionUID");
    private static final List<String> LIBRARY_PACKAGES = List.of("java/", "javax/", "sun/", "com/sun/", "jdk/", "org/w3c/", "org/xml/", "org/ietf/");

    private final ProjectModel project;
    private final boolean renameClasses;
    private final boolean renameMethods;
    private final boolean renameFields;
    private final boolean skipLibraryPackages;
    private final Consumer<String> log;

    /**
     * Configures a rename of a project.
     *
     * @param project the project to rename
     * @param renameClasses whether to rename classes
     * @param renameMethods whether to rename methods
     * @param renameFields whether to rename fields
     * @param skipLibraryPackages whether classes in JDK and standard library packages are left alone
     * @param log told of each planned rename
     */
    public NameDeobfuscator(ProjectModel project, boolean renameClasses, boolean renameMethods, boolean renameFields, boolean skipLibraryPackages, Consumer<String> log)
    {
        this.project = project;
        this.renameClasses = renameClasses;
        this.renameMethods = renameMethods;
        this.renameFields = renameFields;
        this.skipLibraryPackages = skipLibraryPackages;
        this.log = log;
    }

    /** The outcome of a rename: what was renamed, and the old names of renamed classes. */
    public static final class Result
    {
        private final int classes;
        private final int methods;
        private final int fields;
        private final Set<String> oldClassNames;

        Result(int classes, int methods, int fields, Set<String> oldClassNames)
        {
            this.classes = classes;
            this.methods = methods;
            this.fields = fields;
            this.oldClassNames = oldClassNames;
        }

        /** @return the number of classes renamed */
        public int getClasses()
        {
            return classes;
        }

        /** @return the number of method families renamed */
        public int getMethods()
        {
            return methods;
        }

        /** @return the number of fields renamed */
        public int getFields()
        {
            return fields;
        }

        /** @return the internal names the renamed classes had before */
        public Set<String> getOldClassNames()
        {
            return oldClassNames;
        }
    }

    /**
     * Plans every rename, applies it to the project's bytecode and rekeys renamed classes; the caller refreshes the UI.
     *
     * @return what was renamed
     * @throws com.tonic.renamer.exception.RenameException if the renamer rejects or fails to apply the mappings
     */
    public Result apply()
    {
        ClassPool pool = project.getClassPool();
        Renamer renamer = new Renamer(pool);
        ClassHierarchy hierarchy = renamer.getHierarchy();
        LibraryOverrides libraryOverrides = new LibraryOverrides(project);
        Set<String> userClasses = project.getUserClassNames();

        List<String> classes = new ArrayList<>(userClasses);
        classes.sort(String::compareTo);
        classes.removeIf(name -> pool.get(name) == null || (skipLibraryPackages && isLibraryPackage(name)));

        Set<String> takenClassNames = new HashSet<>();
        Set<String> takenMethodNames = new HashSet<>();
        Set<String> takenFieldNames = new HashSet<>();
        for (ClassFile cf : pool.getClasses())
        {
            takenClassNames.add(cf.getClassName());
            cf.getMethods().forEach(method -> takenMethodNames.add(method.getName()));
            cf.getFields().forEach(field -> takenFieldNames.add(field.getName()));
        }

        Map<String, String> classMappings = new HashMap<>();
        int methodFamilies = 0;
        int fields = 0;
        Set<String> handledMethods = new HashSet<>();

        for (String className : classes)
        {
            ClassFile cf = pool.get(className);

            if (renameClasses)
            {
                String newName = nextName(packageOf(className) + "Class", takenClassNames);
                renamer.mapClass(className, newName);
                classMappings.put(className, newName);
                log.accept("Class: " + className + " -> " + newName);
            }

            if (renameMethods)
            {
                for (MethodEntry method : cf.getMethods())
                {
                    String name = method.getName();
                    String descriptor = method.getDesc();
                    if (!handledMethods.add(className + '.' + name + descriptor) || isReflectiveMethod(cf, name, descriptor))
                    {
                        continue;
                    }
                    Set<String> family = family(hierarchy, className, name, descriptor);
                    family.forEach(member -> handledMethods.add(member + '.' + name + descriptor));
                    if (!renameable(family, name, descriptor, userClasses, libraryOverrides))
                    {
                        continue;
                    }
                    String newName = nextName("method", takenMethodNames);
                    for (String member : family)
                    {
                        renamer.mapMethod(member, name, descriptor, newName);
                    }
                    log.accept("  Method: " + name + descriptor + " -> " + newName + (family.size() > 1 ? " (" + family.size() + " classes)" : ""));
                    methodFamilies++;
                }
            }

            if (renameFields)
            {
                for (FieldEntry field : cf.getFields())
                {
                    if (REFLECTIVE_FIELDS.contains(field.getName()))
                    {
                        continue;
                    }
                    String newName = nextName("field", takenFieldNames);
                    renamer.mapField(className, field.getName(), field.getDesc(), newName);
                    log.accept("  Field: " + field.getName() + " -> " + newName);
                    fields++;
                }
            }
        }

        log.accept("Applying " + renamer.getMappings().size() + " mappings...");
        renamer.apply();
        if (!classMappings.isEmpty())
        {
            project.applyClassNameMappings(classMappings);
        }
        return new Result(classMappings.size(), methodFamilies, fields, classMappings.keySet());
    }

    private static Set<String> family(ClassHierarchy hierarchy, String owner, String name, String descriptor)
    {
        Set<String> family = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        family.add(owner);
        pending.add(owner);
        while (!pending.isEmpty())
        {
            for (ClassNode node : hierarchy.findMethodHierarchy(pending.poll(), name, descriptor))
            {
                if (family.add(node.getName()))
                {
                    pending.add(node.getName());
                }
            }
        }
        return family;
    }

    private boolean renameable(Set<String> family, String name, String descriptor, Set<String> userClasses, LibraryOverrides libraryOverrides)
    {
        for (String member : family)
        {
            if (!userClasses.contains(member) || (skipLibraryPackages && isLibraryPackage(member)) || libraryOverrides.overridesLibrary(member, name, descriptor))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isReflectiveMethod(ClassFile cf, String name, String descriptor)
    {
        if (REFLECTIVE_METHODS.contains(name))
        {
            return true;
        }
        return AccessFlags.isEnum(cf.getAccess()) && (name.equals("values") || (name.equals("valueOf") && descriptor.startsWith("(Ljava/lang/String;)")));
    }

    private static String nextName(String prefix, Set<String> taken)
    {
        int number = 1;
        while (taken.contains(prefix + number))
        {
            number++;
        }
        String name = prefix + number;
        taken.add(name);
        return name;
    }

    private static String packageOf(String className)
    {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(0, lastSlash + 1) : "";
    }

    private static boolean isLibraryPackage(String className)
    {
        for (String prefix : LIBRARY_PACKAGES)
        {
            if (className.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }
}
