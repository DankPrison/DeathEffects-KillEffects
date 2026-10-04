package com.dank420girl.core.testing;

import com.dank420girl.core.Perm;
import com.dank420girl.core.Variant;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks the plugin.yml Gradle builds for each plugin from core/src/main/templates/plugin.yml. */
public final class PluginChecks {
    private PluginChecks() {
    }

    /** plugin.yml names the right main class, the commands and all permissions of the README. */
    public static void checkPluginYml(Variant variant, String mainClass) throws Exception {
        String text = new String(readAll(PluginChecks.class.getClassLoader().getResourceAsStream("plugin.yml")), "UTF-8");
        PluginDescriptionFile description = new PluginDescriptionFile(new StringReader(text));
        check(description.getName().equals(variant.name()), "name");
        check(variant.description().equals(description.getDescription()), "description matches the core's Variant");
        check(description.getMain().equals(mainClass), "main class");
        check(Class.forName(mainClass) != null, "main class exists");
        check(!description.getVersion().contains("$"), "version is filled in: " + description.getVersion());
        Map<String, Map<String, Object>> commands = description.getCommands();
        check(commands.size() == 1 && commands.containsKey(variant.id()), "one command " + variant.id());
        Object aliases = commands.get(variant.id()).get("aliases");
        String singular = variant.id().substring(0, variant.id().length() - 1);
        check(aliases instanceof List && ((List<?>) aliases).containsAll(Arrays.asList(variant.alias(), singular)),
                "aliases " + aliases);

        // Permissions are read as YAML: Bukkit's Permission class needs a running server.
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/'); // permission nodes contain dots
        yaml.loadFromString(text);
        ConfigurationSection permissions = yaml.getConfigurationSection("permissions");
        check(permissions != null, "permissions");
        Map<String, Object> nodes = permissions.getValues(false);
        Set<String> declared = new HashSet<String>();
        // Exactly the permissions of the core's Perm, with the same defaults and children.
        for (Perm perm : Perm.values()) {
            String node = variant.permission(perm);
            declared.add(node);
            Map<?, ?> permission = map(nodes.get(node));
            check(permission != null, "plugin.yml declares " + node);
            check(PermissionDefault.getByName(String.valueOf(permission.get("default"))) == perm.defaultValue(),
                    node + " defaults to " + perm.defaultValue());
            check(String.valueOf(permission.get("description")).length() > 10, node + " has a description");
            Set<String> children = new HashSet<String>();
            Map<?, ?> declaredChildren = map(permission.get("children"));
            if (declaredChildren != null) {
                for (Map.Entry<?, ?> child : declaredChildren.entrySet()) {
                    check(Boolean.TRUE.equals(child.getValue()), node + " grants " + child.getKey());
                    children.add(String.valueOf(child.getKey()));
                }
            }
            Set<String> expectedChildren = new HashSet<String>();
            for (Perm child : perm.children()) {
                expectedChildren.add(variant.permission(child));
            }
            check(children.equals(expectedChildren), node + " has the children " + expectedChildren + ", not " + children);
        }
        check(declared.equals(nodes.keySet()), "plugin.yml declares nothing else: " + nodes.keySet());
        // Every permission is reachable from the wildcard.
        Set<Perm> reached = new HashSet<Perm>();
        collect(Perm.ALL, reached);
        for (Perm perm : Perm.values()) {
            check(reached.contains(perm) || perm == Perm.EFFECTS_OTHERS, variant.permission(Perm.ALL) + " grants " + variant.permission(perm));
        }
        check(yaml.getBoolean("folia-supported"), "folia-supported");
        check("1.13".equals(yaml.getString("api-version")), "api-version");
    }

    private static void collect(Perm perm, Set<Perm> into) {
        if (into.add(perm)) {
            for (Perm child : perm.children()) {
                collect(child, into);
            }
        }
    }

    private static Map<?, ?> map(Object value) {
        if (value instanceof ConfigurationSection) {
            return ((ConfigurationSection) value).getValues(false);
        }
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        check(in != null, "plugin.yml exists");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
