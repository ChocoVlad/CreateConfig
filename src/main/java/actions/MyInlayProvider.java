package actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;
import java.util.prefs.Preferences;

/**
 * Предоставляет встраиваемые элементы для параметров конфигурации.
 */
public class MyInlayProvider implements Disposable {
    private final Disposable myDisposable = Disposer.newDisposable();

    public MyInlayProvider() {
        EditorFactory.getInstance().getEventMulticaster().addEditorMouseMotionListener(new EditorMouseMotionListener() {
            @Override
            public void mouseMoved(@NotNull EditorMouseEvent event) {
                try {
                    Preferences preferences = Preferences.userNodeForPackage(CreateConfigAction.class);
                    if (preferences.getBoolean("TOOLTIP", true)) {
                        Editor editor = event.getEditor();
                        LogicalPosition logicalPosition = editor.xyToLogicalPosition(event.getMouseEvent().getPoint());
                        int offset = editor.logicalPositionToOffset(logicalPosition);
                        String wordAtCursor = getWordAt(editor.getDocument().getText(), offset);

                        if (wordAtCursor != null) {
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
        }, myDisposable);
    }

    @Override
    public void dispose() {
        Disposer.dispose(myDisposable);
    }

    /**
     * Обрабатывает текущий файл.
     * @param editor - текущий экземпляр редактора
     * @param currentFile - файл под курсором
     * @param wordAtCursor - слово под курсором
     */
    private void handleCurrentFile(Editor editor, VirtualFile currentFile, String wordAtCursor) {
        try {
            String currentFilePath = currentFile.getPath();
            String configFolderParent = new File(currentFilePath).getParent();
            String configFolderPath = "";
            if (configFolderParent.endsWith("config")) {
                configFolderPath = configFolderParent;
            } else {
                configFolderPath = configFolderParent + File.separator + "config";
            }

            List<String> results = new ArrayList<>();

            if (!currentFilePath.endsWith("config.ini")) {
                File configFile = new File(new File(currentFilePath).getParent() + File.separator + "config.ini");
                String value = findValueForKey(configFile, wordAtCursor);
                if (value != null) {
                    results.add("<b>config.ini</b> : " + value);
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
                String tooltip = "<html><center><b>" + wordAtCursor + "</b></center>" + "<hr>" + String.join("<br>", results) + "</html>";
                editor.getContentComponent().setToolTipText(tooltip);
            } else {
                editor.getContentComponent().setToolTipText(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Получает слово на конкретном смещении.
     * @param text - текст, в котором ищется слово
     * @param offset - смещение, с которого начинается поиск
     * @return - найденное слово по смещению или null, если не найдено
     */
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

    /**
     * Находит значение для конкретного ключа в файле.
     * @param file - файл, в котором ищется ключ
     * @param key - ключ, для которого ищется значение
     * @return - найденное значение для ключа или null, если не найдено
     */
    private String findValueForKey(File file, String key) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            Pattern pattern = Pattern.compile("^\\s*" + Pattern.quote(key) + "\\s*=\\s*(.*)$");
            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
