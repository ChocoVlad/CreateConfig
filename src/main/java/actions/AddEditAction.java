package actions;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.tuple.Pair;
import org.ini4j.Wini;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class AddEditAction extends AnAction  {

    private JDialog dialog = null;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (editor != null && virtualFile != null) {
            // Извлечение слова под курсором
            int offset = editor.getCaretModel().getOffset();
            String wordAtCursor = getWordAt(editor.getDocument().getText(), offset);

            // Получение пути к папке config и извлечение имен файлов без расширения
            VirtualFile currentFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
            VirtualFile parentDirectory = currentFile.getParent();
            if (parentDirectory.getPath().endsWith("config")) {
                parentDirectory = parentDirectory.getParent();
            }
            if (parentDirectory == null) {
                return;
            }
            VirtualFile configsDirectory = parentDirectory.findChild("config");
            if (configsDirectory == null) {
                return;
            }
            VirtualFile[] configFiles = configsDirectory.getChildren();
            if (configFiles.length == 0) {
                return;
            }
            // Сортировка по имени файла
            Arrays.sort(configFiles, Comparator.comparing(VirtualFile::getName));

            // Показать диалоговое окно для добавления или редактирования параметра
            showDialog(configFiles, wordAtCursor);
        }
    }

    private void showDialog(VirtualFile[] files, String wordAtCursor) {
        resetFilesParamsList();
        setMainNameParam(wordAtCursor);
        if (dialog != null && dialog.isVisible()) {
            return;
        }
        Frame parentFrame = JOptionPane.getFrameForComponent(null);
        dialog = new JDialog(parentFrame);
        if (wordAtCursor != null) {
            dialog.setTitle(wordAtCursor);
        } else {
            dialog.setTitle("Выставление параметра");
        }
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                resetFilesParamsList();
                dialog = null;
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Создание метки для отображения "Word at Cursor"
        JLabel cursorWordLabel = new JLabel("Название параметра: ");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        panel.add(cursorWordLabel, gbc);

        // Создание текстового поля для отображения значения wordAtCursor
        JTextField cursorWordField = new JTextField(20);
        if (wordAtCursor != null && !wordAtCursor.trim().isEmpty()) {
            cursorWordField.setText(wordAtCursor);
        }
        cursorWordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                setMainNameParam(cursorWordField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                setMainNameParam(cursorWordField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                setMainNameParam(cursorWordField.getText());
            }
        });
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2; // Объединение ячеек для текстового поля
        panel.add(cursorWordField, gbc);

        gbc.gridwidth = 1;
        for (VirtualFile file : files) {
            gbc.gridy++;

            gbc.gridx = 0;
            gbc.weightx = 0.5;
            JLabel label = new JLabel(file.getName() + ": ");
            panel.add(label, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 5, 0, 0);
            JTextField textField = new JTextField(20);
            textField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateTooltip();
                    updatePref();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateTooltip();
                    updatePref();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateTooltip();
                    updatePref();
                }

                private void updateTooltip() {
                    if (textField.getText().length() > 35) {
                        textField.setToolTipText(textField.getText());
                    } else {
                        textField.setToolTipText(null);
                    }
                }
                private void updatePref() {
                    if (textField.getText() != null) {
                        setFileParam(file.getName(), textField.getText(), "new");
                    } else {
                        setFileParam(file.getName(), "", "new");
                    }
                }
            });

            try {
                Pair<String, String> result = findValueAndSectionForKey(file, wordAtCursor);
                String sectionName = result.getLeft();
                String keyValue = result.getRight();
                setSectionParam(file.getName(), sectionName);
                if (keyValue != null) {
                    textField.setText(keyValue);
                    textField.setCaretPosition(0);
                    setFileParam(file.getName(), keyValue, "old");
                } else {
                    setFileParam(file.getName(), "", "new");
                    setFileParam(file.getName(), "", "old");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            panel.add(textField, gbc);

            gbc.gridx = 2;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.insets = new Insets(0, 5, 0, 0);

            // Создание кнопки копирования
            JButton copyButton = new JButton(AllIcons.Actions.Copy);
            copyButton.setBorderPainted(false);
            copyButton.setContentAreaFilled(false);
            copyButton.setFocusPainted(false);
            copyButton.setOpaque(false);

            // Установка размера кнопки в соответствии
            copyButton.setPreferredSize(new Dimension(16, 16));
            copyButton.addActionListener(e -> {
                String textToCopy = textField.getText();
                StringSelection stringSelection = new StringSelection(textToCopy);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
            });
            copyButton.setPreferredSize(new Dimension(16, 16));
            copyButton.setToolTipText("Скопировать значение из " + file.getName());
            panel.add(copyButton, gbc);

            gbc.fill = GridBagConstraints.HORIZONTAL;
        }
        JButton saveButton = new JButton("Обновить файлы");
        saveButton.addActionListener(e -> {
            for (VirtualFile file : files) {
                try {
                    if (cursorWordField.getText() != null) {
                        String newSection = getSectionParam(file.getName());
                        String newValueParam = getFileParam(file.getName());
                        if (newValueParam != null) {
                            File iniFile = new File(file.getPath());
                            iniFile.createNewFile();
                            Wini ini = new Wini();
                            ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
                            ini.getConfig().setLowerCaseOption(false);
                            ini.load(iniFile);
                            ini.get(newSection).put(getMainNameParam(), newValueParam);
                            ini.store(iniFile);

                            file.refresh(false, false);
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Notification notification = new Notification("AddEditAction", "Ошибка", "Ошибка при записи параметров в " + file.getName(), NotificationType.ERROR);
                    Notifications.Bus.notify(notification);
                }
            }
            dialog.dispose();
            resetFilesParamsList();
        });
        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> {
            dialog.dispose();
            resetFilesParamsList();
        });
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonsPanel.add(saveButton);
        buttonsPanel.add(closeButton);
        dialog.getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
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
    private Pair<String, String> findValueAndSectionForKey(VirtualFile file, String key) {
        try {
            File iniFile = new File(file.getPath());
            iniFile.createNewFile();
            Wini ini = new Wini();
            ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
            ini.getConfig().setLowerCaseOption(false);
            ini.load(iniFile);
            for (String sectionName : ini.keySet()) {
                if (ini.get(sectionName, key) != null) {
                    String value = ini.get(sectionName, key);
                    return Pair.of(sectionName, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null; // Секция и ключ не найдены
    }

    public void resetFilesParamsList() {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        preferences.remove("filesparamslistnow");
    }

    public void setMainNameParam(String mainNameParam) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        if (mainNameParam == null) {
            preferences.put("nameparammain", "");
        } else {
            preferences.put("nameparammain", mainNameParam);
        }
    }

    public String getMainNameParam() {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        String mainName = preferences.get("nameparammain", "");
        return mainName;
    }

    public void setFileParam(String fileName, String value, String type) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        String json = preferences.get("filesparamslistnow", "{}");
        Gson gson = new Gson();

        // Преобразование JSON строки в объект
        Map<String, Map<String, String>> filesParams = gson.fromJson(json, new TypeToken<Map<String, Map<String, String>>>(){}.getType());

        // Получение текущих параметров файла или создание новых
        Map<String, String> fileParams = filesParams.getOrDefault(fileName, new HashMap<>());
        String key = type + "name";
        fileParams.put(key, value);
        filesParams.put(fileName, fileParams);

        // Сохранение обновленного JSON
        preferences.put("filesparamslistnow", gson.toJson(filesParams));
    }

    public String getFileParam(String fileName) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        String json = preferences.get("filesparamslistnow", "{}");
        Gson gson = new Gson();

        // Преобразование JSON строки в объект
        Map<String, Map<String, String>> filesParams = gson.fromJson(json, new TypeToken<Map<String, Map<String, String>>>(){}.getType());

        // Получение значений для файла
        if (filesParams.containsKey(fileName)) {
            String oldValue = filesParams.get(fileName).get("oldname"); // Обрезание пробелов
            String newValue = filesParams.get(fileName).get("newname"); // Обрезание пробелов

            if (oldValue == null && newValue != null) {
                return newValue;
            }
            if (!oldValue.equals(newValue)) {
                return newValue;
            }
        }
        return null;
    }

    public void setSectionParam(String sectionName, String value) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        String json = preferences.get("sectionsParamsListNow", "{}");
        Gson gson = new Gson();

        // Преобразование JSON строки в объект
        Map<String, Map<String, String>> sectionsParams = gson.fromJson(json, new TypeToken<Map<String, Map<String, String>>>(){}.getType());

        // Получение текущих параметров секции или создание новых
        Map<String, String> sectionParams = sectionsParams.getOrDefault(sectionName, new HashMap<>());
        sectionParams.put("section", value);
        sectionsParams.put(sectionName, sectionParams);

        // Сохранение обновленного JSON
        preferences.put("sectionsParamsListNow", gson.toJson(sectionsParams));
    }
    public String getSectionParam(String fileName) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        String json = preferences.get("sectionsParamsListNow", "{}");
        Gson gson = new Gson();

        // Преобразование JSON строки в объект
        Map<String, Map<String, String>> filesParams = gson.fromJson(json, new TypeToken<Map<String, Map<String, String>>>(){}.getType());

        // Получение значений для файла
        if (filesParams.containsKey(fileName)) {

            String newValue = filesParams.get(fileName).getOrDefault("section", "").trim();

            if (newValue == "") {
                return "custom";
            } else {
                return newValue;
            }
        }
        return null;
    }
}
