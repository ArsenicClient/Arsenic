package arsenic.addon;

import arsenic.runtime.AccessorRewriter;
import arsenic.runtime.RuntimeNames;
import arsenic.runtime.InjectedLaunch;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Compiles addon source with the Eclipse compiler (so a plain JRE is enough) against the live classpath.
 * With remapping on, Minecraft is presented to the compiler under MCP names and the output is mapped back to
 * the runtime (SRG) names, which is what lets addons be written against the names everybody knows.
 */
public final class AddonCompiler {

    public static final class Result {
        public final Map<String, byte[]> classes = new LinkedHashMap<>(); // binary name (a.b.C) -> bytes
        public final List<String> errors = new ArrayList<>();
    }

    private static final byte[] MISSING = new byte[0];

    private final ClassSource source;
    private final MappingTable table; // null when the runtime already uses MCP names

    public AddonCompiler(ClassSource source, MappingTable table) {
        this.source = source;
        this.table = table;
    }

    /** @param sources file name -> file contents */
    public Result compile(Map<String, String> sources) {
        Result result = new Result();
        if (sources.isEmpty())
            return result;

        ICompilationUnit[] units = sources.entrySet().stream()
                .map(e -> new SourceUnit(e.getKey(), e.getValue())).toArray(ICompilationUnit[]::new);

        Map<String, byte[]> raw = new LinkedHashMap<>();
        ICompilerRequestor requestor = r -> {
            if (r.hasErrors()) {
                for (CategorizedProblem p : r.getErrors())
                    result.errors.add(new String(r.getCompilationUnit().getFileName()) + ":" + p.getSourceLineNumber() + ": " + p.getMessage());
                return;
            }
            for (ClassFile cf : r.getClassFiles())
                raw.put(String.valueOf(cf.fileName()), cf.getBytes());
        };

        Map<String, String> options = new HashMap<>();
        options.put(CompilerOptions.OPTION_Compliance, CompilerOptions.VERSION_1_8);
        options.put(CompilerOptions.OPTION_Source, CompilerOptions.VERSION_1_8);
        options.put(CompilerOptions.OPTION_TargetPlatform, CompilerOptions.VERSION_1_8);
        options.put(CompilerOptions.OPTION_Encoding, "UTF-8");
        options.put(CompilerOptions.OPTION_LineNumberAttribute, CompilerOptions.GENERATE);
        options.put(CompilerOptions.OPTION_LocalVariableAttribute, CompilerOptions.GENERATE);

        Set<String> sourceTypes = new HashSet<>();
        for (ICompilationUnit unit : units) {
            SourceUnit s = (SourceUnit) unit;
            sourceTypes.add(s.internalName());
        }

        new Compiler(new Environment(sourceTypes), DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                new CompilerOptions(options), requestor, new DefaultProblemFactory(Locale.ENGLISH)).compile(units);

        Remappers.Hierarchy hierarchy = new Remappers.Hierarchy(source);
        if (table != null)
            raw.values().forEach(hierarchy::register);
        Map<String, byte[]> srg = new LinkedHashMap<>();
        raw.forEach((internal, bytes) -> {
            byte[] remapped = table != null ? Remappers.toSrg(bytes, table, hierarchy) : bytes;
            // injected, Minecraft classes do not implement the accessor interfaces
            if (InjectedLaunch.isInjected())
                remapped = AccessorRewriter.rewrite(remapped);
            srg.put(internal, remapped);
        });
        // injected into vanilla: SRG to the game's names, which follows member lookups up through the addon's own classes
        RuntimeNames names = RuntimeNames.current();
        if (table != null && names.namespace() == RuntimeNames.Namespace.NOTCH)
            srg.values().forEach(names::registerSupers);
        srg.forEach((internal, bytes) -> result.classes.put(internal.replace('/', '.'),
                table != null ? names.toRuntime(bytes) : bytes));
        return result;
    }

    private static final class SourceUnit implements ICompilationUnit {
        private final String fileName;
        private final char[] contents;
        private final char[] mainTypeName;
        private final char[][] packageName;

        SourceUnit(String fileName, String contents) {
            this.fileName = fileName;
            this.contents = contents.toCharArray();
            String base = fileName.substring(Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
            this.mainTypeName = base.replaceAll("\\.java$", "").toCharArray();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(contents);
            if (m.find()) {
                String[] parts = m.group(1).split("\\.");
                packageName = new char[parts.length][];
                for (int i = 0; i < parts.length; i++)
                    packageName[i] = parts[i].toCharArray();
            } else {
                packageName = new char[0][];
            }
        }

        String internalName() {
            StringBuilder sb = new StringBuilder();
            for (char[] part : packageName)
                sb.append(part).append('/');
            return sb.append(mainTypeName).toString();
        }

        @Override public char[] getContents() { return contents; }
        @Override public char[] getMainTypeName() { return mainTypeName; }
        @Override public char[][] getPackageName() { return packageName; }
        @Override public char[] getFileName() { return fileName.toCharArray(); }
        @Override public boolean ignoreOptionalProblems() { return false; }
    }

    private final class Environment implements INameEnvironment {
        private final Set<String> sourceTypes;

        Environment(Set<String> sourceTypes) {
            this.sourceTypes = sourceTypes;
        }

        private final Map<String, byte[]> cache = new HashMap<>();

        @Override
        public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
            return find(join(compoundTypeName, '/'));
        }

        @Override
        public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
            String pkg = join(packageName, '/');
            return find(pkg.isEmpty() ? String.valueOf(typeName) : pkg + '/' + String.valueOf(typeName));
        }

        @Override
        public boolean isPackage(char[][] parentPackageName, char[] packageName) {
            String parent = parentPackageName == null ? "" : join(parentPackageName, '/');
            String name = parent.isEmpty() ? String.valueOf(packageName) : parent + '/' + String.valueOf(packageName);
            return !sourceTypes.contains(name) && bytes(name) == null;
        }

        @Override
        public void cleanup() {}

        private NameEnvironmentAnswer find(String internal) {
            byte[] bytes = bytes(internal);
            if (bytes == null)
                return null;
            try {
                return new NameEnvironmentAnswer(new ClassFileReader(bytes, internal.toCharArray(), true), null);
            } catch (Exception e) {
                return null;
            }
        }

        private byte[] bytes(String internal) {
            byte[] cached = cache.get(internal);
            if (cached == null) {
                byte[] loaded = source.getBytes(internal);
                if (loaded != null && table != null)
                    loaded = Remappers.toMcpDeclarations(loaded, table);
                cached = loaded == null ? MISSING : loaded;
                cache.put(internal, cached);
            }
            return cached == MISSING ? null : cached;
        }

        private String join(char[][] parts, char sep) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0)
                    sb.append(sep);
                sb.append(parts[i]);
            }
            return sb.toString();
        }
    }
}
