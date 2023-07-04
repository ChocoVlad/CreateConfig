package actions;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.ini4j.Wini;

import javax.swing.*;
import javax.swing.event.CellEditorListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonSyntaxException;

public class CreateConfigAction extends AnAction {

    private static final String PREF_PARAMETERS = "parameters";
    private static final String PREF_API_DATA = "API_DATA";
    private static final String PREF_TEST_FILES = "TEST_FILES";

    // Карта для хранения параметров
    private Map<String, Parameter> parameters;

    @Override
    public void actionPerformed(AnActionEvent e) {
        // Получаем проект
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        FileDocumentManager.getInstance().saveAllDocuments();

        // Получаем выбранные файлы
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length == 0) {
            Notification notification = new Notification("ConfigCopy", "Предупреждение", "Нет файла в превью программы", NotificationType.WARNING);
            Notifications.Bus.notify(notification);
            return;
        }
        VirtualFile currentFile = selectedFiles[0];

        // Получаем родительскую директорию
        VirtualFile parentDirectory = currentFile.getParent();
        if (parentDirectory == null) {
            return;
        }

        VirtualFile configsDirectory = parentDirectory.findChild("config");
        if (configsDirectory == null || !configsDirectory.isDirectory()) {
            Notification notification = new Notification("ConfigCopy", "Предупреждение", "На уровне с текущим файлом должна быть папка config", NotificationType.WARNING);
            Notifications.Bus.notify(notification);
            return;
        }

        // Получаем файлы из директории "config"
        VirtualFile[] configFiles = configsDirectory.getChildren();
        if (configFiles.length == 0) {
            return;
        }

        // Создаем контекстное меню
        JPopupMenu popupMenu = new JPopupMenu();

        // Создаем список компонентов меню
        java.util.List<Component> menuComponents = new ArrayList<>();

        for (VirtualFile configFile : configFiles) {
            if (!configFile.isDirectory()) {
                // Получаем имя файла без расширения
                String fileName = configFile.getNameWithoutExtension();
                // Создаем пункт меню с именем файла и иконкой
                JMenuItem menuItem = new JMenuItem(fileName, AllIcons.General.ArrowRight);

                // Проверяем наличие файла config.ini
                VirtualFile configOldFile = parentDirectory.findChild("config.ini");
                if (configOldFile != null) {
                    // Проверяем наличие комментария о копировании в файле config.ini
                    try {
                        List<String> lines = Files.readAllLines(Paths.get(configOldFile.getPath()), StandardCharsets.UTF_8);
                        for (String line : lines) {
                            if (line.startsWith("#") && line.contains("Файл конфигурации скопирован из: ")) {
                                // Извлекаем имя файла из комментария
                                Matcher matcher = Pattern.compile("Файл конфигурации скопирован из: (.*)").matcher(line);
                                if (matcher.find()) {
                                    String copiedFromFile = matcher.group(1);
                                        if (copiedFromFile.equals(configFile.getName())) {
                                            menuItem.setIcon(AllIcons.General.InspectionsOK); // Устанавливаем иконку для файла, если найден соответствующий файл в списке
                                            break;
                                    }
                                }
                                break;
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                // Добавляем слушатель на пункт меню
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // Создаем приложение
                        Application application = ApplicationManager.getApplication();
                        // Запускаем операцию записи
                        application.runWriteAction(() -> {
                            try {
                                // Создаем файл конфигурации в родительской директории
                                VirtualFile destinationFile = currentFile.getParent().createChildData(this, "config.ini");
                                // Копируем содержимое из исходного файла конфигурации
                                destinationFile.setBinaryContent(configFile.contentsToByteArray());

                                String configName = configFile.getName();
                                String notificationMessage = "Установлен config: " + configName;
                                Notification notification = new Notification("ConfigCopy", "Успешно", notificationMessage, NotificationType.INFORMATION);
                                Notifications.Bus.notify(notification);

                                // Сохраняем параметры конфигурации
                                saveConfigParameters(project);

                                // Если флажок API_DATA установлен, то копируем соответствующий файл из папки "data_asserts"
                                if (getPrefState(PREF_API_DATA)) {
                                    VirtualFile dataAssertsDirectory = parentDirectory.findChild("data_asserts");
                                    if (dataAssertsDirectory != null && dataAssertsDirectory.isDirectory()) {
                                        VirtualFile dataFile = dataAssertsDirectory.findChild(fileName + ".py");
                                        if (dataFile != null) {
                                            try {
                                                VirtualFile destinationDataFile = currentFile.getParent().createChildData(this, "data.py");
                                                destinationDataFile.setBinaryContent(dataFile.contentsToByteArray());
                                            } catch (IOException ex) {
                                                ex.printStackTrace();
                                                notification = new Notification("ConfigCopy", "Ошибка", "Ошибка копирования файла data.py", NotificationType.ERROR);
                                                Notifications.Bus.notify(notification);
                                            }
                                        }
                                    }
                                }
                                // Проставляем комментарий в новом файле config.ini
                                String copiedFromComment = "# Файл конфигурации скопирован из: " + configFile.getName();
                                try {
                                    VirtualFile configFile = currentFile.getParent().findChild("config.ini");
                                    if (configFile != null) {
                                        String currentContent = new String(configFile.contentsToByteArray(), StandardCharsets.UTF_8);
                                        currentContent += copiedFromComment + "\n";
                                        configFile.setBinaryContent(currentContent.getBytes(StandardCharsets.UTF_8));
                                    }
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                    notification = new Notification("ConfigCopy", "Ошибка", "Ошибка записи комментария в config.ini", NotificationType.ERROR);
                                    Notifications.Bus.notify(notification);
                                }

                                // Обновляем файл config.ini для получения списка файлов с комментариями
                                currentFile.getParent().refresh(false, true);
                            } catch (IOException ex) {
                                // Выводим ошибку в консоль
                                ex.printStackTrace();
                            }
                        });
                    }
                });
                // Добавляем пункт меню в список компонентов меню
                menuComponents.add(menuItem);
            }
        }

        // Сортируем компоненты меню по имени
        menuComponents.sort(Comparator.comparing(c -> ((JMenuItem) c).getText()));

        // Создаем пункт меню "Настройки"
        JMenuItem settingsItem = new JMenuItem("Настройки", AllIcons.General.Settings);
        // Добавляем слушатель на пункт меню "Настройки"
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());

