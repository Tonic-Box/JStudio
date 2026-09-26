package com.tonic.ui.live.eval;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.util.DescriptorParser;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shallow scratch-pad completion: keywords and project class names, plus public members after a receiver that resolves against the project's classes. */
public final class ScratchCompletionProvider extends DefaultCompletionProvider
{

    private static final String[] KEYWORDS =
            {
                    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
                    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
                    "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
                    "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
                    "volatile", "while", "true", "false", "null", "var"
            };

    private static final Pattern RECEIVER = Pattern.compile("([\\w$.]+)\\.(\\w*)$");

    private final ProjectModel project;
    private final Map<String, String> simpleToInternal = new HashMap<>();

    /**
     * Creates the provider with keyword and project class name completions.
     *
     * @param project the project whose classes are offered and used to resolve receivers
     */
    public ScratchCompletionProvider(ProjectModel project)
    {
        this.project = project;
        for (String kw : KEYWORDS)
        {
            addCompletion(new BasicCompletion(this, kw));
        }
        for (ClassEntryModel entry : project.getAllClasses())
        {
            String internal = entry.getClassName();
            if (internal == null)
            {
                continue;
            }
            String simple = simpleName(internal);
            simpleToInternal.putIfAbsent(simple, internal);
        }
        for (ClassEntryModel entry : project.getUserClasses())
        {
            String internal = entry.getClassName();
            if (internal != null)
            {
                addCompletion(new BasicCompletion(this, simpleName(internal), internal.replace('/', '.')));
            }
        }
        for (Map.Entry<String, List<String>> e : JdkClassIndex.simpleToFqn().entrySet())
        {
            addCompletion(new BasicCompletion(this, e.getKey(), e.getValue().get(0)));
        }
    }

    @Override
    protected List<Completion> getCompletionsImpl(JTextComponent comp)
    {
        String before = textBeforeCaret(comp);
        Matcher m = RECEIVER.matcher(before);
        if (m.find())
        {
            List<Completion> members = memberCompletions(m.group(1), before, m.group(2));
            if (members != null)
            {
                return members;
            }
        }
        return super.getCompletionsImpl(comp);
    }

    private List<Completion> memberCompletions(String receiver, String before, String partial)
    {
        ClassEntryModel projectType = resolveType(receiver);
        boolean staticsOnly = projectType != null;
        if (projectType == null)
        {
            projectType = resolveType(localVarType(before, receiver));
        }
        if (projectType != null)
        {
            return projectMembers(projectType, staticsOnly, partial);
        }
        return jdkMembers(receiver, before, partial);
    }

    private List<Completion> projectMembers(ClassEntryModel type, boolean staticsOnly, String partial)
    {
        List<Completion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String lower = partial.toLowerCase();
        ClassEntryModel current = type;
        Set<String> visited = new HashSet<>();
        while (current != null && visited.add(current.getClassName()))
        {
            for (MethodEntryModel method : current.getMethods())
            {
                if (!method.isPublic() || (staticsOnly && !method.isStatic()))
                {
                    continue;
                }
                String name = method.getName();
                if (name.startsWith("<") || !name.toLowerCase().startsWith(lower) || !seen.add(name + method.getDescriptor()))
                {
                    continue;
                }
                String sig = name + "(" + DescriptorParser.formatMethodParams(method.getDescriptor()) + ") : "
                        + DescriptorParser.formatReturnType(method.getDescriptor());
                out.add(new BasicCompletion(this, name, sig));
            }
            for (FieldEntryModel field : current.getFields())
            {
                if (!field.isPublic() || (staticsOnly && !field.isStatic()))
                {
                    continue;
                }
                String name = field.getName();
                if (!name.toLowerCase().startsWith(lower) || !seen.add(name))
                {
                    continue;
                }
                out.add(new BasicCompletion(this, name, name + " : " + field.getDisplayType()));
            }
            current = project.getClass(current.getSuperClassName());
        }
        return out;
    }

