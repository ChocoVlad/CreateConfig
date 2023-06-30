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

public class CreateConfigAction extends AnAction {

    private static final String PREF_DOWNLOAD_DIR = "downloadDir";
    private static final String PREF_HIGHLIGHT_ACTION = "highLightAction";
    private static final String PREF_HEADLESS_MODE = "headlessAction";
    private static final String PREF_API_DATA = "apiDataAction";
    private static final String PREF_AUTH_SERVICE_ADDRESS = "authServiceAction";
    private static final String PREF_TEST_FILES = "testFilesAction";

    private String downloadDir;
    private boolean highLightActionEnabled;
    private boolean headlessActionEnabled;
    private boolean apiDataActionEnabled;
    private boolean authServiceActionEnabled;
    private boolean testFilesActionEnabled;

    private String isDownloadDir() {
        return downloadDir;
    }

    private boolean isHighLightActionEnabled() {
        return highLightActionEnabled;
    }

    private boolean isHeadlessActionEnabled() {
        return headlessActionEnabled;
    }

    private boolean isAuthServiceActionEnabled() {
        return authServiceActionEnabled;
    }

    private boolean isTestFilesActionEnabled() {
        return testFilesActionEnabled;
    }

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

                                File iniFile = new File(destinationFile.getPath());
                                iniFile.createNewFile();

                                Wini ini = new Wini();
                                ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
                                ini.getConfig().setLowerCaseOption(false);

                                ini.load(iniFile);

                                if (!ini.containsKey("general")) {
                                    ini.add("general");
                                }

                                if (!StringUtil.isEmptyOrSpaces(downloadDir)) {
                                    ini.get("general").put("DOWNLOAD_DIR", downloadDir);
                                }

                                if (highLightActionEnabled) {
                                    ini.get("general").put("HIGHLIGHT_ACTION", "True");
                                }
                                if (headlessActionEnabled) {
                                    ini.get("general").put("HEADLESS_MODE", "True");
                                }
                                if (authServiceActionEnabled) {
                                    ini.get("general").put("AUTH_SERVICE_ADDRESS", "http://dev-jenkinscontrol-service.unix.tensor.ru:8787");
                                }

                                if (testFilesActionEnabled) {
                                    if (!ini.get("custom").containsKey("TEST_FILES")) {
                                        VirtualFile testFilesDirectory = parentDirectory.findChild("test-files");
                                        if (testFilesDirectory != null && testFilesDirectory.isDirectory()) {
                                            if (!ini.containsKey("custom")) {
                                                ini.add("custom");
                                            }
                                            ini.get("custom").put("TEST_FILES", testFilesDirectory.getPath());
                                        }
                                    }
                                }

