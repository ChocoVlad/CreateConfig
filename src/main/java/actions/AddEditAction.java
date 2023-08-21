package actions;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddEditAction extends AnAction  {

    private JDialog dialog = null;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        FileDocumentManager.getInstance().saveAllDocuments();
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
            List<VirtualFile> allFilesList = new ArrayList<>(Arrays.asList(configFiles));

            for (VirtualFile virtualFile1 : configFiles) {
                if (virtualFile1.isDirectory()) {
                    VirtualFile[] configFolderFiles = virtualFile1.getChildren();
                    configFolderFiles = Arrays.stream(configFolderFiles)
                            .filter(file -> file.getExtension() != null && file.getExtension().equalsIgnoreCase("ini"))
                            .toArray(VirtualFile[]::new);

                    // Добавление файлов из configFolderFiles в общий список
                    allFilesList.addAll(Arrays.asList(configFolderFiles));
                }
            }

            // Преобразование списка обратно в массив
            VirtualFile[] allFiles = allFilesList.toArray(new VirtualFile[0]);
            // Фильтрация файлов, чтобы оставить только файлы с расширением .ini
            allFiles = Arrays.stream(allFiles)
                    .filter(file -> file.getExtension() != null && file.getExtension().equalsIgnoreCase("ini"))
                    .toArray(VirtualFile[]::new);

            if (allFiles.length == 0) {
                return;
            }
            // Сортировка по имени файла
            Arrays.sort(allFiles, Comparator.comparing(VirtualFile::getName));

            // Получаем проект
            Project project = e.getProject();

            // Показать диалоговое окно для добавления или редактирования параметра
            showDialog(allFiles, wordAtCursor, project);
        }
    }

    private void showDialog(VirtualFile[] files, String wordAtCursor, Project project) {
        resetFilesParamsList();
        resetSectionsParamsList();
        setMainNameParam(wordAtCursor);
        if (dialog != null && dialog.isVisible()) {
            return;
        }

        Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
        dialog = new JDialog(parentWindow);
        dialog.setLocationRelativeTo(parentWindow);
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
                resetSectionsParamsList();
                dialog = null;
            }
        });

        JPanel panel = new JPanel(new GridLayout(0, 2, 0, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Создание метки для отображения "wordAtCursor"
        JLabel cursorWordLabel = new JLabel("Название параметра: ");
        panel.add(cursorWordLabel);

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
        panel.add(cursorWordField);

        for (VirtualFile file : files) {
            if (!file.getParent().getName().equals("config")) {
                JLabel label = new JLabel(file.getParent().getName() + "/" + file.getName() + ": ");
                panel.add(label);
            } else {
                JLabel label = new JLabel(file.getName() + ": ");
                panel.add(label);
            }
            CustomTextField customTextField = new CustomTextField(20, file);
            try {
                Pair<String, String> result = findValueAndSectionForKey(file, wordAtCursor);
                String sectionName = result.getLeft();
                String keyValue = result.getRight();
                if (!file.getParent().getName().equals("config")) {
                    setSectionParam(file.getParent().getName() + "/" + file.getName(), sectionName);
                } else {
                    setSectionParam(file.getName(), sectionName);
                }
                if (keyValue != null) {
                    customTextField.setText(keyValue);
                    customTextField.setCaretPosition(0);
                    if (!file.getParent().getName().equals("config")) {
                        setFileParam(file.getParent().getName() + "/" + file.getName(), keyValue, "old");
                    } else {
                        setFileParam(file.getName(), keyValue, "old");
                    }
                } else {
                    if (!file.getParent().getName().equals("config")) {
                        setFileParam(file.getParent().getName() + "/" + file.getName(), "", "old");
                        setFileParam(file.getParent().getName() + "/" + file.getName(), "", "new");
                    } else {
                        setFileParam(file.getName(), "", "old");
                        setFileParam(file.getName(), "", "new");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            panel.add(customTextField);
        }

        JButton saveButton = new JButton("Обновить файлы");
        saveButton.addActionListener(e -> {
            FileDocumentManager.getInstance().saveAllDocuments();
            for (VirtualFile file : files) {
                try {
                    if (cursorWordField.getText() != null) {
                        String newSection = "";
                        String newValueParam = "";
                        if (!file.getParent().getName().equals("config")) {
                            newSection = getSectionParam(file.getParent().getName() + "/" + file.getName());
                            newValueParam = getFileParam(file.getParent().getName() + "/" + file.getName());
                        } else {
                            newSection = getSectionParam(file.getName());
                            newValueParam = getFileParam(file.getName());
                        }
                        if (newValueParam != null) {
                            File iniFile = new File(file.getPath());
                            iniFile.createNewFile();
                            Wini ini = new Wini();
                            ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
                            ini.getConfig().setLowerCaseOption(false);
                            ini.load(iniFile);
                            if (!ini.containsKey(newSection)) {
                                addSectionConfig(file, newSection);
                            }
                            String mainNameParam = getMainNameParam();
                            replaceParam(file, newSection, mainNameParam, newValueParam);
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
            resetSectionsParamsList();
        });

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> {
            dialog.dispose();
            resetFilesParamsList();
            resetSectionsParamsList();
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
    public void resetSectionsParamsList() {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        preferences.remove("sectionsParamsListNow");
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
            String oldValue = filesParams.get(fileName).get("oldname");
            String newValue = filesParams.get(fileName).get("newname");

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
        return "custom";
    }

    public class CustomTextField extends JPanel {
        private JTextField textField;
        private JButton copyButton;
        private VirtualFile file;

        public CustomTextField(int columns, VirtualFile file) {
            this.file = file;
            setLayout(new BorderLayout());

            textField = new JTextField(columns) {
                @Override
                public void setBounds(int x, int y, int width, int height) {
                    super.setBounds(x, y, width, height);
                }
            };
            add(textField, BorderLayout.CENTER);

            copyButton = new JButton(AllIcons.General.InlineCopy);
            copyButton.setBorderPainted(false);
            copyButton.setContentAreaFilled(false);
            copyButton.setFocusPainted(false);
            copyButton.setOpaque(false);
            copyButton.setPreferredSize(new Dimension(16, 16));
            add(copyButton, BorderLayout.EAST);

            copyButton.addActionListener(e -> {
                copyButton.setIcon(AllIcons.General.InlineCopyHover);

                // Копирование текста в буфер обмена
                String textToCopy = textField.getText();
                if (textToCopy != null) {
                    StringSelection stringSelection = new StringSelection(textToCopy);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, null);
                }

                // Возвращаем фон кнопки к оригинальному цвету
                Timer timer = new Timer(200, event -> copyButton.setIcon(AllIcons.General.InlineCopy));
                timer.setRepeats(false);
                timer.start();

            });

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
                        if (!file.getParent().getName().equals("config")) {
                            setFileParam(file.getParent().getName() + "/" + file.getName(), textField.getText(), "new");
                        } else {
                            setFileParam(file.getName(), textField.getText(), "new");
                        }
                    } else {
                        if (!file.getParent().getName().equals("config")) {
                            setFileParam(file.getParent().getName() + "/" + file.getName(), "", "new");
                        } else {
                            setFileParam(file.getName(), "", "new");
                        }
                    }
                }
            });
        }
        public void setText(String text) {
            textField.setText(text);
        }

        public void setCaretPosition(int position) {
            textField.setCaretPosition(position);
        }
    }

    private void replaceParam(VirtualFile file, String section, String mainNameParam, String newValueParam) throws IOException {
        String regexPattern = "^" + Pattern.quote(mainNameParam) + "\\b\\s*=";
        Pattern pattern = Pattern.compile(regexPattern);

        // Чтение содержимого файла построчно
        List<String> lines = new ArrayList<>();
        boolean inSection = false;
        boolean valueFound = false;
        try (InputStream inputStream = file.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.trim().equals("[" + section + "]")) {
                    inSection = true;
                } else if (inSection && line.trim().startsWith("[")) {
                    inSection = false;
                    if (!valueFound) {
                        lines.add(mainNameParam + " = " + newValueParam); // Вставляем значение, если не найдено
                    }
                }

                if (inSection) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        line = line.replaceFirst(regexPattern + ".*", mainNameParam + " = " + newValueParam);
                        valueFound = true;
                    }
                }
                lines.add(line);
            }

            if (inSection && !valueFound) {
                lines.add(mainNameParam + " = " + newValueParam); // Вставляем значение в конец, если не найдено в последней секции
            }
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                try (OutputStream outputStream = file.getOutputStream(this, file.getModificationStamp(), file.getTimeStamp());
                     OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                     BufferedWriter bufferedWriter = new BufferedWriter(writer)) {

                    for (String line : lines) {
                        bufferedWriter.write(line);
                        bufferedWriter.newLine();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Обновление файла в файловой системе IntelliJ
        file.refresh(false, false);
    }

    private void addSectionConfig(VirtualFile file, String sectionValue) throws IOException {
        // Чтение всего содержимого файла в список строк
        List<String> lines = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream();
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
        }

        // Добавление пустой строки и новой секции в квадратных скобках
        lines.add(""); // Пустая строка
        lines.add("[" + sectionValue + "]");

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                try (OutputStream outputStream = file.getOutputStream(this, file.getModificationStamp(), file.getTimeStamp());
                     OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                     BufferedWriter bufferedWriter = new BufferedWriter(writer)) {

                    for (String line : lines) {
                        bufferedWriter.write(line);
                        bufferedWriter.newLine();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // Обновление файла в файловой системе IntelliJ
        file.refresh(false, false);
    }
}
