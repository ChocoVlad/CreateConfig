package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MyInlayProvider extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // Получаем текущий проект
        Project project = e.getProject();

        if (project != null) {
            // Получаем текущий текстовый редактор
            Editor editor = e.getData(PlatformDataKeys.EDITOR);

            // Проверяем, есть ли выделенный текст
            if (editor != null && editor.getSelectionModel().hasSelection()) {
                // Получаем выделенный текст
                String selectedText = editor.getSelectionModel().getSelectedText();

                // Проверяем, является ли редактор экземпляром EditorEx (чтобы получить позицию выделения)
                if (editor instanceof EditorEx) {
                    // Получаем позицию выделения
                    int selectionStart = editor.getSelectionModel().getSelectionStart();
                    VisualPosition startPosition = editor.offsetToVisualPosition(selectionStart);
                    Point startPoint = editor.visualPositionToXY(startPosition);

                    // Получаем ширину и высоту символов
                    int charWidth = editor.getColorsScheme().getFont(EditorFontType.PLAIN).getSize();
                    int lineHeight = editor.getLineHeight();

                    // Создаем прямоугольник позиции выделения
                    Rectangle startRect = new Rectangle(startPoint.x, startPoint.y, charWidth * selectedText.length(), lineHeight);

                    // Получаем путь к папке config
                    String currentFilePath = e.getData(PlatformDataKeys.VIRTUAL_FILE).getPath();
                    String configFolderPath = new File(currentFilePath).getParent() + File.separator + "config";

                    // Просматриваем все файлы .ini в папке config
                    List<String> results = new ArrayList<>();
                    File configFolder = new File(configFolderPath);
                    File[] files = configFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".ini"));

                    if (files != null) {
                        for (File file : files) {
                            String filename = file.getName();
                            String fileNameWithoutExtension = filename.substring(0, filename.lastIndexOf('.'));
                            String value = findValueForKey(file, selectedText);
                            if (value != null) {
                                results.add(fileNameWithoutExtension + " : " + value);
                            }
                        }
                    }

                    // Создаем панель для отображения списка значений
                    JPanel panel = new JPanel(new BorderLayout());
                    panel.setBackground(Color.WHITE);

                    // Создаем кастомный рендерер для списка
                    DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
                        @Override
                        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                            if (component instanceof JLabel) {
                                JLabel label = (JLabel) component;
                                String text = value.toString();
                                int separatorIndex = text.indexOf(" : ");
                                if (separatorIndex > 0) {
                                    String fileName = text.substring(0, separatorIndex);
                                    String fileValue = text.substring(separatorIndex + 3);
                                    Font font = label.getFont();
                                    if (index == -1) { // Название файла
                                        font = font.deriveFont(Font.BOLD); // Установка жирного стиля шрифта
                                        label.setHorizontalAlignment(SwingConstants.CENTER); // Установка выравнивания по центру
                                    } else {
                                        label.setHorizontalAlignment(SwingConstants.LEFT); // Установка выравнивания слева для значений
                                    }
                                    label.setFont(font);
                                    label.setText("<html><b>" + fileName + "</b> : " + fileValue + "</html>"); // Включение тегов HTML для применения стиля
                                }
                            }
                            return component;
                        }
                    };

                    JList<String> list = new JList<>(results.toArray(new String[0]));
                    list.setCellRenderer(renderer);

                    JScrollPane scrollPane = new JScrollPane(list);
                    panel.add(scrollPane, BorderLayout.CENTER);

                    // Получаем высоту содержимого списка
                    int listHeight = list.getPreferredSize().height;

                    // Добавляем небольшой запас к высоте списка
                    int extraHeight = 5;
                    int panelHeight = listHeight + extraHeight;

                    // Устанавливаем высоту панели на основе высоты списка с запасом
                    Dimension panelSize = new Dimension(scrollPane.getPreferredSize().width + extraHeight, panelHeight);
                    panel.setPreferredSize(panelSize);
                    panel.setMinimumSize(panelSize);
                    panel.setMaximumSize(panelSize);

                    // Создаем всплывающую подсказку
                    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(panel)
                            .setFillColor(JBColor.WHITE)
                            .setBorderColor(JBColor.GRAY)
                            .setShadow(true)
                            .setHideOnClickOutside(true)
                            .setHideOnAction(true)
                            .createBalloon();

                    // Отображаем всплывающую подсказку рядом с текстом
                    SwingUtilities.getWindowAncestor(editor.getComponent()).toFront(); // Перемещаем окно редактора на передний план
                    RelativePoint target = new RelativePoint(editor.getComponent(), new Point(startPoint.x + charWidth * selectedText.length(), startPoint.y + editor.getLineHeight()));
                    balloon.show(target, Balloon.Position.below);


                    // Добавляем слушатель событий документа для закрытия подсказки при изменении текста
                    editor.getDocument().addDocumentListener(new DocumentListener() {
                        @Override
                        public void beforeDocumentChange(@NotNull DocumentEvent event) {}

                        @Override
                        public void documentChanged(@NotNull DocumentEvent event) {
                            balloon.hide();
                        }
                    });

                    // Добавляем слушатель событий компонента редактора для закрытия подсказки при изменении размера окна
                    editor.getComponent().addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentResized(ComponentEvent e) {
                            balloon.hide();
                        }
                    });
                }
            }
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
