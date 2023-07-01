//@formatter:off
// -*- coding: utf-8 -*-
//@formatter:on

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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
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
    private DefaultTableModel tableModel;
    private JTable table;

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

                // Создание модели таблицы
                tableModel = new DefaultTableModel();
                tableModel.addColumn("Название параметра");
                tableModel.addColumn("Значение параметра");
                tableModel.addColumn("Секция параметра");
                tableModel.addColumn(" ");

                // Заполнение модели данными из параметров
                for (String key : parameters.keySet()) {
                    Parameter parameter = parameters.get(key);
                    tableModel.addRow(new Object[]{key, parameter.getValue(), parameter.getSection(), null});
                }

                // Создание таблицы и установка модели
                table = new JTable(tableModel);

                // Установка ширины столбца с иконкой удаления
                table.getColumnModel().getColumn(3).setMaxWidth(30);

                // Создание рендерера и редактора для столбца с иконкой удаления
                table.getColumnModel().getColumn(3).setCellRenderer(new DeleteButtonRenderer());
                table.getColumnModel().getColumn(3).setCellEditor(new DeleteButtonEditor());

                // Добавление таблицы в панель с прокруткой
                JScrollPane scrollPane = new JScrollPane(table);
                panel.add(scrollPane, BorderLayout.CENTER);

                // Создание панели для добавления нового параметра
                JPanel addParameterPanel = new JPanel(new FlowLayout());

                JTextField paramNameField = new JTextField();
                paramNameField.setPreferredSize(new Dimension(200, 30));
                JTextField paramValueField = new JTextField();
                paramValueField.setPreferredSize(new Dimension(200, 30));
                JTextField paramSectionField = new JTextField();
                paramSectionField.setPreferredSize(new Dimension(200, 30));

                addParameterPanel.add(new JLabel("Название параметра"));
                addParameterPanel.add(paramNameField);
                addParameterPanel.add(new JLabel("Значение параметра"));
                addParameterPanel.add(paramValueField);
                addParameterPanel.add(new JLabel("Секция параметра"));
                addParameterPanel.add(paramSectionField);

                JButton addParameterButton = new JButton("Добавить параметр");
                addParameterButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String name = paramNameField.getText();
                        String value = paramValueField.getText();
                        String section = paramSectionField.getText();
                        if (!StringUtil.isEmptyOrSpaces(name) && !StringUtil.isEmptyOrSpaces(value) && !StringUtil.isEmptyOrSpaces(section)) {
                            tableModel.addRow(new Object[]{name, value, section, null});
                            parameters.put(name, new Parameter(value, section));
                            paramNameField.setText("");
                            paramValueField.setText("");
                            paramSectionField.setText("");
                        }
                    }
                });

                addParameterPanel.add(addParameterButton);

                panel.add(addParameterPanel, BorderLayout.SOUTH);

                int option = JOptionPane.showOptionDialog(null, panel, "Настройки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
                if (option == JOptionPane.OK_OPTION) {
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
                        if (!ini.containsKey(section)) {
                            ini.add(section);
                        }
                        if (!StringUtil.isEmptyOrSpaces(value)) {
                            ini.get(section).put(key, value);
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

        Parameter(String value, String section) {
            this.value = value;
            this.section = section;
        }

        public String getValue() {
            return value;
        }

        public String getSection() {
            return section;
        }
    }

    class DeleteButtonRenderer extends DefaultTableCellRenderer {
        private final JButton deleteButton;

        public DeleteButtonRenderer() {
            deleteButton = new JButton(AllIcons.General.Remove);
            deleteButton.setBorderPainted(false);
            deleteButton.setContentAreaFilled(false);
            deleteButton.setBackground(Color.RED); // Установка красного цвета фона кнопки
            deleteButton.setOpaque(true); // Установка непрозрачности фона кнопки
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            return deleteButton;
        }
    }

    class DeleteButtonEditor extends DefaultCellEditor {
        private final JButton deleteButton;

        public DeleteButtonEditor() {
            super(new JCheckBox());
            deleteButton = new JButton(AllIcons.General.Remove);
            deleteButton.setBorderPainted(false);
            deleteButton.setContentAreaFilled(false);
            deleteButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    stopCellEditing();
                    int row = table.getSelectedRow();
                    if (row != -1) {
                        String paramName = (String) table.getValueAt(row, 0);
                        parameters.remove(paramName);
                        tableModel.removeRow(row);
                        fireEditingStopped();
                    }
                }
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            return deleteButton;
        }
    }
}
