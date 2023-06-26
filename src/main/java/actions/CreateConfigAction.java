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
import com.intellij.util.ui.JBUI;
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

// Определение класса CreateConfigAction
public class CreateConfigAction extends AnAction {

    // Поля для хранения введенных параметров
    private List<String> parameterNames = new ArrayList<>();
    private List<String> parameterValues = new ArrayList<>();
    private List<String> sectionNames = new ArrayList<>();

    private DefaultTableModel parameterTableModel;

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

                                    ini.get(sectionName).put(parameterName, parameterValue);
                                }

                                ini.store(iniFile);

                                if (destinationFile != null) {
                                    destinationFile.refresh(false, true);
                                }

                                String configName = configFile.getName();
                                String notificationMessage = "Установлен config: " + configName;
                                Notification notification = new Notification("ConfigCopy", "Успешно", notificationMessage, NotificationType.INFORMATION);
                                Notifications.Bus.notify(notification);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                Notification notification = new Notification("ConfigCopy", "Ошибка", "Ошибка при установке config файла", NotificationType.ERROR);
                                Notifications.Bus.notify(notification);
                            }
                        });
                    }
                });
                menuComponents.add(menuItem);
            }
        }

        menuComponents.sort(Comparator.comparing(c -> ((JMenuItem) c).getText()));

        JMenu settingsMenu = new JMenu("Настройки");
        JMenuItem settingsItem = new JMenuItem("Открыть настройки", AllIcons.General.GearPlain);
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSettingsDialog();
            }
        });
        settingsMenu.add(settingsItem);
        menuComponents.add(settingsMenu);

        for (Component component : menuComponents) {
            popupMenu.add(component);
        }

        Component component = e.getInputEvent().getComponent();
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            popupMenu.show(jComponent, 0, jComponent.getHeight());
        }
    }

    private void showSettingsDialog() {
        JDialog settingsDialog = new JDialog();
        settingsDialog.setTitle("Настройки параметров");
        settingsDialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;

        JLabel parameterNameLabel = new JLabel("Название параметра: ");
        JTextField parameterNameTextField = new JTextField();
        parameterNameTextField.setPreferredSize(new Dimension(200, 24));
        panel.add(parameterNameLabel, constraints);
        constraints.gridx++;
        panel.add(parameterNameTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        JLabel parameterValueLabel = new JLabel("Значение параметра: ");
        JTextField parameterValueTextField = new JTextField();
        parameterValueTextField.setPreferredSize(new Dimension(200, 24));
        panel.add(parameterValueLabel, constraints);
        constraints.gridx++;
        panel.add(parameterValueTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        JLabel sectionNameLabel = new JLabel("Название секции: ");
        JTextField sectionNameTextField = new JTextField();
        sectionNameTextField.setPreferredSize(new Dimension(200, 24));
        panel.add(sectionNameLabel, constraints);
        constraints.gridx++;
        panel.add(sectionNameTextField, constraints);

        if (!parameterNames.isEmpty()) {
            parameterNameTextField.setText(parameterNames.get(parameterNames.size() - 1));
            parameterValueTextField.setText(parameterValues.get(parameterValues.size() - 1));
            sectionNameTextField.setText(sectionNames.get(sectionNames.size() - 1));
        }

        JButton addButton = new JButton("Добавить параметр");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String parameterName = parameterNameTextField.getText();
                String parameterValue = parameterValueTextField.getText();
                String sectionName = sectionNameTextField.getText();

                if (StringUtil.isNotEmpty(parameterName) && StringUtil.isNotEmpty(parameterValue) && StringUtil.isNotEmpty(sectionName)) {
                    parameterNames.add(parameterName);
                    parameterValues.add(parameterValue);
                    sectionNames.add(sectionName);
                    parameterTableModel.addRow(new Object[]{parameterName, parameterValue, sectionName});
                    parameterNameTextField.setText("");
                    parameterValueTextField.setText("");
                    sectionNameTextField.setText("");
                }
            }
        });
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        panel.add(addButton, constraints);

        parameterTableModel = new DefaultTableModel(new Object[]{"Название параметра", "Значение параметра", "Название секции"}, 0);
        JTable parameterTable = new JTable(parameterTableModel);
        JScrollPane tableScrollPane = new JScrollPane(parameterTable);
        tableScrollPane.setPreferredSize(new Dimension(500, 200));

        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        panel.add(tableScrollPane, constraints);

        settingsDialog.add(panel, BorderLayout.CENTER);

        JButton saveButton = new JButton("Сохранить");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                settingsDialog.dispose();
            }
        });
        settingsDialog.add(saveButton, BorderLayout.SOUTH);

        settingsDialog.pack();
        settingsDialog.setLocationRelativeTo(null);
        settingsDialog.setModal(true);
        settingsDialog.setVisible(true);
    }
}