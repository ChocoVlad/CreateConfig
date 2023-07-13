package actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionAdapter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class MyInlayProvider {
    public MyInlayProvider() {
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(new EditorMouseMotionAdapter() {
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent event) {
                Editor editor = event.getEditor();
                LogicalPosition logicalPosition = editor.xyToLogicalPosition(event.getMouseEvent().getPoint());
                int offset = editor.logicalPositionToOffset(logicalPosition);
                String wordAtCursor = getWordAt(editor.getDocument().getText(), offset);

                if (wordAtCursor != null) {
                    // Получаем путь к папке config
                    VirtualFile currentFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
                    if (currentFile != null) {
                        String currentFilePath = currentFile.getPath();
                        String configFolderPath = new File(currentFilePath).getParent() + File.separator + "config";

                        // Просматриваем все файлы .ini в папке config
                        List<String> results = new ArrayList<>();
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
                    }
                } else {
                    editor.getContentComponent().setToolTipText(null);
                }
            }
        });
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
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties.getProperty(key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