    private List<Completion> jdkMembers(String receiver, String before, String partial)
    {
        String[] segments = receiver.split("\\.");
        Class<?> type;
        boolean staticContext;
        int memberStart;

        Class<?> headType = loadClass(segments[0]);
        if (headType == null)
        {
            headType = loadClass(localVarType(before, segments[0]));
            if (headType == null)
            {
                return resolveFqnPrefix(segments, partial);
            }
            type = headType;
            staticContext = false;
        }
        else
        {
            type = headType;
            staticContext = true;
        }
        memberStart = 1;

        for (int i = memberStart; i < segments.length; i++)
        {
            Class<?> next = fieldType(type, segments[i]);
            if (next == null)
            {
                return null;
            }
            type = next;
            staticContext = false;
        }
        return reflectMembers(type, staticContext, partial);
    }

    private List<Completion> resolveFqnPrefix(String[] segments, String partial)
    {
        StringBuilder fqn = new StringBuilder();
        for (int i = 0; i < segments.length; i++)
        {
            fqn.append(i == 0 ? "" : ".").append(segments[i]);
            Class<?> type = loadClass(fqn.toString());
            if (type != null)
            {
                boolean staticContext = true;
                for (int j = i + 1; j < segments.length; j++)
                {
                    type = fieldType(type, segments[j]);
                    if (type == null)
                    {
                        return null;
                    }
                    staticContext = false;
                }
                return reflectMembers(type, staticContext, partial);
            }
        }
        return null;
    }

    private List<Completion> reflectMembers(Class<?> type, boolean staticContext, String partial)
    {
        List<Completion> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String lower = partial.toLowerCase();
        for (Method method : type.getMethods())
        {
            if (method.isSynthetic() || method.isBridge() || Modifier.isStatic(method.getModifiers()) != staticContext)
            {
                continue;
            }
            String name = method.getName();
            if (!name.toLowerCase().startsWith(lower))
            {
                continue;
            }
            String params = paramList(method.getParameterTypes());
            if (!seen.add(name + "(" + params + ")"))
            {
                continue;
            }
            out.add(new BasicCompletion(this, name, name + "(" + params + ") : " + method.getReturnType().getSimpleName()));
        }
        for (Field field : type.getFields())
        {
            if (Modifier.isStatic(field.getModifiers()) != staticContext)
            {
                continue;
            }
            String name = field.getName();
            if (!name.toLowerCase().startsWith(lower) || !seen.add(name))
            {
                continue;
            }
            out.add(new BasicCompletion(this, name, name + " : " + field.getType().getSimpleName()));
        }
        return out;
    }

    private static Class<?> fieldType(Class<?> owner, String name)
    {
        try
        {
            return owner.getField(name).getType();
        }
        catch (NoSuchFieldException ignored)
        {
        }
        try
        {
            Method m = owner.getMethod(name);
            return m.getReturnType();
        }
        catch (NoSuchMethodException ignored)
        {
        }
        return null;
    }

    private Class<?> loadClass(String name)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }
        String fqn = name;
        if (name.indexOf('.') < 0)
        {
            List<String> fqns = JdkClassIndex.simpleToFqn().get(name);
            if (fqns == null || fqns.size() != 1)
            {
                return null;
            }
            fqn = fqns.get(0);
        }
        try
        {
            return Class.forName(fqn, false, getClass().getClassLoader());
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static String paramList(Class<?>[] params)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i++)
        {
            sb.append(i == 0 ? "" : ", ").append(params[i].getSimpleName());
        }
        return sb.toString();
    }

    private ClassEntryModel resolveType(String name)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }
        if (name.indexOf('.') >= 0)
        {
            return project.getClass(name.replace('.', '/'));
        }
        String internal = simpleToInternal.get(name);
        return internal != null ? project.getClass(internal) : null;
    }

    private static String localVarType(String before, String varName)
    {
        Matcher decl = Pattern.compile("(?:^|[\\s({;])([A-Za-z_][\\w$.]*)\\s+" + Pattern.quote(varName) + "\\b")
                .matcher(before);
        String type = null;
        while (decl.find())
        {
            type = decl.group(1);
        }
        return type;
    }

    private static String simpleName(String internalName)
    {
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(slash + 1) : internalName;
    }

    private static String textBeforeCaret(JTextComponent comp)
    {
        try
        {
            return comp.getDocument().getText(0, comp.getCaretPosition());
        }
        catch (BadLocationException e)
        {
            return "";
        }
    }
}
