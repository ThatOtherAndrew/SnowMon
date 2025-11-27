package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PropertiesReader {
    private static final Map<String, String> properties = new HashMap<>();

    public PropertiesReader(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));

        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                // ignore blank lines and comments
                continue;
            }

            String[] parts = stripped.split("=", 2);
            properties.put(parts[0].strip(), parts[1].strip());
        }
    }

    public String getStringProperty(String key, String defaultValue) {
        if (!properties.containsKey(key)) {
            System.err.printf("Key %s not found in properties file, defaulting to %s%n", key, defaultValue);
            return defaultValue;
        }
        return properties.get(key);
    }

    public int getIntProperty(String key, int defaultValue) {
        String value = getStringProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.printf("Key %s is not a valid integer value, defaulting to %d%n", key, defaultValue);
            return defaultValue;
        }
    }
}
