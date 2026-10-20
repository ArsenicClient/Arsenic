package arsenic.module.property;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Turns a module's {@link ShowWhen} (and old {@link PropertyInfo}) annotations into visibility rules, checking each one. */
@SuppressWarnings("deprecation")
public final class Visibility {

    private Visibility() {
    }

    /** One checked rule: shown while target is shown and its value is (or is not) one of values. */
    private static final class Rule {
        final Property<?> target;
        final IReliable reliable;
        final Set<String> values;
        final boolean negate;
        final String text;

        Rule(Property<?> target, Set<String> values, boolean negate, String text) {
            this.target = target;
            this.reliable = (IReliable) target;
            this.values = values;
            this.negate = negate;
            this.text = text;
        }

        boolean holds() {
            return target.isVisible() && values.contains(reliable.currentValue()) != negate;
        }
    }

    /**
     * Wires the rules of every public property field of owner. A rule with a problem is left out, so its setting stays
     * visible rather than becoming unreachable.
     * @return the problems found, one line each (empty when everything is fine)
     */
    public static List<String> wire(Object owner) {
        List<String> problems = new ArrayList<>();
        Map<Field, Property<?>> fields = new LinkedHashMap<>();
        for (Field field : owner.getClass().getFields()) {
            Object value = read(field, owner);
            if (value instanceof Property)
                fields.put(field, (Property<?>) value);
        }

        Map<String, Property<?>> byName = new LinkedHashMap<>();
        Map<String, Property<?>> byField = new LinkedHashMap<>();
        for (Map.Entry<Field, Property<?>> e : fields.entrySet()) {
            byName.putIfAbsent(e.getValue().getName().toLowerCase(Locale.ROOT), e.getValue());
            byField.putIfAbsent(e.getKey().getName().toLowerCase(Locale.ROOT), e.getValue());
        }

        Map<Property<?>, List<Rule>> rules = new IdentityHashMap<>();
        for (Map.Entry<Field, Property<?>> e : fields.entrySet()) {
            Field field = e.getKey();
            Property<?> property = e.getValue();
            String where = "\"" + property.getName() + "\" (" + field.getName() + ")";
            for (ShowWhen showWhen : field.getAnnotationsByType(ShowWhen.class))
                addRule(rules, problems, where, property, byName, byField,
                        showWhen.property(), showWhen.is(), showWhen.not(), "@ShowWhen");
            PropertyInfo info = field.getAnnotation(PropertyInfo.class);
            if (info != null)
                addRule(rules, problems, where, property, byName, byField,
                        info.reliesOn(), new String[] { info.value() }, new String[0], "@PropertyInfo");
        }

        // a setting that depends on itself, directly or through others, would recurse forever when drawn
        for (Map.Entry<Property<?>, List<Rule>> e : rules.entrySet())
            e.getValue().removeIf(rule -> {
                if (!reaches(rule.target, e.getKey(), rules, new HashSet<>()))
                    return false;
                problems.add("\"" + e.getKey().getName() + "\": " + rule.text + " is circular (\""
                        + rule.target.getName() + "\" depends back on it), so it is ignored");
                return true;
            });

        for (Map.Entry<Property<?>, List<Rule>> e : rules.entrySet()) {
            List<Rule> list = e.getValue();
            if (list.isEmpty())
                continue;
            Supplier<Boolean> visible = list.size() == 1
                    ? list.get(0)::holds
                    : () -> {
                        for (Rule rule : list)
                            if (!rule.holds())
                                return false;
                        return true;
                    };
            e.getKey().setVisible(visible);
        }

        // annotations on fields that are not settings at all
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (Field field : c.getDeclaredFields())
                if (!Modifier.isPublic(field.getModifiers())
                        && (field.getAnnotationsByType(ShowWhen.class).length > 0 || field.isAnnotationPresent(PropertyInfo.class)))
                    problems.add(field.getName() + " has @ShowWhen but is not public, so it is not a setting");
        return problems;
    }

    private static void addRule(Map<Property<?>, List<Rule>> rules, List<String> problems, String where,
                                Property<?> property, Map<String, Property<?>> byName, Map<String, Property<?>> byField,
                                String targetName, String[] is, String[] not, String annotation) {
        String text = annotation + "(\"" + targetName + "\")";
        String key = targetName == null ? "" : targetName.trim().toLowerCase(Locale.ROOT);
        Property<?> target = byName.containsKey(key) ? byName.get(key) : byField.get(key);

        if (target == null) {
            problems.add(where + ": " + text + " names no setting of this module. Settings that can be used: "
                    + dependable(byName));
            return;
        }
        if (target == property) {
            problems.add(where + ": " + text + " names the setting itself");
            return;
        }
        if (!(target instanceof IReliable)) {
            problems.add(where + ": " + text + " is a " + target.getClass().getSimpleName()
                    + "; only on/off (BooleanProperty) and mode (EnumProperty) settings can be depended on");
            return;
        }
        boolean negate = is.length == 0;
        String[] wanted = negate ? not : is;
        if (wanted.length == 0 || (is.length > 0 && not.length > 0)) {
            problems.add(where + ": " + text + " needs either is = ... or not = ..., not " + (wanted.length == 0 ? "neither" : "both"));
            return;
        }

        List<String> possible = ((IReliable) target).possibleValues();
        Set<String> values = new HashSet<>();
        for (String v : wanted) {
            String match = null;
            for (String p : possible)
                if (p.equalsIgnoreCase(v == null ? "" : v.trim()))
                    match = p;
            if (match == null) {
                problems.add(where + ": " + text + ": \"" + v + "\" is not a value of \"" + target.getName()
                        + "\" (it can be " + String.join(", ", possible) + ")");
                return;
            }
            values.add(match);
        }
        rules.computeIfAbsent(property, p -> new ArrayList<>()).add(new Rule(target, values, negate, text));
    }

    private static boolean reaches(Property<?> from, Property<?> goal, Map<Property<?>, List<Rule>> rules, Set<Property<?>> seen) {
        if (from == goal)
            return true;
        if (!seen.add(from))
            return false;
        List<Rule> list = rules.get(from);
        if (list != null)
            for (Rule rule : list)
                if (reaches(rule.target, goal, rules, seen))
                    return true;
        return false;
    }

    private static String dependable(Map<String, Property<?>> byName) {
        String names = byName.values().stream().filter(p -> p instanceof IReliable)
                .map(p -> "\"" + p.getName() + "\"").collect(Collectors.joining(", "));
        return names.isEmpty() ? "none" : names;
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
