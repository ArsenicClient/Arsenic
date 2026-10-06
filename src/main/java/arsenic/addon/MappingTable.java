package arsenic.addon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP name <-> SRG name table for Minecraft members, generated at build time into /addon-mappings.txt.
 * Lines: "F owner mcpName srgName" and "M owner mcpName desc srgName". Owner is always the declaring class.
 */
public final class MappingTable {

    private final Map<String, String> methodMcpToSrg = new HashMap<>(); // owner.mcp+desc -> srg
    private final Map<String, String> fieldMcpToSrg = new HashMap<>();  // owner.mcp -> srg
    private final Map<String, String> methodSrgToMcp = new HashMap<>(); // owner.srg -> mcp
    private final Map<String, String> fieldSrgToMcp = new HashMap<>();  // owner.srg -> mcp

    public static MappingTable load(InputStream in) throws IOException {
        MappingTable table = new MappingTable();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split(" ");
                if (p.length == 4 && p[0].equals("F")) {
                    table.fieldMcpToSrg.put(p[1] + '.' + p[2], p[3]);
                    table.fieldSrgToMcp.put(p[1] + '.' + p[3], p[2]);
                } else if (p.length == 5 && p[0].equals("M")) {
                    table.methodMcpToSrg.put(p[1] + '.' + p[2] + p[3], p[4]);
                    table.methodSrgToMcp.put(p[1] + '.' + p[4], p[2]);
                }
            }
        }
        return table;
    }

    public String methodToSrg(String owner, String mcpName, String desc) {
        return methodMcpToSrg.get(owner + '.' + mcpName + desc);
    }

    public String fieldToSrg(String owner, String mcpName) {
        return fieldMcpToSrg.get(owner + '.' + mcpName);
    }

    public String methodToMcp(String owner, String srgName) {
        return methodSrgToMcp.get(owner + '.' + srgName);
    }

    public String fieldToMcp(String owner, String srgName) {
        return fieldSrgToMcp.get(owner + '.' + srgName);
    }

    public int size() {
        return methodMcpToSrg.size() + fieldMcpToSrg.size();
    }
}
