package actions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class IniFileReader {
    public static Set<String> readKeysFromIniFiles(Path configFolder) {
        Set<String> keys = new HashSet<>();
        File[] files = configFolder.toFile().listFiles(file -> file.getName().endsWith(".toml"));

        if (files != null) {
            for (File file : files) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("[") && line.contains("=")) {
                            String key = line.split("=", 2)[0].trim();
                            keys.add(key);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return keys;
    }
}
