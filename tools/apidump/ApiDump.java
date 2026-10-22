import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes API.md: the public/protected surface of the parts of Arsenic that addons can call, read straight from the
 * sources so it cannot drift. Run by the generateAddonApi gradle task:
 *
 *   ApiDump <source root> <output file> <entry>...
 *
 * An entry is a path under the source root: a .java file, or a directory (every file below it). Order is kept.
 * This is a small hand-rolled declaration scanner, not a Java parser: it understands comments, strings, nested
 * types, enum constants and field initialisers, which is all the signature list needs.
 */
public final class ApiDump {

    private static final class Type {
        final String header;
        final boolean iface;
        final boolean isEnum;
        final List<String> members = new ArrayList<>();
        final List<Type> nested = new ArrayList<>();

        Type(String header, boolean iface, boolean isEnum) {
            this.header = header;
            this.iface = iface;
            this.isEnum = isEnum;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        List<Path> files = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            Path p = root.resolve(args[i]);
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    files.addAll(s.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList()));
                }
            } else if (Files.exists(p)) {
                files.add(p);
            } else {
                System.err.println("ApiDump: no such entry " + args[i]);
            }
        }

        StringBuilder md = new StringBuilder();
        md.append("# Arsenic addon API (generated)\n\n");
        md.append("Generated from the sources by `gradlew generateAddonApi` (also run by every build). Do not edit.\n");
        md.append("Public and protected members only; the first line of each Javadoc is kept. For how things fit together read ADDONS.md.\n");
        int count = 0;
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            String pkg = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')).replace('/', '.') : "";
            Type type = scan(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (type == null)
                continue;
            md.append("\n## ").append(pkg).append('.').append(simpleName(type.header)).append("\n\n```java\n");
            write(md, type, "");
            md.append("```\n");
            count++;
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write(md.toString());
        }
        System.out.println("ApiDump: " + count + " types -> " + out);
    }

    private static String simpleName(String header) {
        String[] t = header.split("\\s+");
        for (int i = 0; i < t.length - 1; i++)
            if (t[i].equals("class") || t[i].equals("interface") || t[i].equals("enum") || t[i].equals("@interface"))
                return t[i + 1].replaceAll("[<{].*", "");
        return header;
    }

    private static void write(StringBuilder md, Type t, String indent) {
        md.append(indent).append(t.header).append(" {\n");
        for (String m : t.members)
            md.append(indent).append("    ").append(m).append('\n');
        for (Type n : t.nested) {
            if (!t.members.isEmpty() || n != t.nested.get(0))
                md.append('\n');
            write(md, n, indent + "    ");
        }
        md.append(indent).append("}\n");
    }

    // ---------------------------------------------------------------------------------------------------------

    private static Type scan(String src) {
        Deque<Type> stack = new ArrayDeque<>();
        List<Type> tops = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        String doc = null;      // first line of the javadoc just before the declaration being read
        String pendingDoc = null;
        int paren = 0;
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);

            // comments
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                if (end < 0) end = n - 2;
                if (src.startsWith("/**", i) && head.toString().trim().isEmpty())
                    pendingDoc = firstLine(src.substring(i + 3, end));
                i = end + 2;
                continue;
            }
            // strings / chars
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c) j += src.charAt(j) == '\\' ? 2 : 1;
                head.append(src, i, Math.min(j + 1, n));
                i = j + 1;
                continue;
            }

            if (c == '(' ) paren++;
            if (c == ')' ) paren--;

            if (paren == 0 && (c == '{' || c == ';' || c == '=' || c == '}')) {
                String h = clean(head.toString());
                if (c == '}') {
                    // closes the current type body
                    if (h.isEmpty() && !stack.isEmpty())
                        stack.pop();
                    head.setLength(0);
                    pendingDoc = null;
                    i++;
                    continue;
                }
                if (h.startsWith("package ") || h.startsWith("import ")) {
                    head.setLength(0);
                    pendingDoc = null;
                    i++;
                    continue;
                }
                doc = pendingDoc;
                pendingDoc = null;
                head.setLength(0);

                boolean isType = isTypeHeader(h);
                if (isType && c == '{') {
                    Type t = new Type(typeHeader(h), h.contains("interface "), isEnumHeader(h));
                    if (stack.isEmpty()) {
                        if (isPublic(h)) tops.add(t);
                    } else if (isVisible(h, stack.peek())) {
                        stack.peek().nested.add(t);
                    }
                    stack.push(t);
                    if (t.isEnum)
                        i = readEnumConstants(src, i + 1, t) ;
                    else
                        i++;
                    continue;
                }
                Type owner = stack.peek();
                if (owner != null && !h.isEmpty() && isVisible(h, owner) && !h.contains("createComponent"))
                    owner.members.add((doc != null && !doc.isEmpty() ? "/** " + doc + " */ " : "") + declaration(h, owner, c == '{'));
                if (c == '{') {
                    i = skipBlock(src, i);          // method body, initialiser or lambda
                } else if (c == '=') {
                    i = skipInitialiser(src, i + 1); // field initialiser up to its ';'
                } else {
                    i++;
                }
                continue;
            }
            head.append(c);
            i++;
        }
        return tops.isEmpty() ? null : tops.get(0);
    }

    private static String firstLine(String comment) {
        for (String line : comment.split("\n")) {
            String l = line.replaceAll("^\\s*\\*+", "").replaceAll("<[^>]+>", "").trim();
            if (!l.isEmpty() && !l.startsWith("@"))
                return l.replaceAll("\\{@\\w+ ([^}]*)}", "$1");
        }
        return "";
    }

    /** Whitespace collapsed, annotations removed. */
    private static String clean(String s) {
        StringBuilder out = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '@' && !s.startsWith("@interface", i)) {
                i++;
                while (i < n && (Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) == '.')) i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                if (i < n && s.charAt(i) == '(') {
                    int depth = 0;
                    for (; i < n; i++) {
                        if (s.charAt(i) == '(') depth++;
                        if (s.charAt(i) == ')' && --depth == 0) break;
                    }
                } else {
                    i--;
                }
                continue;
            }
            out.append(c);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static boolean isTypeHeader(String h) {
        return h.matches(".*\\b(class|interface|enum) [A-Za-z_].*") && !h.contains("(")
                || h.contains("@interface ");
    }

    private static boolean isEnumHeader(String h) {
        return h.matches(".*\\benum [A-Za-z_].*");
    }

    private static String typeHeader(String h) {
        return h;
    }

    private static boolean isPublic(String h) {
        return h.startsWith("public ") || h.contains(" public ") || h.startsWith("protected ");
    }

    private static boolean isVisible(String h, Type owner) {
        if (h.startsWith("private ") || h.contains(" private ") || h.startsWith("static {"))
            return false;
        if (owner.iface || owner.header.contains("@interface"))
            return true;
        return h.startsWith("public ") || h.startsWith("protected ") || h.contains(" public ") || h.contains(" protected ");
    }

    private static String declaration(String h, Type owner, boolean hasBody) {
        boolean method = h.contains("(");
        String d = h.replace(" final ", " ").replaceAll("^final ", "");
        if (owner.iface && !method)
            d = d.replace("static ", "");
        return d + ";";
    }

    /** Reads enum constants from just after an enum's '{'. Returns the index after the terminating ';' or '}'. */
    private static int readEnumConstants(String src, int i, Type t) {
        List<String> names = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int e = src.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 2;
                continue;
            }
            if (c == '"') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '"') j += src.charAt(j) == '\\' ? 2 : 1;
                i = j + 1;
                continue;
            }
            if (c == '(' || c == '{') depth++;
            if (c == ')' || (c == '}' && depth > 0)) depth--;
            else if (c == '}' && depth == 0) break;
            if (depth == 0 && (c == ',' || c == ';')) {
                String name = cur.toString().trim().replaceAll("[^A-Za-z0-9_].*", "");
                if (!name.isEmpty()) names.add(name);
                cur.setLength(0);
                if (c == ';') {
                    i++;
                    break;
                }
            } else if (depth == 0) {
                cur.append(c);
            }
            i++;
        }
        String last = cur.toString().trim().replaceAll("[^A-Za-z0-9_].*", "");
        if (!last.isEmpty()) names.add(last);
        if (!names.isEmpty())
            t.members.add(String.join(", ", names) + ";");
        return i;
    }

    /** From just after '{' of a block, returns the index after its matching '}'. */
    private static int skipBlock(String src, int open) {
        int depth = 0;
        int n = src.length();
        for (int i = open; i < n; i++) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int e = src.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 1;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c) j += src.charAt(j) == '\\' ? 2 : 1;
                i = j;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i + 1;
            }
        }
        return n;
    }

    /** From just after '=', returns the index after the ';' that ends the initialiser. */
    private static int skipInitialiser(String src, int i) {
        int depth = 0;
        int n = src.length();
        for (; i < n; i++) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int e = src.indexOf("*/", i + 2);
                i = e < 0 ? n : e + 1;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c) j += src.charAt(j) == '\\' ? 2 : 1;
                i = j;
            } else if (c == '{' || c == '(' || c == '[') {
                depth++;
            } else if (c == '}' || c == ')' || c == ']') {
                depth--;
            } else if (c == ';' && depth <= 0) {
                return i + 1;
            }
        }
        return n;
    }
}
