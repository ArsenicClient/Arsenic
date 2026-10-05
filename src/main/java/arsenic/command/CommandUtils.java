package arsenic.command;

import arsenic.module.Module;
import arsenic.module.property.Property;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.FolderProperty;
import arsenic.module.property.impl.StringProperty;
import arsenic.module.property.impl.TextProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class CommandUtils {

    private CommandUtils() {}

    public static final class PropMatch {
        public final Property<?> property;
        public final String[] valueArgs;

        private PropMatch(Property<?> property, String[] valueArgs) {
            this.property = property;
            this.valueArgs = valueArgs;
        }
    }

    public static List<Property<?>> getAllProperties(Module module) {
        List<Property<?>> out = new ArrayList<>();
        for (Property<?> p : module.getProperties()) {
            out.add(p);
            if (p instanceof FolderProperty)
                out.addAll(((FolderProperty) p).getValue());
        }
        return out;
    }

    public static boolean isSettable(Property<?> p) {
        return p instanceof BooleanProperty || p instanceof DoubleProperty || p instanceof RangeProperty
                || p instanceof EnumProperty || p instanceof ColourProperty || p instanceof StringProperty
                || p instanceof TextProperty;
    }

    public static List<Property<?>> getSettableProperties(Module module) {
        return getAllProperties(module).stream().filter(CommandUtils::isSettable).collect(Collectors.toList());
    }

    public static String displayName(Property<?> p) {
        return p.getName().replace(' ', '_');
    }

    private static String normalize(String s) {
        return s.replace('_', ' ');
    }

    public static List<String> getSettingNames(Module module) {
        return getSettableProperties(module).stream().map(CommandUtils::displayName).collect(Collectors.toList());
    }

    public static PropMatch matchProperty(Module module, String[] args) {
        if (args.length == 0)
            return null;
        List<Property<?>> props = getAllProperties(module);
        for (int take = args.length; take >= 1; take--) {
            String joined = normalize(String.join(" ", Arrays.copyOfRange(args, 0, take)));
            for (Property<?> p : props) {
                if (isSettable(p) && normalize(p.getName()).equalsIgnoreCase(joined))
                    return new PropMatch(p, Arrays.copyOfRange(args, take, args.length));
            }
        }
        return null;
    }

    public static String describe(Property<?> p) {
        if (p instanceof DoubleProperty)
            return ((DoubleProperty) p).getValueString();
        if (p instanceof RangeProperty)
            return ((RangeProperty) p).getValueString();
        if (p instanceof EnumProperty)
            return ((EnumProperty<?>) p).getValue().name();
        if (p instanceof BooleanProperty)
            return String.valueOf(((BooleanProperty) p).getValue());
        if (p instanceof ColourProperty)
            return "#" + String.format("%08X", ((ColourProperty) p).getValue());
        return String.valueOf(p.getValue());
    }

    public static List<String> valueSuggestions(Property<?> p) {
        if (p instanceof EnumProperty)
            return ((EnumProperty<?>) p).getModeNames();
        if (p instanceof BooleanProperty)
            return new ArrayList<>(Arrays.asList("true", "false", "toggle"));
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static String applyValue(Property<?> p, String[] valueArgs) {
        String name = displayName(p);
        try {
            if (p instanceof BooleanProperty) {
                BooleanProperty bp = (BooleanProperty) p;
                Boolean parsed = parseBoolean(valueArgs.length > 0 ? valueArgs[0] : "", bp.getValue());
                if (parsed == null)
                    return "'" + (valueArgs.length > 0 ? valueArgs[0] : "") + "' is not true/false/toggle";
                bp.setValue(parsed);
                return name + " set to " + parsed;
            }
            if (p instanceof DoubleProperty) {
                if (valueArgs.length == 0)
                    return name + " needs a number";
                ((DoubleProperty) p).getValue().setInput(Double.parseDouble(valueArgs[0]));
                return name + " set to " + ((DoubleProperty) p).getValueString();
            }
            if (p instanceof RangeProperty) {
                if (valueArgs.length < 2)
                    return name + " needs a min and a max, e.g. '2 4'";
                RangeProperty rp = (RangeProperty) p;
                rp.getValue().setMin(Double.parseDouble(valueArgs[0]));
                rp.getValue().setMax(Double.parseDouble(valueArgs[1]));
                return name + " set to " + rp.getValueString();
            }
            if (p instanceof EnumProperty) {
                EnumProperty<?> ep = (EnumProperty<?>) p;
                if (valueArgs.length == 0 || !ep.setByName(valueArgs[0]))
                    return name + " modes: " + String.join(", ", ep.getModeNames());
                return name + " set to " + ep.getValue().name();
            }
            if (p instanceof ColourProperty) {
                if (valueArgs.length == 0)
                    return name + " needs a hex colour, e.g. FFFF0000";
                String hex = valueArgs[0].replace("#", "").replace("0x", "").replace("0X", "");
                ((ColourProperty) p).setValue((int) Long.parseLong(hex, 16));
                return name + " set to #" + hex.toUpperCase();
            }
            if (p instanceof TextProperty) {
                ((TextProperty) p).setValue(String.join(" ", valueArgs));
                return name + " set to " + p.getValue();
            }
            if (p instanceof StringProperty) {
                ((Property<String>) p).setValue(String.join(" ", valueArgs));
                return name + " set to " + p.getValue();
            }
        } catch (NumberFormatException e) {
            return "'" + String.join(" ", valueArgs) + "' is not a valid value for " + name;
        }
        return name + " can't be changed with a command";
    }

    private static Boolean parseBoolean(String s, boolean current) {
        switch (s.toLowerCase()) {
            case "true":
            case "on":
            case "yes":
            case "1":
                return true;
            case "false":
            case "off":
            case "no":
            case "0":
                return false;
            case "toggle":
            case "t":
                return !current;
            default:
                return null;
        }
    }
}
