//@formatter:off
// -*- coding: utf-8 -*-
//@formatter:on

package actions;


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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.ini4j.Wini;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.prefs.BackingStoreException;

// Определение класса CreateConfigAction
public class CreateConfigAction extends AnAction {

    // Поля для хранения введенных параметров
    private List<String> parameterNames = new ArrayList<>();
    private List<String> parameterValues = new ArrayList<>();
    private List<String> sectionNames = new ArrayList<>();

    private DefaultTableModel parameterTableModel;
    private static final String PREFERENCES_NODE = "com.example.config.plugin";
    private Preferences preferences = Preferences.userRoot().node(PREFERENCES_NODE);

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
        List<Component> menuComponents = new ArrayList<>();

        for (VirtualFile configFile : configFiles) {
            if (!configFile.isDirectory()) {
                JMenuItem menuItem = new JMenuItem(configFile.getName(), AllIcons.FileTypes.Config);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Application application = ApplicationManager.getApplication();
                        application.runWriteAction(() -> {
                            try {
                                VirtualFile destinationFile = currentFile.getParent().createChildData(this, "config.ini");
                                destinationFile.setBinaryContent(configFile.contentsToByteArray());

                                File iniFile = new File(destinationFile.getPath());
                                iniFile.createNewFile();

                                Wini ini = new Wini();
                                ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
                                ini.getConfig().setLowerCaseOption(false);

                                ini.load(iniFile);

                                for (int i = 0; i < parameterNames.size(); i++) {
                                    String parameterName = parameterNames.get(i);
                                    String parameterValue = parameterValues.get(i);
                                    String sectionName = sectionNames.get(i);

                                    if (!ini.containsKey(sectionName)) {
                                        ini.add(sectionName);
                                    }

                                    ini.put(sectionName, parameterName, parameterValue);
                                }

                                ini.store();
                                Notifications.Bus.notify(new Notification("Config Plugin", "Обновление конфигурации", "Конфигурационный файл был обновлен", NotificationType.INFORMATION));
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                Notifications.Bus.notify(new Notification("Config Plugin", "Ошибка", "Произошла ошибка при обновлении конфигурационного файла", NotificationType.ERROR));
                            }
                        });
                    }
                });
                popupMenu.add(menuItem);
                menuComponents.add(menuItem);
            }
        }

        if (popupMenu.getComponentCount() > 0) {
            JButton settingsButton = new JButton("Настройки");
            settingsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showSettingsDialog(project);
                }
            });
            popupMenu.add(settingsButton);
            menuComponents.add(settingsButton);
        }

        Component[] components = menuComponents.toArray(new Component[0]);

        Point location = e.getInputEvent().getComponent().getLocationOnScreen();
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        popupMenu.show(e.getInputEvent().getComponent(), location.x, location.y + 20);

        SwingUtilities.invokeLater(() -> {
            for (Component component : components) {
                if (component instanceof JButton) {
                    JButton button = (JButton) component;
                    button.setRequestFocusEnabled(false);
                    button.setFocusable(false);
                    button.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                    button.setBackground(new JBLabel().getBackground());
                }
            }
        });
    }

    private void showSettingsDialog(Project project) {
        // Создание диалогового окна
        JDialog dialog = new JDialog();
        dialog.setTitle("Настройки");

        // Создание панелей и компонентов для диалогового окна
        JPanel mainPanel = new JPanel();
        JPanel tablePanel = new JPanel();
        JPanel buttonPanel = new JPanel();

        parameterTableModel = new DefaultTableModel();
        parameterTableModel.addColumn("Секция");
        parameterTableModel.addColumn("Параметр");
        parameterTableModel.addColumn("Значение");

        JTable parameterTable = new JTable(parameterTableModel);
        JScrollPane tableScrollPane = new JScrollPane(parameterTable);

        JButton addButton = new JButton("Добавить");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parameterTableModel.addRow(new Object[]{"", "", ""});
            }
        });

        JButton removeButton = new JButton("Удалить");
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int selectedRow = parameterTable.getSelectedRow();
                if (selectedRow != -1) {
                    parameterTableModel.removeRow(selectedRow);
                }
            }
        });

        JButton saveButton = new JButton("Сохранить");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveParametersToPreferences();
                dialog.dispose();
            }
        });

        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        // Установка менеджера компоновки для панелей
        mainPanel.setLayout(new BorderLayout());
        tablePanel.setLayout(new BorderLayout());
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        // Добавление компонентов на панели
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(tablePanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Загрузка параметров из preferences
        loadParametersFromPreferences();

        // Установка параметров в таблицу
        for (int i = 0; i < sectionNames.size(); i++) {
            parameterTableModel.addRow(new Object[]{sectionNames.get(i), parameterNames.get(i), parameterValues.get(i)});
        }

        // Установка диалогового окна
        dialog.setContentPane(mainPanel);
        dialog.setSize(500, 300);
        dialog.setLocationRelativeTo(null);
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    private void saveParametersToPreferences() {
        try {
            preferences.clear();

            for (int i = 0; i < parameterTableModel.getRowCount(); i++) {
                String sectionName = StringUtil.notNullize((String) parameterTableModel.getValueAt(i, 0));
                String parameterName = StringUtil.notNullize((String) parameterTableModel.getValueAt(i, 1));
                String parameterValue = StringUtil.notNullize((String) parameterTableModel.getValueAt(i, 2));

                preferences.put(sectionName + "." + parameterName, parameterValue);
            }

            preferences.flush();
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
            Notifications.Bus.notify(new Notification("Config Plugin", "Ошибка", "Произошла ошибка при сохранении параметров в настройки", NotificationType.ERROR));
        }
    }

    private void loadParametersFromPreferences() {
        parameterNames.clear();
        parameterValues.clear();
        sectionNames.clear();

        try {
            String[] keys = preferences.keys();
            for (String key : keys) {
                int dotIndex = key.indexOf('.');
                if (dotIndex != -1) {
                    String sectionName = key.substring(0, dotIndex);
                    String parameterName = key.substring(dotIndex + 1);
                    String parameterValue = preferences.get(key, "");

                    sectionNames.add(sectionName);
                    parameterNames.add(parameterName);
                    parameterValues.add(parameterValue);
                }
            }

            // Сортировка параметров по секциям и именам параметров
            Comparator<String> sectionComparator = (s1, s2) -> {
                if (s1.isEmpty() && s2.isEmpty()) {
                    return 0;
                } else if (s1.isEmpty()) {
                    return 1;
                } else if (s2.isEmpty()) {
                    return -1;
                } else {
                    return s1.compareToIgnoreCase(s2);
                }
            };

            Comparator<String> parameterComparator = (p1, p2) -> {
                if (p1.isEmpty() && p2.isEmpty()) {
                    return 0;
                } else if (p1.isEmpty()) {
                    return 1;
                } else if (p2.isEmpty()) {
                    return -1;
                } else {
                    return p1.compareToIgnoreCase(p2);
                }
            };

            sectionNames.sort(sectionComparator);
            parameterNames.sort(parameterComparator);
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
            Notifications.Bus.notify(new Notification("Config Plugin", "Ошибка", "Произошла ошибка при загрузке параметров из настроек", NotificationType.ERROR));
        }
    }
}