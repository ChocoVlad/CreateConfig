package actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.ini4j.Ini;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.prefs.Preferences;

public class MyInlayProvider {
    public MyInlayProvider() {
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(new EditorMouseMotionAdapter() {
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent event) {
                try {
                    Preferences preferences = Preferences.userNodeForPackage(actions.CreateConfigAction.class);
                    if (preferences.getBoolean("TOOLTIP_PARAMETER", false)) {
                        Editor editor = event.getEditor();
                        LogicalPosition logicalPosition = editor.xyToLogicalPosition(event.getMouseEvent().getPoint());
                        int offset = editor.logicalPositionToOffset(logicalPosition);
                        String wordAtCursor = getWordAt(editor.getDocument().getText(), offset);

                        if (wordAtCursor != null) {
                            // Try to retrieve the current file
                            VirtualFile currentFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
                            if (currentFile != null) {
                                handleCurrentFile(editor, currentFile, wordAtCursor);
                            }
                        } else {
                            editor.getContentComponent().setToolTipText(null);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void handleCurrentFile(Editor editor, VirtualFile currentFile, String wordAtCursor) {
        try {
            String currentFilePath = currentFile.getPath();
            String configFolderPath = new File(currentFilePath).getParent() + File.separator + "config";

            // Просматриваем все файлы .ini в папке config
            List<String> results = new ArrayList<>();

            // Если текущий файл не является config.ini, то мы добавляем параметр из config.ini
            if (!currentFilePath.endsWith("config.ini")) {
                File configFile = new File(new File(currentFilePath).getParent() + File.separator + "config.ini");
                String value = findValueForKey(configFile, wordAtCursor);
                if (value != null) {
                    results.add("<b>config.ini</b> : " + value);
                    results.add("");
                }
            }

            File configFolder = new File(configFolderPath);
            File[] files = configFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".ini"));

            if (files != null) {
                for (File file : files) {
                    String filename = file.getName();
                    String fileNameWithoutExtension = filename.substring(0, filename.lastIndexOf('.'));
                    String value = findValueForKey(file, wordAtCursor);
                    if (value != null) {
                        results.add("<b>" + fileNameWithoutExtension + "</b> : " + value);
                    }
                }
            }

            if (!results.isEmpty()) {
                editor.getContentComponent().setToolTipText("<html>" + String.join("<br>", results) + "</html>");
            } else {
                editor.getContentComponent().setToolTipText(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getWordAt(String text, int offset) {
        int startOffset = offset;
        int endOffset = offset;

        while (startOffset > 0 && Character.isJavaIdentifierPart(text.charAt(startOffset - 1))) {
            startOffset--;
        }

        while (endOffset < text.length() && Character.isJavaIdentifierPart(text.charAt(endOffset))) {
            endOffset++;
        }

        if (startOffset == endOffset) {
            return null;
        } else {
            return text.substring(startOffset, endOffset);
        }
    }

    private String findValueForKey(File file, String key) {
        try {
            Ini ini = new Ini(file);
            for (String sectionName : ini.keySet()) {
                Ini.Section section = ini.get(sectionName);
                if (section.containsKey(key)) {
                    return section.get(key);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