                Object[][] data = new Object[parameters.size()][5];
                String[] columnNames = {" ", "Название", "Значение", "Секция", "-"};

                int i = 0;
                for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
                    data[i][0] = entry.getValue().isActive();
                    data[i][1] = entry.getKey();
                    data[i][2] = entry.getValue().getValue();
                    data[i][3] = entry.getValue().getSection();
                    data[i][4] = AllIcons.Actions.GC;
                    i++;
                }

                DefaultTableModel model = new DefaultTableModel(data, columnNames) {
                    @Override
                    public Class<?> getColumnClass(int columnIndex) {
                        switch (columnIndex) {
                            case 0:
                                return Boolean.class;
                            case 1:
                            case 2:
                            case 3:
                            case 4:
                                return Object.class;
                            default:
                                return super.getColumnClass(columnIndex);
                        }
                    }
                };

                JTable table = new JTable(model);
                table.getColumn("-").setCellRenderer(new IconRenderer());
                table.getColumn("-").setCellEditor(new IconEditor(table, model));
                table.getColumnModel().getColumn(0).setMaxWidth(40);
                table.getColumnModel().getColumn(4).setMaxWidth(60);

                JScrollPane scrollPane = new JScrollPane(table);
                panel.add(scrollPane, BorderLayout.CENTER);

                JPanel inputPanel = new JPanel();
                inputPanel.setLayout(new GridLayout(1, 4, 5, 0));

                JTextField nameField = new JTextField();
                inputPanel.add(labeledField("Название", nameField));

                JTextField valueField = new JTextField();
                inputPanel.add(labeledField("Значение", valueField));

                JTextField sectionField = new JTextField();
                inputPanel.add(labeledField("Секция", sectionField));

                JButton addButton = new JButton("Добавить параметр");
                addButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        String name = nameField.getText();
                        String value = valueField.getText();
                        String section = sectionField.getText();
                        if (!name.isEmpty() && !value.isEmpty() && !section.isEmpty()) {
                            model.addRow(new Object[]{Boolean.TRUE, name, value, section, AllIcons.Actions.GC});
                            parameters.put(name, new Parameter(value, section, true));
                            nameField.setText("");
                            valueField.setText("");
                            sectionField.setText("");
                        }
                    }
                });
                inputPanel.add(addButton);

                // Добавляем кнопку "Специальные параметры"
                JButton specialParametersButton = new JButton("Специальные параметры", AllIcons.General.GearPlain);
                specialParametersButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JCheckBox apiDataCheckBox = new JCheckBox("API_DATA");
                        apiDataCheckBox.setSelected(getPrefState(PREF_API_DATA));
                        apiDataCheckBox.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                setPrefState(PREF_API_DATA, apiDataCheckBox.isSelected());
                            }
                        });

                        JCheckBox testFilesCheckBox = new JCheckBox("TEST_FILES");
                        testFilesCheckBox.setSelected(getPrefState(PREF_TEST_FILES));
                        testFilesCheckBox.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                setPrefState(PREF_TEST_FILES, testFilesCheckBox.isSelected());
                            }
                        });

                        JPanel checkBoxPanel = new JPanel(new GridLayout(2, 1));
                        checkBoxPanel.add(apiDataCheckBox);
                        checkBoxPanel.add(testFilesCheckBox);

                        JOptionPane.showConfirmDialog(
                                null, checkBoxPanel, "Выберите специальные параметры",
                                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE);
                    }
                });

                inputPanel.add(specialParametersButton);

                panel.add(inputPanel, BorderLayout.SOUTH);

                int result = JOptionPane.showConfirmDialog(null, panel, "Настройки",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    parameters.clear();
                    for (int j = 0; j < model.getRowCount(); j++) {
                        Boolean active = (Boolean) model.getValueAt(j, 0);
                        String name = (String) model.getValueAt(j, 1);
                        String value = (String) model.getValueAt(j, 2);
                        String section = (String) model.getValueAt(j, 3);
                        parameters.put(name, new Parameter(value, section, active));
                    }
                    Preferences preferences = Preferences.userNodeForPackage(getClass());
                    Gson gson = new Gson();
                    preferences.put(PREF_PARAMETERS, gson.toJson(parameters));
                    saveConfigParameters(project);
                }
            }
        });

        // Добавляем все компоненты в контекстное меню
        for (Component component : menuComponents) {
            popupMenu.add(component);
        }
        // Добавляем разделитель в меню
        popupMenu.addSeparator();
        // Добавляем пункт меню "Настройки" в контекстное меню
        popupMenu.add(settingsItem);

        Component component = e.getInputEvent().getComponent();
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            popupMenu.show(jComponent, 0, jComponent.getHeight());
        }
    }

    // Метод создания поля с меткой
    private Component labeledField(String label, JTextField textField) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(textField, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);

        // Получаем объект настроек
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        Gson gson = new Gson();
        String json = preferences.get(PREF_PARAMETERS, "");

        // Определяем тип данных для GSON
        Type type = new TypeToken<Map<String, Parameter>>(){}.getType();
        try {
            parameters = gson.fromJson(json, type);
        } catch (JsonSyntaxException exception) {
            parameters = new HashMap<>();
        }

        // Если карта параметров не определена, то создаем пустую карту параметров
        if (parameters == null) {
            parameters = new HashMap<>();
        }

        e.getPresentation().setEnabledAndVisible(true);
    }

    // Метод сохранения параметров конфигурации
    private void saveConfigParameters(Project project) {
        VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        if (selectedFiles.length == 0) {
            return;
        }
        VirtualFile currentFile = selectedFiles[0];

        VirtualFile parentDirectory = currentFile.getParent();
        if (parentDirectory == null) {
            return;
        }

        VirtualFile configsDirectory = parentDirectory.findChild("config");
        if (configsDirectory == null || !configsDirectory.isDirectory()) {
            return;
        }

        VirtualFile[] configFiles = configsDirectory.getChildren();
        if (configFiles.length == 0) {
            return;
        }

        Application application = ApplicationManager.getApplication();
        application.runWriteAction(() -> {
            try {
                VirtualFile existsConfigFile = currentFile.getParent().findChild("config.ini");

                if (existsConfigFile != null) {
                    VirtualFile destinationFile = currentFile.getParent().createChildData(this, "config.ini");

                    File iniFile = new File(destinationFile.getPath());
                    iniFile.createNewFile();

                    Wini ini = new Wini();
                    ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
                    ini.getConfig().setLowerCaseOption(false);

                    ini.load(iniFile);

                    for (String key : parameters.keySet()) {
                        Parameter parameter = parameters.get(key);
                        String value = parameter.getValue();
                        String section = parameter.getSection();
                        Boolean active = parameter.isActive();
                        if (active) {
                            if (!ini.containsKey(section)) {
                                ini.add(section);
                            }
                            if (!StringUtil.isEmptyOrSpaces(value)) {
                                ini.get(section).put(key, value);
                            }
                        }
                    }
                    // При выставленном TEST_FILES и копируется
                    if (getPrefState(PREF_TEST_FILES)) {
                        VirtualFile testFilesDirectory = parentDirectory.findChild("test-files");
                        if (testFilesDirectory != null && testFilesDirectory.isDirectory()) {
                            String testFilesPath = testFilesDirectory.getPath();
                            if (!ini.containsKey("custom")) {
                                ini.add("custom");
                            }
                            ini.get("custom").put("TEST_FILES", testFilesPath);
                        }
                    }

                    ini.store(iniFile);

                    if (destinationFile != null) {
                        destinationFile.refresh(false, true);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                Notification notification = new Notification("ConfigCopy", "Ошибка", "Ошибка при записи параметров в config.ini", NotificationType.ERROR);
                Notifications.Bus.notify(notification);
            }
        });
    }

    // Метод получения состояния настройки
    private boolean getPrefState(String prefName) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        return preferences.getBoolean(prefName, false);
    }

    // Метод установки состояния настройки
    private void setPrefState(String prefName, boolean state) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        preferences.putBoolean(prefName, state);
    }

    // Внутренний класс для описания параметра
    static class Parameter {
        private final String value;
        private final String section;
        private final Boolean active;

        Parameter(String value, String section, Boolean active) {
            this.value = value;
            this.section = section;
            this.active = active;
        }

        public String getValue() {
            return value;
        }

        public String getSection() {
            return section;
        }

        public Boolean isActive() {
            return active;
        }
    }

    class IconRenderer extends JButton implements TableCellRenderer {

        public IconRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(UIManager.getColor("Button.background"));
            }
            setIcon(AllIcons.Actions.GC);
            setText("");
            return this;
        }
    }

    class IconEditor extends DefaultCellEditor {
        protected JButton button;
        private String label;
        private boolean isPushed;
        private JTable table;
        private DefaultTableModel model;

        public IconEditor(JTable table, DefaultTableModel model) {
            super(new JCheckBox());
            this.table = table;
            this.model = model;
            button = new JButton();
            button.setOpaque(true);
            button.setIcon(AllIcons.Actions.GC);
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    fireEditingStopped();
                }
            });
        }

        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            if (isSelected) {
                button.setForeground(table.getSelectionForeground());
                button.setBackground(table.getSelectionBackground());
            } else {
                button.setForeground(table.getForeground());
                button.setBackground(table.getBackground());
            }
            label = "";
            button.setText(label);
            isPushed = true;
            return button;
        }

        public Object getCellEditorValue() {
            if (isPushed) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow != -1) {
                    String name = (String) model.getValueAt(selectedRow, 1);
                    parameters.remove(name);
                    model.removeRow(selectedRow);
                }
            }
            isPushed = false;
            return new String(label);
        }

        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }

        @Override
        public boolean isCellEditable(EventObject anEvent) {
            return true;
        }

        @Override
        public void cancelCellEditing() {
            isPushed = false;
            super.cancelCellEditing();
        }

        @Override
        public void addCellEditorListener(CellEditorListener l) {
            super.addCellEditorListener(l);
        }
    }
}
