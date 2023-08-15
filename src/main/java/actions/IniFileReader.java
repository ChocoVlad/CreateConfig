package actions;

import org.ini4j.Ini;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class IniFileReader {
    public static Set<String> readKeysFromIniFiles(Path configFolder) {
        Set<String> keys = new HashSet<>();
        File[] files = configFolder.toFile().listFiles(file -> file.getName().endsWith(".ini"));

        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    Ini ini = new Ini(reader);

                    for (Ini.Section section : ini.values()) {
                        keys.addAll(section.keySet());
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return keys;
    }
}
