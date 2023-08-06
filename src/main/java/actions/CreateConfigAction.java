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
import com.intellij.openapi.wm.WindowManager;
import org.ini4j.Wini;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
    private static final String PREF_CHECK_CONFIG = "CHECK_CONFIG";
    private static final String PREF_TOOLTIP_PARAMETER = "TOOLTIP";

    private JDialog settingsDialogOpen;
    private JDialog specialParametersDialogOpen;
    private JDialog settingsDialog = null;

    // Карта для хранения параметров
    private Map<String, Parameter> parameters;
    private Map<String, Parameter> currentParameters;

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
                JMenuItem menuItem = new JMenuItem(fileName, AllIcons.General.CopyHovered);

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
                                if (getPrefState(PREF_CHECK_CONFIG)) {
                                    String copiedFromComment = "# Файл конфигурации скопирован из: " + configFile.getName();
                                    try {
                                        VirtualFile configFile = currentFile.getParent().findChild("config.ini");
                                        if (configFile != null) {
                                            String currentContent = new String(configFile.contentsToByteArray(), StandardCharsets.UTF_8);
                                            currentContent = currentContent.replaceFirst("^\\s*", "");
                                            String newContent = copiedFromComment + "\n" + currentContent;
                                            configFile.setBinaryContent(newContent.getBytes(StandardCharsets.UTF_8));
                                        }
                                    } catch (IOException ex) {
                                        ex.printStackTrace();
                                        notification = new Notification("ConfigCopy", "Ошибка", "Ошибка записи комментария в config.ini", NotificationType.ERROR);
                                        Notifications.Bus.notify(notification);
                                    }
                                }
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
                // Создание диалога для отображения панели
                if (settingsDialogOpen == null || !settingsDialogOpen.isShowing()) {

                    Map<String, DefaultTableModel> groupModels = new HashMap<>();
                    JTabbedPane tabbedPane = new JTabbedPane();

                    // Получение групп из Preferences
                    Preferences prefs = Preferences.userNodeForPackage(this.getClass());
                    String groups = "Основные;" + prefs.get("groups", "");
                    String[] groupNames = groups.split(";");

                    // Создание вкладки для каждой группы
                    for (String groupName : groupNames) {
                        if (!groupName.trim().isEmpty()) {
                            Preferences preferences = Preferences.userNodeForPackage(getClass());
                            Gson gson = new Gson();
                            if (groupName.equals("Основные")) {
                                currentParameters = new HashMap<>(parameters);
                            } else {
                                String jsonParameters = preferences.get(groupName, "");
                                currentParameters = gson.fromJson(jsonParameters, new TypeToken<Map<String, Parameter>>() {}.getType());
                            }
                            JPanel panel = new JPanel();
                            panel.setLayout(new BorderLayout());

                            Object[][] data = new Object[currentParameters.size()][5];
                            String[] columnNames = {" ", "Название", "Значение", "Секция", "", "-"};

                            int i = 0;
                            for (Map.Entry<String, Parameter> entry : currentParameters.entrySet()) {
                                data[i][0] = entry.getValue().isActive();
                                data[i][1] = entry.getKey();
                                data[i][2] = entry.getValue().getValue();
                                data[i][3] = entry.getValue().getSection();
                                data[i][4] = AllIcons.Actions.GC;
                                i++;
                            }

                            // Создаем модель таблицы
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
                                        case 5:
                                            return Object.class;
                                        default:
                                            return super.getColumnClass(columnIndex);

                                    }
                                }

                                @Override
                                public void removeRow(int row) {
                                    if (getRowCount() > 1) {
                                        super.removeRow(row);
                                    }
                                }
                            };

                            // Добавляем пустую строку в конец модели таблицы
                            model.addRow(new Object[]{null, null, null, null, null});

                            JTable table = new JTable(model);
                            table.getTableHeader().setReorderingAllowed(false);
                            table.getColumn("-").setCellRenderer(new IconRenderer());
                            table.getColumn("-").setCellEditor(new IconEditor(table, model));
                            table.getColumnModel().getColumn(0).setMaxWidth(40);
                            table.getColumnModel().getColumn(5).setMaxWidth(60);
                            table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JTextField()) {
                                private JTextField hiddenTextField = new JTextField();
                                private CustomComboBox comboBoxEditor = new CustomComboBox();

                                @Override
                                public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                                    String cellValue = (String) value;
                                    if (cellValue.contains("%n")) {
                                        comboBoxEditor.removeAllItems();
                                        String[] items = cellValue.split("%n");
                                        for (String item : items) {
                                            comboBoxEditor.addItem(item);
                                        }
                                        comboBoxEditor.addFocusListener(new FocusAdapter() {
                                            @Override
                                            public void focusLost(FocusEvent e) {
                                                super.focusLost(e);
                                                stopCellEditing();
                                            }
                                        });
                                        editorComponent = comboBoxEditor;
                                        delegate = new EditorDelegate() {
                                            @Override
                                            public void setValue(Object value) {
                                                comboBoxEditor.setSelectedItem(value);
                                                hiddenTextField.setText((value != null) ? value.toString() : "");
                                            }

                                            @Override
                                            public Object getCellEditorValue() {
                                                String selectedValue = (String) comboBoxEditor.getSelectedItem();
                                                StringBuilder cellValue = new StringBuilder(selectedValue);
                                                for (int i = 0; i < comboBoxEditor.getItemCount(); i++) {
                                                    String item = (String) comboBoxEditor.getItemAt(i);
                                                    if (!item.equals(selectedValue)) {
                                                        cellValue.append("%n").append(item);
                                                    }
                                                }
                                                hiddenTextField.setText(cellValue.toString());
                                                return hiddenTextField.getText();
                                            }
                                        };
                                    } else {
                                        JTextField textField = new JTextField();
                                        editorComponent = textField;
                                        delegate = new EditorDelegate() {
                                            @Override
                                            public void setValue(Object value) {
                                                textField.setText((value != null) ? value.toString().split("%n")[0] : "");
                                            }

                                            @Override
                                            public Object getCellEditorValue() {
                                                return textField.getText();
                                            }
                                        };
                                    }
                                    return super.getTableCellEditorComponent(table, value, isSelected, row, column);
                                }
                            });

                            table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
                                @Override
                                public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                                    if (value instanceof String) {
                                        String cellValue = (String) value;
                                        if (cellValue.contains("%n")) {
                                            value = cellValue.split("%n")[0];
                                        }
                                    }
                                    return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                                }
                            });
                            table.getColumnModel().getColumn(2).setCellRenderer(new CustomCellRenderer());

                            // Создаем вертикальный отступ
                            Component verticalStrut = Box.createVerticalStrut(5);

                            // Устанавливаем высоту строки последней строки на 1 пиксель
                            int lastRowIndex = table.getRowCount() - 1;
                            table.setRowHeight(lastRowIndex, 1);
                            table.addFocusListener(new FocusAdapter() {
                                @Override
                                public void focusLost(FocusEvent e) {
                                    if (table.isEditing() && !(table.getEditorComponent() instanceof JComboBox || table.getEditorComponent() instanceof JButton)) {
                                        table.getCellEditor().stopCellEditing();
                                    }
                                }
                            });

                            JScrollPane scrollPane = new JScrollPane(table);
                            panel.add(scrollPane, BorderLayout.NORTH);
                            panel.add(verticalStrut, BorderLayout.CENTER);

                            JPanel inputPanel = new JPanel();
                            inputPanel.setLayout(new GridLayout(1, 4, 5, 0));

                            JTextField nameField = new JTextField();
                            inputPanel.add(labeledField("Название", nameField));

                            JTextField valueField = new JTextField();
                            inputPanel.add(labeledField("Значение", valueField));

                            JTextField sectionField = new JTextField();
                            inputPanel.add(labeledField("Секция", sectionField));

                            table.getColumnModel().getColumn(4).setCellRenderer(new EditButtonRenderer());
                            table.getColumnModel().getColumn(4).setCellEditor(new EditButtonEditor(table, model, nameField, valueField, sectionField));
                            table.getColumnModel().getColumn(4).setMaxWidth(60);
                            JButton addButton = new JButton("Добавить параметр");
                            addButton.addActionListener(new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    String name = nameField.getText();
                                    String value = valueField.getText();
                                    String section = sectionField.getText();

                                    // Обнуляем рамку всех полей перед проверкой
                                    nameField.setBorder(new JTextField().getBorder());
                                    valueField.setBorder(new JTextField().getBorder());
                                    sectionField.setBorder(new JTextField().getBorder());
                                    Color newColor = new Color(204, 71, 66, 255);
                                    if (name.isEmpty() || value.isEmpty() || section.isEmpty()) {
                                        if (name.isEmpty()) {
                                            nameField.setBorder(new GradientBorder(newColor));
                                        }
                                        if (value.isEmpty()) {
                                            valueField.setBorder(new GradientBorder(newColor));
                                        }
                                        if (section.isEmpty()) {
                                            sectionField.setBorder(new GradientBorder(newColor));
                                        }
                                    } else {
                                        if (currentParameters.containsKey(name)) {
                                            Parameter existingParam = currentParameters.get(name);
                                            if (checkStrings(existingParam.getValue(), value) && existingParam.getSection().equals(section)) {
                                                // Если значения value и section равны существующему параметру,
                                                // очищаем поля и прекращаем обработку события
                                                nameField.setText("");
                                                valueField.setText("");
                                                sectionField.setText("");
                                                return;
                                            }
                                            Object[] options = {"Подтвердить", "Отмена"};
                                            int dialogResult = JOptionPane.showOptionDialog(settingsDialog,
                                                    "Вы собираетесь изменить существующий параметр \"" + name + "\".",
                                                    "Подтвердите изменение",
                                                    JOptionPane.YES_NO_OPTION,
                                                    JOptionPane.WARNING_MESSAGE,
                                                    null,
                                                    options,
                                                    options[0]);
                                            if (dialogResult == JOptionPane.YES_OPTION) {
                                                // Ищем строку с таким же именем и удаляем её
                                                for (int i = 0; i < model.getRowCount(); i++) {
                                                    if (model.getValueAt(i, 1).equals(name)) {
                                                        model.removeRow(i);
                                                        break;
                                                    }
                                                }

                                                // Удаляем старое значение параметра из currentParameters
                                                currentParameters.remove(name);

                                                model.insertRow(0, new Object[]{Boolean.TRUE, name, value, section, AllIcons.Actions.GC});
                                                currentParameters.put(name, new Parameter(value, section, true));
                                            }
                                        } else {
                                            model.insertRow(0, new Object[]{Boolean.TRUE, name, value, section, AllIcons.Actions.GC});
                                            currentParameters.put(name, new Parameter(value, section, true));
                                        }

                                        nameField.setText("");
                                        valueField.setText("");
                                        sectionField.setText("");
                                    }
                                }
                            });

                            inputPanel.add(addButton);

                            Window parent = WindowManager.getInstance().getFrame(project);
                            settingsDialog = new JDialog(parent);
                            settingsDialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

                            // Добавляем кнопку "Специальные параметры"
                            JButton specialParametersButton = new JButton("Специальные параметры", AllIcons.General.GearPlain);
                            specialParametersButton.addActionListener(new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    if (specialParametersDialogOpen == null || !specialParametersDialogOpen.isShowing()) {
                                        JCheckBox apiDataCheckBox = new JCheckBox("API_DATA");
                                        apiDataCheckBox.setSelected(getPrefState(PREF_API_DATA));
                                        apiDataCheckBox.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                setPrefState(PREF_API_DATA, apiDataCheckBox.isSelected());
                                            }
                                        });
                                        apiDataCheckBox.setToolTipText("<html><b>API_DATA:</b> Формирование файла data.py вместе с файлом config.ini</html>");

                                        JCheckBox testFilesCheckBox = new JCheckBox("TEST_FILES");
                                        testFilesCheckBox.setSelected(getPrefState(PREF_TEST_FILES));
                                        testFilesCheckBox.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                setPrefState(PREF_TEST_FILES, testFilesCheckBox.isSelected());
                                            }
                                        });
                                        testFilesCheckBox.setToolTipText("<html><b>TEST_FILES:</b> Автоматическое определение папки test-files и проброс ее в config.ini</html>");

                                        JCheckBox checkConfigCheckBox = new JCheckBox("CHECK_CONFIG");
                                        checkConfigCheckBox.setSelected(getPrefState(PREF_CHECK_CONFIG));
                                        checkConfigCheckBox.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                setPrefState(PREF_CHECK_CONFIG, checkConfigCheckBox.isSelected());
                                            }
                                        });
                                        checkConfigCheckBox.setToolTipText("<html><b>CHECK_CONFIG:</b> Отслеживание выбранного файла конфигурции</html>");

                                        JCheckBox checkTooltipParameterBox = new JCheckBox("TOOLTIP");
                                        checkTooltipParameterBox.setSelected(getPrefState(PREF_TOOLTIP_PARAMETER, true));
                                        checkTooltipParameterBox.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                setPrefState(PREF_TOOLTIP_PARAMETER, checkTooltipParameterBox.isSelected());
                                            }
                                        });
                                        checkTooltipParameterBox.setToolTipText("<html><b>TOOLTIP:</b> Всплывающая подсказка с информацией о значении параметра в конфигах.</html>");
                                        JDialog specialParametersDialog = new JDialog(settingsDialog);

                                        JButton saveButton = new JButton("ОК");
                                        saveButton.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                setPrefState(PREF_API_DATA, apiDataCheckBox.isSelected());
                                                setPrefState(PREF_TEST_FILES, testFilesCheckBox.isSelected());
                                                setPrefState(PREF_CHECK_CONFIG, checkConfigCheckBox.isSelected());
                                                setPrefState(PREF_TOOLTIP_PARAMETER, checkTooltipParameterBox.isSelected());
                                                specialParametersDialog.dispose();
                                            }
                                        });

                                        JPanel checkBoxPanel = new JPanel(new GridBagLayout());
                                        checkBoxPanel.setBorder(new EmptyBorder(10, 10, 5, 10));
                                        GridBagConstraints gbc = new GridBagConstraints();
                                        gbc.gridwidth = GridBagConstraints.REMAINDER;
                                        gbc.fill = GridBagConstraints.HORIZONTAL;
                                        checkBoxPanel.add(apiDataCheckBox, gbc);
                                        checkBoxPanel.add(testFilesCheckBox, gbc);
                                        checkBoxPanel.add(checkConfigCheckBox, gbc);
                                        checkBoxPanel.add(checkTooltipParameterBox, gbc);

                                        // Добавляем раздел "Группы"
                                        JPanel groupsPanel = new JPanel(new GridBagLayout());
                                        GridBagConstraints groupsGbc = new GridBagConstraints();
                                        groupsGbc.gridwidth = GridBagConstraints.REMAINDER;
                                        groupsGbc.fill = GridBagConstraints.HORIZONTAL;

                                        // Создаем общую панель для групп с рамкой
                                        JPanel groupsContainer = new JPanel(new GridBagLayout());
                                        groupsContainer.setBorder(BorderFactory.createTitledBorder("ГРУППЫ")); // Добавляем рамку с заголовком

                                        // Создаем макет для размещения компонентов внутри общей панели
                                        GridBagConstraints containerGbc = new GridBagConstraints();
                                        containerGbc.gridwidth = GridBagConstraints.REMAINDER;
                                        containerGbc.fill = GridBagConstraints.HORIZONTAL;

                                        // Добавляем панель с группами
                                        groupsContainer.add(groupsPanel, containerGbc);

                                        // Панель для ввода новой группы (inputPanel2) с полем и кнопкой
                                        JPanel inputPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
                                        JTextField newGroupField = new JTextField();
                                        newGroupField.setPreferredSize(new Dimension(150, 30));
                                        JButton acceptButton = new JButton();
                                        acceptButton.setIcon(AllIcons.Actions.MenuSaveall);
                                        acceptButton.setPreferredSize(new Dimension(30, 30));
                                        inputPanel2.add(newGroupField);
                                        inputPanel2.add(acceptButton);
                                        groupsContainer.add(inputPanel2, containerGbc); // Добавляем панель для ввода в общую панель

                                        // Добавляем слушатель к текстовому полю
                                        newGroupField.getDocument().addDocumentListener(new DocumentListener() {
                                            @Override
                                            public void insertUpdate(DocumentEvent e) {
                                                checkField();
                                            }

                                            @Override
                                            public void removeUpdate(DocumentEvent e) {
                                                checkField();
                                            }

                                            @Override
                                            public void changedUpdate(DocumentEvent e) {
                                                checkField();
                                            }

                                            private void checkField() {
                                                String groupName = newGroupField.getText().trim();

                                                // Удаляем красную рамку, если текстовое поле не пусто
                                                if (!groupName.isEmpty()) {
                                                    newGroupField.setBorder(new JTextField().getBorder());
                                                }
                                            }
                                        });

                                        int rowHeight = 28; // Высота строки для каждой группы
                                        int initialNumberOfGroups = 0; // Начальное количество групп
                                        int maxTextLength = 20; // Максимальная длина текста
                                        Color newColor = new Color(204, 71, 66, 255);

                                        // Подтягиваем группы из Preferences
                                        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
                                        String existingGroups = prefs.get("groups", "");
                                        String[] groupNames = existingGroups.split(";");

                                        for (String groupName : groupNames) {
                                            if (!groupName.trim().isEmpty()) {
                                                initialNumberOfGroups++;

                                                // Сохраняем оригинальное название для всплывающей подсказки
                                                String originalGroupName = groupName;

                                                // Обрезаем текст, если он превышает максимальную длину
                                                if (groupName.length() > maxTextLength) {
                                                    groupName = groupName.substring(0, maxTextLength - 3) + "...";
                                                }

                                                JLabel newGroupLabel = new JLabel(groupName);
                                                JButton deleteButton = new JButton(AllIcons.Actions.Cancel);
                                                deleteButton.setPreferredSize(new Dimension(15, 15)); // Устанавливаем размер кнопки
                                                deleteButton.setContentAreaFilled(false); // Убираем заливку фона
                                                deleteButton.setBorderPainted(false); // Убираем рамку
                                                deleteButton.setOpaque(false); // Делаем кнопку прозрачной

                                                // Устанавливаем всплывающую подсказку с оригинальным названием
                                                newGroupLabel.setToolTipText(originalGroupName);

                                                // Создаем панель для группы и кнопки удаления
                                                JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                                                groupPanel.add(newGroupLabel);
                                                groupPanel.add(deleteButton);

                                                // Добавляем действие для кнопки удаления
                                                deleteButton.addActionListener(new ActionListener() {
                                                    @Override
                                                    public void actionPerformed(ActionEvent e) {
                                                        // Удаляем группу из предпочтений
                                                        removePreferenceGroup(originalGroupName);
                                                        // Удаляем всю панель groupPanel
                                                        groupsPanel.remove(groupPanel);
                                                        groupsPanel.revalidate();
                                                        groupsPanel.repaint();

                                                        // Уменьшаем высоту окна
                                                        Dimension currentSize = specialParametersDialog.getSize();
                                                        specialParametersDialog.setSize(new Dimension((int) currentSize.getWidth(), (int) currentSize.getHeight() - rowHeight));
                                                    }
                                                });

                                                groupsPanel.add(groupPanel, groupsGbc);
                                            }
                                        }
                                        // Действие при нажатии на кнопку "Зеленая галка"
                                        acceptButton.addActionListener(new ActionListener() {
                                            @Override
                                            public void actionPerformed(ActionEvent e) {
                                                String groupName = newGroupField.getText().trim();
                                                if (isGroupExists(groupName)) {
                                                    newGroupField.setBorder(new GradientBorder(newColor));
                                                } else if (!groupName.trim().isEmpty()) {
                                                    // Сохраняем оригинальное название для всплывающей подсказки
                                                    String originalGroupName = groupName;

                                                    // Обрезаем текст, если он превышает максимальную длину
                                                    if (groupName.length() > maxTextLength) {
                                                        groupName = groupName.substring(0, maxTextLength - 3) + "...";
                                                    }
                                                    // Добавляем группу в предпочтения
                                                    addPreferenceGroup(originalGroupName);

                                                    JLabel newGroupLabel = new JLabel(groupName);

                                                    // Устанавливаем всплывающую подсказку с оригинальным названием
                                                    newGroupLabel.setToolTipText(originalGroupName);

                                                    JButton deleteButton = new JButton(AllIcons.Actions.Cancel);
                                                    deleteButton.setPreferredSize(new Dimension(15, 15)); // Устанавливаем размер кнопки
                                                    deleteButton.setContentAreaFilled(false); // Убираем заливку фона
                                                    deleteButton.setBorderPainted(false); // Убираем рамку
                                                    deleteButton.setOpaque(false); // Делаем кнопку прозрачной

                                                    // Создаем панель для группы и кнопки удаления
                                                    JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                                                    groupPanel.add(newGroupLabel);
                                                    groupPanel.add(deleteButton);

                                                    // Добавляем действие для кнопки удаления
                                                    deleteButton.addActionListener(new ActionListener() {
                                                        @Override
                                                        public void actionPerformed(ActionEvent e) {
                                                            // Удаляем группу из предпочтений
                                                            removePreferenceGroup(originalGroupName);
                                                            // Удаляем всю панель groupPanel
                                                            groupsPanel.remove(groupPanel);
                                                            groupsPanel.revalidate();
                                                            groupsPanel.repaint();

                                                            // Уменьшаем высоту окна
                                                            Dimension currentSize = specialParametersDialog.getSize();
                                                            specialParametersDialog.setSize(new Dimension((int) currentSize.getWidth(), (int) currentSize.getHeight() - rowHeight));
                                                        }
                                                    });

                                                    groupsPanel.add(groupPanel, groupsGbc);

                                                    // Увеличиваем высоту окна
                                                    Dimension currentSize = specialParametersDialog.getSize();
                                                    specialParametersDialog.setSize(new Dimension((int)currentSize.getWidth(), (int)currentSize.getHeight() + rowHeight));

                                                    groupsPanel.revalidate();
                                                    groupsPanel.repaint();
                                                    newGroupField.setText("");
                                                } else {
                                                    newGroupField.setBorder(new GradientBorder(newColor)); // Устанавливаем красную рамку
                                                }
                                            }
                                        });

                                        // Добавляем общую панель для групп к основному контейнеру
                                        checkBoxPanel.add(groupsContainer, gbc);
                                        checkBoxPanel.add(saveButton, gbc);

                                        // Создаем диалог для отображения панели
                                        specialParametersDialog.setPreferredSize(new Dimension(250, 250 + rowHeight * initialNumberOfGroups));
                                        specialParametersDialog.setSize(new Dimension(250, 250 + rowHeight * initialNumberOfGroups)); // Установите начальный размер
                                        specialParametersDialog.setModalityType(Dialog.ModalityType.MODELESS);
                                        specialParametersDialog.setTitle("Специальные параметры");
                                        specialParametersDialog.getContentPane().add(checkBoxPanel);
                                        specialParametersDialog.pack();

                                        // Чтобы появился в центре родительского окна
                                        specialParametersDialog.setLocationRelativeTo(settingsDialog);

                                        // Определение действия при закрытии окна
                                        specialParametersDialog.addWindowListener(new WindowAdapter() {
                                            @Override
                                            public void windowClosed(WindowEvent e) {
                                                specialParametersDialogOpen = null;
                                            }
                                        });

                                        specialParametersDialogOpen = specialParametersDialog;
                                        specialParametersDialog.setVisible(true);
                                    } else {
                                        specialParametersDialogOpen.toFront();
                                    }
                                }
                            });

                            inputPanel.add(specialParametersButton);

                            panel.add(inputPanel, BorderLayout.SOUTH);

                            // Устанавливаем немодальность
                            settingsDialog.setModalityType(Dialog.ModalityType.MODELESS);

                            // Добавление вашей основной панели в центральную часть диалогового окна
                            settingsDialog.add(panel, BorderLayout.CENTER);

                            // Получаем панель содержимого и устанавливаем границу
                            Container contentPane = settingsDialog.getContentPane();
                            ((JComponent) contentPane).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                            // Добавление вкладки
                            JPanel mainTab = new JPanel();
                            mainTab.add(panel, BorderLayout.CENTER);
                            tabbedPane.addTab(groupName, mainTab);
                            groupModels.put(groupName, model);
                        }
                    }
                    settingsDialog.add(tabbedPane, BorderLayout.CENTER);

                    // Создание панели с кнопками OK и Cancel
                    JPanel buttonPanel = new JPanel();
                    JButton okButton = new JButton("Сохранить");
                    JButton cancelButton = new JButton("Закрыть");
                    buttonPanel.add(okButton);
                    buttonPanel.add(cancelButton);

                    // Добавление панели с кнопками в нижнюю часть диалогового окна
                    settingsDialog.add(buttonPanel, BorderLayout.SOUTH);

                    // Определение поведения при нажатии кнопки OK
                    okButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            for (String groupName: groupModels.keySet()) {
                                currentParameters.clear();
                                DefaultTableModel model = groupModels.get(groupName);
                                for (int j = 0; j < model.getRowCount(); j++) {
                                    Boolean active = (Boolean) model.getValueAt(j, 0);
                                    String name = (String) model.getValueAt(j, 1);
                                    String value = (String) model.getValueAt(j, 2);
                                    String section = (String) model.getValueAt(j, 3);
                                    if (name != null & value != null & section != null) {
                                        currentParameters.put(name, new Parameter(value, section, active));
                                    }
                                }
                                Preferences preferences = Preferences.userNodeForPackage(getClass());
                                Gson gson = new Gson();
                                if (groupName.equals("Основные")) {
                                    preferences.put(PREF_PARAMETERS, gson.toJson(currentParameters));
                                    saveConfigParameters(project);
                                } else {
                                    preferences.put(groupName, gson.toJson(currentParameters));
                                }
                            }
                            settingsDialog.dispose();
                        }
                    });

                    // Определение поведения при нажатии кнопки Cancel
                    cancelButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            settingsDialog.dispose();
                        }
                    });

                    // Позиционирование диалогового окна
                    settingsDialog.pack();
                    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                    if (activeWindow != null) {
                        int x = activeWindow.getX() + (activeWindow.getWidth() - settingsDialog.getWidth()) / 2;
                        int y = activeWindow.getY() + (activeWindow.getHeight() - settingsDialog.getHeight()) / 2;
                        settingsDialog.setLocation(x, y);
                    } else {
                        settingsDialog.setLocationRelativeTo(null);
                    }

                    // Определение действия при закрытии окна
                    settingsDialog.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosed(WindowEvent e) {
                            settingsDialogOpen = null;
                        }
                    });

                    // Отображение диалогового окна
                    settingsDialog.setTitle("Настройки параметров");
                    settingsDialogOpen = settingsDialog;
                    settingsDialog.setVisible(true);
                } else {
                    settingsDialogOpen.toFront();
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

                    Preferences preferences = Preferences.userNodeForPackage(getClass());
                    Gson gson = new Gson();
                    String jsonParameters = preferences.get(PREF_PARAMETERS, "");
                    currentParameters = gson.fromJson(jsonParameters, new TypeToken<Map<String, Parameter>>() {}.getType());
                    for (String key : currentParameters.keySet()) {
                        Parameter parameter = currentParameters.get(key);
                        String value = parameter.getValue();
                        String section = parameter.getSection();
                        Boolean active = parameter.isActive();
                        if (active) {
                            if (!ini.containsKey(section)) {
                                ini.add(section);
                            }
                            if (!StringUtil.isEmptyOrSpaces(value)) {
                                // Если значение содержит "%n", выберите первый элемент после разделения
                                if (value.contains("%n")) {
                                    value = value.split("%n")[0];
                                }
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

    private boolean getPrefState(String prefName, boolean defaultValue) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        return preferences.getBoolean(prefName, defaultValue);
    }

    // Метод установки состояния настройки
    private void setPrefState(String prefName, boolean state) {
        Preferences preferences = Preferences.userNodeForPackage(getClass());
        preferences.putBoolean(prefName, state);
    }
    private void addPreferenceGroup(String groupName) {
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
        String existingGroups = prefs.get("groups", "");
        existingGroups += groupName + ";";
        prefs.put("groups", existingGroups);
    }

    private void removePreferenceGroup(String groupName) {
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
        String existingGroups = prefs.get("groups", "");
        existingGroups = existingGroups.replace(groupName + ";", "");
        prefs.put("groups", existingGroups);
    }

    private boolean isGroupExists(String groupName) {
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
        String existingGroups = prefs.get("groups", "");
        String[] groupsArray = existingGroups.split(";"); // Разделяем группы по символу ";"

        for (String group : groupsArray) {
            if (group.equals(groupName)) {
                return true; // Нашли совпадение
            }
        }

        return false; // Не нашли совпадение
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

            // Проверяем, является ли текущая строка последней пустой строкой
            if (row == table.getRowCount() - 1 && isLastEmptyRow(row)) {
                button.setEnabled(false);
            } else {
                button.setEnabled(true);
            }

            return button;
        }

        // Метод для проверки, является ли строка последней пустой строкой
        private boolean isLastEmptyRow(int row) {
            for (int i = row + 1; i < table.getRowCount(); i++) {
                for (int j = 1; j < table.getColumnCount(); j++) {
                    if (table.getValueAt(i, j) != null) {
                        return false;
                    }
                }
            }
            return true;
        }

        public Object getCellEditorValue() {
            if (isPushed) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow != -1) {
                    String name = (String) model.getValueAt(selectedRow, 1);
                    currentParameters.remove(name);
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

    class CustomCellRenderer extends DefaultTableCellRenderer {
        Icon icon = UIManager.getIcon("Table.ascendingSortIcon");

        public CustomCellRenderer() {
            super();
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof String) {
                String cellValue = (String) value;
                if (cellValue.contains("%n")) {
                    value = cellValue.split("%n")[0];
                    setIcon(icon);
                } else {
                    setIcon(null);
                }
                setText((String) value);
            }
            return this;
        }
    }

    public class CustomComboBox extends JComboBox<String> {
        private Icon collapsedIcon;
        private Icon expandedIcon;
        private Icon currentIcon;
        private int inset = 18;  // Отступ в пикселях

        public CustomComboBox() {
            super();
            collapsedIcon = UIManager.getIcon("Table.ascendingSortIcon");
            expandedIcon = UIManager.getIcon("Table.descendingSortIcon");
            currentIcon = collapsedIcon; // Установите начальную иконку

            // Установить свой UI
            setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    // Этот код отключает стрелку
                    return new JButton() {
                        @Override
                        public int getWidth() {
                            return 0;
                        }
                    };
                }

                @Override
                protected ComboPopup createPopup() {
                    CustomComboPopup customComboPopup = new CustomComboPopup(comboBox);
                    customComboPopup.getAccessibleContext().setAccessibleParent(comboBox);
                    return customComboPopup;
                }
            });

            // Добавляем отступ слева для каждого элемента
            setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    setBorder(new EmptyBorder(0, inset, 0, 0));
                    return this;
                }
            });

            // Добавить слушатель выпадающего списка
            addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    currentIcon = expandedIcon;
                    repaint();
                    SwingUtilities.invokeLater(() -> {
                        Object child = e.getSource();
                        if (child instanceof JComboBox) {
                            Object popup = ((JComboBox) child).getUI().getAccessibleChild((JComboBox) child, 0);
                            if (popup instanceof BasicComboPopup) {
                                JList list = ((BasicComboPopup) popup).getList();
                                list.setFixedCellHeight(25);
                            }
                        }
                    });
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    currentIcon = collapsedIcon;
                    repaint();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {
                    currentIcon = collapsedIcon;
                    repaint();
                }
            });
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            int iconWidth = currentIcon.getIconWidth();
            int iconHeight = currentIcon.getIconHeight();
            int y = (getHeight() - iconHeight) / 2;
            currentIcon.paintIcon(this, g, 3, y);
        }
    }

    public class CustomComboPopup extends BasicComboPopup {
        private int maxVisibleRows = 10; // Максимальное количество видимых строк

        public CustomComboPopup(JComboBox comboBox) {
            super(comboBox);
        }

        @Override
        protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
            int popupHeight = calculatePopupHeight();
            Rectangle rect = super.computePopupBounds(px, py, pw, ph);
            rect.height = popupHeight;
            return rect;
        }

        private int calculatePopupHeight() {
            ListCellRenderer<? super String> renderer = comboBox.getRenderer();
            if (renderer == null) {
                throw new IllegalArgumentException("JComboBox должен иметь установленный renderer");
            }

            int itemHeight = 25;

            int itemCount = comboBox.getItemCount();
            int visibleRows = Math.min(itemCount, maxVisibleRows);
            return visibleRows * itemHeight;
        }
    }
    class EditButtonRenderer extends JButton implements TableCellRenderer {
        public EditButtonRenderer() {
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
            setIcon(AllIcons.Actions.Edit);
            setText("");
            if (row != table.getRowCount() - 1) {
                table.setRowHeight(row, 25);
            }
            return this;
        }
    }
    class EditButtonEditor extends DefaultCellEditor {
        protected JButton button;
        private String label;
        private boolean isPushed;
        private JTable table;
        private DefaultTableModel model;
        private JTextField nameField;
        private JTextField valueField;
        private JTextField sectionField;

        public EditButtonEditor(JTable table, DefaultTableModel model, JTextField nameField, JTextField valueField, JTextField sectionField) {
            super(new JCheckBox());
            this.table = table;
            this.model = model;
            this.nameField = nameField;
            this.valueField = valueField;
            this.sectionField = sectionField;
            button = new JButton();
            button.setOpaque(true);
            button.setIcon(AllIcons.Actions.Edit);
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
            if (row != table.getRowCount() - 1) {
                table.setRowHeight(row, 25);
            }

            return button;
        }

        public Object getCellEditorValue() {
            if (isPushed) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow != -1) {
                    String name = (String) model.getValueAt(selectedRow, 1);
                    String value = (String) model.getValueAt(selectedRow, 2);
                    String section = (String) model.getValueAt(selectedRow, 3);
                    nameField.setText(name);
                    valueField.setText(value);
                    sectionField.setText(section);
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

    public class GradientBorder extends AbstractBorder {
        private final Color color;

        public GradientBorder(Color color) {
            this.color = color;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint gradientPaint = new GradientPaint(0, 0, color, 0, height, new Color(0x02A1818, true));
            g2d.setPaint(gradientPaint);

            Stroke oldStroke = g2d.getStroke();
            // Определяем толщину линии рамки
            int borderSize = 2;
            g2d.setStroke(new BasicStroke(borderSize));
            // Рисуем прямоугольник вокруг элемента
            g2d.drawRect(x + borderSize - 1, y + borderSize + 1, width - borderSize - 1, height - borderSize - 1);
            g2d.setStroke(oldStroke);
        }
    }

    private static boolean checkStrings(String str1, String str2) {
        // Находим символы, по которым нужно разделить строки
        char delimiter = '%';

        // Разделяем строки по символу-разделителю и получаем списки символов
        String[] list1 = splitString(str1, delimiter);
        String[] list2 = splitString(str2, delimiter);

        // Проверяем, что списки символов из обеих строк совпадают
        if (list1.length != list2.length) {
            return false;
        }

        for (int i = 0; i < list1.length; i++) {
            if (!list1[i].equals(list2[i])) {
                return false;
            }
        }

        return true;
    }

    private static String[] splitString(String str, char delimiter) {
        // Метод для разделения строки по заданному символу-разделителю
        return str.split(String.valueOf(delimiter));
    }
}
