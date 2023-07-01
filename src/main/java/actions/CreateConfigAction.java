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
import java.util.*;
import java.util.prefs.Preferences;
import com.google.gson.JsonSyntaxException;

public class CreateConfigAction extends AnAction {

    private static final String PREF_PARAMETERS = "parameters";
    private Map<String, Parameter> parameters;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        FileDocumentManager.getInstance().saveAllDocuments();

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

        JPopupMenu popupMenu = new JPopupMenu();
        java.util.List<Component> menuComponents = new ArrayList<>();

        for (VirtualFile configFile : configFiles) {
            if (!configFile.isDirectory()) {
                String fileName = configFile.getNameWithoutExtension();
                JMenuItem menuItem = new JMenuItem(fileName, AllIcons.FileTypes.Config);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Application application = ApplicationManager.getApplication();
                        application.runWriteAction(() -> {
                            try {
                                VirtualFile destinationFile = currentFile.getParent().createChildData(this, "config.ini");
                                destinationFile.setBinaryContent(configFile.contentsToByteArray());

                                saveConfigParameters(project);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        });
                    }
                });
                menuComponents.add(menuItem);
            }
        }

        menuComponents.sort(Comparator.comparing(c -> ((JMenuItem) c).getText()));

        JMenuItem settingsItem = new JMenuItem("Настройки", AllIcons.General.GearPlain);
        settingsItem.setToolTipText("Открыть настройки");
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());

                Object[][] data = new Object[parameters.size()][5];
                String[] columnNames = {"Active", "Название параметра", "Значение параметра", "Секция параметра", "Удалить"};

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
                table.getColumn("Удалить").setCellRenderer(new IconRenderer());
                table.getColumn("Удалить").setCellEditor(new IconEditor(table, model));

                JScrollPane scrollPane = new JScrollPane(table);
                panel.add(scrollPane, BorderLayout.CENTER);

                JPanel inputPanel = new JPanel();
                inputPanel.setLayout(new GridLayout(1, 4, 5, 0)); // Меняем расположение на горизонтальное

                JTextField nameField = new JTextField();
                inputPanel.add(labeledField("Название параметра", nameField));

                JTextField valueField = new JTextField();
                inputPanel.add(labeledField("Значение параметра", valueField));

                JTextField sectionField = new JTextField();
                inputPanel.add(labeledField("Секция параметра", sectionField));

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

        for (Component component : menuComponents) {
            popupMenu.add(component);
        }
        popupMenu.addSeparator();
        popupMenu.add(settingsItem);

        Component component = e.getInputEvent().getComponent();
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            popupMenu.show(jComponent, 0, jComponent.getHeight());
        }
    }

    private Component labeledField(String label, JTextField textField) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(textField, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);

        Preferences preferences = Preferences.userNodeForPackage(getClass());
        Gson gson = new Gson();
        String json = preferences.get(PREF_PARAMETERS, "");

        Type type = new TypeToken<Map<String, Parameter>>(){}.getType();
        try {
            parameters = gson.fromJson(json, type);
        } catch (JsonSyntaxException exception) {
            parameters = new HashMap<>();
        }

        if (parameters == null) {
            parameters = new HashMap<>();
        }

        e.getPresentation().setEnabledAndVisible(true);
    }

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

                    ini.store(iniFile);

                    if (destinationFile != null) {
                        destinationFile.refresh(false, true);
                    }

                    String configName = configFiles[0].getName();
                    String notificationMessage = "Установлен config: " + configName;
                    Notification notification = new Notification("ConfigCopy", "Успешно", notificationMessage, NotificationType.INFORMATION);
                    Notifications.Bus.notify(notification);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                Notification notification = new Notification("ConfigCopy", "Ошибка", "Ошибка при установке config файла", NotificationType.ERROR);
                Notifications.Bus.notify(notification);
            }
        });
    }

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
