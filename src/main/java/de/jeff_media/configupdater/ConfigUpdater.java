package de.jeff_media.configupdater;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfigUpdater extends JavaPlugin {

    private final Plugin plugin;

    private final String fileName;
    private final File file;

    private final YamlConfiguration existingYaml;
    //private final YamlConfiguration defaultYaml;
    private final YamlConfiguration updateInstructions;

    private final List<String> stringlists;
    private final List<String> doublequotes;
    private final List<String> ignored;
    private final List<String> escapeNewlines;

    private final List<String> newFileAsList = new ArrayList<>();

    public ConfigUpdater(Plugin plugin, String fileName, String updateInstructionsFileName) throws IOException {

        this.plugin = plugin;
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);
        this.existingYaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaultFile = plugin.getResource(fileName);
             InputStreamReader defaultFileReader = new InputStreamReader(defaultFile, StandardCharsets.UTF_8);

             InputStream updateInstructionsFile = plugin.getResource(updateInstructionsFileName);
             InputStreamReader updateInstructionsReader = new InputStreamReader(updateInstructionsFile, StandardCharsets.UTF_8)

        ) {

            BufferedReader defaultFileBufferedReader = new BufferedReader(defaultFileReader);
            String line;
            while ((line = defaultFileBufferedReader.readLine()) != null) {
                newFileAsList.add(line);
            }
            defaultFileReader.reset();

            //defaultYaml = YamlConfiguration.loadConfiguration(defaultFileReader);
            updateInstructions = YamlConfiguration.loadConfiguration(updateInstructionsReader);
            stringlists = updateInstructions.getStringList("stringlists");
            doublequotes = updateInstructions.getStringList("doublequotes");
            ignored = updateInstructions.getStringList("ignored");
            escapeNewlines = updateInstructions.getStringList("escape-newlines");
        }
    }

    private static long getNewConfigVersion(Plugin plugin) {
        try (final InputStream in = plugin.getResource("/config-version.txt");
             final BufferedReader reader = new BufferedReader(new InputStreamReader(in))
        ) {
            return Long.parseLong(reader.readLine());
        } catch (final IOException ioException) {
            ioException.printStackTrace();
            return 0;
        }
    }

    public static boolean needsUpdate(Plugin plugin) {
        return plugin.getConfig().getLong("config-version") < getNewConfigVersion(plugin);
    }

    private boolean matches(List<String> list, String line) {
        String key = line.split(":")[0];
        for (String regex : list) {
            if (Pattern.matches(regex, key)) return true;
        }
        return false;
    }

    public void update() {
        file.delete();
        //plugin.saveResource(fileName, true);

        final Set<String> existingKeys = existingYaml.getKeys(false);
        final List<String> newConfig = new ArrayList<>();

        for (String line : newFileAsList) {

            String key = line.split(":")[0];

            // Keep comments as they are
            if (line.replace(" ", "").startsWith("#")) {
                newConfig.add(line);
                continue;
            }

            // Do not include default list entries
            if (line.replace(" ", "").startsWith("-")) {
                continue;
            }

            // Do not do anything to ignored lines
            if (matches(ignored, key)) {
                newConfig.add(line);
                continue;
            }

            // Insert StringList values
            if (matches(stringlists, key)) {
                newConfig.add(line);
                for (final String entry : existingYaml.getStringList(key)) {
                    newConfig.add("- " + entry);
                }
                continue;
            }

            // Update regular values
            for (final String oldKey : existingKeys) {
                if (!line.startsWith(oldKey + ":")) continue;
                final String quotes = getQuotes(key);
                String value = Objects.requireNonNull(existingYaml.get(key)).toString();

                // Escape newlines
                if (matches(escapeNewlines, key)) {
                    value = value.replaceAll("\n", "\\\\n");
                }

                newConfig.add(key + ": " + quotes + value + quotes);
            }
        }

        save(newConfig);
    }

    private String getQuotes(String oldKey) {
        if (matches(doublequotes, oldKey)) return "\"";
        return "";
    }

    private void save(List<String> newConfig) {
        try {
            final BufferedWriter fw = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
            for (final String line : newConfig) {
                fw.write(line + System.lineSeparator());
            }
            fw.close();
        } catch (final IOException ioException) {
            ioException.printStackTrace();
        }
    }

}