                                if (apiDataActionEnabled) {
                                    VirtualFile previewDirectory = parentDirectory.findChild("data_asserts");
                                    if (previewDirectory != null && previewDirectory.isDirectory()) {
                                        VirtualFile dataFile = previewDirectory.findChild(fileName + ".py");
                                        if (dataFile != null) {
                                            VirtualFile destinationDataFile = currentFile.getParent().createChildData(this, "data.py");
                                            destinationDataFile.setBinaryContent(dataFile.contentsToByteArray());
                                        }
                                    }
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

        JMenuItem settingsItem = new JMenuItem("Настройки", AllIcons.General.GearPlain);
        settingsItem.setToolTipText("Открыть настройки");
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Создаем панель настроек
                JPanel panel = new JPanel(new GridBagLayout());
                GridBagConstraints constraints = new GridBagConstraints();
                constraints.gridx = 0;
                constraints.gridy = 0;
                constraints.anchor = GridBagConstraints.WEST;

                // Создаем компоненты для настроек
                JLabel downloadDirLabel = new JBLabel("DOWNLOAD_DIR: ");
                JBTextField downloadDirTextField = new JBTextField(downloadDir);
                downloadDirTextField.setPreferredSize(new Dimension(400, 30));

                JCheckBox highLightActionCheckbox = new JCheckBox("HIGHLIGHT_ACTION");
                highLightActionCheckbox.setSelected(highLightActionEnabled);

                JCheckBox headlessActionCheckbox = new JCheckBox("HEADLESS_MODE");
                headlessActionCheckbox.setSelected(headlessActionEnabled);

                JCheckBox apiDataActionCheckbox = new JCheckBox("API_DATA");
                apiDataActionCheckbox.setSelected(apiDataActionEnabled);

                JCheckBox authServiceActionCheckbox = new JCheckBox("AUTH_SERVICE_ADDRESS");
                authServiceActionCheckbox.setSelected(authServiceActionEnabled);

                JCheckBox testFilesActionCheckbox = new JCheckBox("TEST_FILES");
                testFilesActionCheckbox.setSelected(testFilesActionEnabled);

                // Добавляем компоненты на панель
                panel.add(downloadDirLabel, constraints);
                constraints.gridy++;
                panel.add(downloadDirTextField, constraints);
                constraints.gridy++;
                panel.add(highLightActionCheckbox, constraints);
                constraints.gridy++;
                panel.add(headlessActionCheckbox, constraints);
                constraints.gridy++;
                panel.add(authServiceActionCheckbox, constraints);
                constraints.gridy++;
                panel.add(testFilesActionCheckbox, constraints);
                constraints.gridy++;
                panel.add(apiDataActionCheckbox, constraints);

                // Отображаем диалоговое окно с настройками
                int option = JOptionPane.showOptionDialog(null, panel, "Настройки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);

                if (option == JOptionPane.OK_OPTION) {
                    // Сохраняем значения настроек
                    downloadDir = downloadDirTextField.getText();
                    highLightActionEnabled = highLightActionCheckbox.isSelected();
                    headlessActionEnabled = headlessActionCheckbox.isSelected();
                    apiDataActionEnabled = apiDataActionCheckbox.isSelected();
                    authServiceActionEnabled = authServiceActionCheckbox.isSelected();
                    testFilesActionEnabled = testFilesActionCheckbox.isSelected();

                    Preferences preferences = Preferences.userNodeForPackage(getClass());
                    preferences.put(PREF_DOWNLOAD_DIR, downloadDir);
                    preferences.putBoolean(PREF_HIGHLIGHT_ACTION, highLightActionEnabled);
                    preferences.putBoolean(PREF_HEADLESS_MODE, headlessActionEnabled);
                    preferences.putBoolean(PREF_API_DATA, apiDataActionEnabled);
                    preferences.putBoolean(PREF_AUTH_SERVICE_ADDRESS, authServiceActionEnabled);
                    preferences.putBoolean(PREF_TEST_FILES, testFilesActionEnabled);

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
        downloadDir = preferences.get(PREF_DOWNLOAD_DIR, "");
        highLightActionEnabled = preferences.getBoolean(PREF_HIGHLIGHT_ACTION, false);
        headlessActionEnabled = preferences.getBoolean(PREF_HEADLESS_MODE, false);
        apiDataActionEnabled = preferences.getBoolean(PREF_API_DATA, false);
        authServiceActionEnabled = preferences.getBoolean(PREF_AUTH_SERVICE_ADDRESS, false);
        testFilesActionEnabled = preferences.getBoolean(PREF_TEST_FILES, false);

        e.getPresentation().setEnabledAndVisible(true);
    }

    private void saveConfigParameters(Project project) {
        // Получение пути к файлу config.ini
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

        // Получение значений параметров
        String downloadDir = isDownloadDir();
        boolean highLightActionEnabled = isHighLightActionEnabled();
        boolean headlessActionEnabled = isHeadlessActionEnabled();
        boolean authServiceActionEnabled = isAuthServiceActionEnabled();
        boolean testFilesActionEnabled = isTestFilesActionEnabled();

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

                    if (!ini.containsKey("general")) {
                        ini.add("general");
                    }

                    if (!StringUtil.isEmptyOrSpaces(downloadDir)) {
                        ini.get("general").put("DOWNLOAD_DIR", downloadDir);
                    }

                    if (highLightActionEnabled) {
                        ini.get("general").put("HIGHLIGHT_ACTION", "True");
                    }
                    if (headlessActionEnabled) {
                        ini.get("general").put("HEADLESS_MODE", "True");
                    }
                    if (authServiceActionEnabled) {
                        ini.get("general").put("AUTH_SERVICE_ADDRESS", "http://dev-jenkinscontrol-service.unix.tensor.ru:8787");
                    }

                    if (testFilesActionEnabled) {
                        VirtualFile testFilesDirectory = parentDirectory.findChild("test-files");
                        if (testFilesDirectory != null && testFilesDirectory.isDirectory()) {
                            if (!ini.containsKey("custom")) {
                                ini.add("custom");
                            }
                            ini.get("custom").put("TEST_FILES", testFilesDirectory.getPath());
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
}