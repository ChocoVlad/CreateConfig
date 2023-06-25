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

// Определение класса CreateConfigAction
public class CreateConfigAction extends AnAction {

    // Константы для ключей настроек
    private static final String PREF_DOWNLOAD_DIR = "downloadDir";
    private static final String PREF_AUTH_SERVICE_ADDRESS = "authServiceAddress";
    private static final String PREF_HIGHLIGHT_ACTION = "highlightAction";
    private static final String PREF_TEST_FILES_ACTION = "testfilesAction";
    private static final String TEST_FILES_PARAM = "TEST_FILES";

    // Поля для хранения настроек
    private String downloadDir;
    private String authServiceAddress;
    private boolean highlightActionEnabled;
    private boolean testfilesActionEnabled;

    @Override
    public void actionPerformed(AnActionEvent e) {
        // Получение проекта из AnActionEvent
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // Сохранение всех открытых документов
        FileDocumentManager.getInstance().saveAllDocuments();

        // Получение текущего открытого файла в превью
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

        // Создание попап-меню
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

                                // Добавление параметров в config.ini
                                File iniFile = new File(destinationFile.getPath());
                                iniFile.createNewFile();

                                Wini ini = new Wini();
                                ini.getConfig().setFileEncoding(StandardCharsets.UTF_8);
                                ini.getConfig().setLowerCaseOption(false);

                                // Загружаем ini-файл
                                ini.load(iniFile);

                                // Создаем раздел [general], если он не существует
                                if (!ini.containsKey("general")) {
                                    ini.add("general");
                                }

                                // Добавляем параметры DOWNLOAD_DIR и AUTH_SERVICE_ADDRESS в раздел [general]
                                if (!StringUtil.isEmptyOrSpaces(downloadDir)) {
                                    ini.get("general").put("DOWNLOAD_DIR", downloadDir);
                                }
                                // Добавляем параметр AUTH_SERVICE_ADDRESS только для выбранных элементов, у которых в начале названия нет слов fix, test, pre_test
                                String configName = configFile.getName();
                                if (!StringUtil.isEmptyOrSpaces(authServiceAddress) && !configName.startsWith("fix") && !configName.startsWith("test") && !configName.startsWith("pre_test")) {
                                    ini.get("general").put("AUTH_SERVICE_ADDRESS", authServiceAddress);
                                }

                                if (highlightActionEnabled) {
                                    // Создаем раздел [custom], если он не существует
                                    if (!ini.containsKey("custom")) {
                                        ini.add("custom");
                                    }
                                    ini.get("custom").put("HIGHLIGHT_ACTION", "True");
                                }

                                // Проверяем наличие папки "test-files" в родительском каталоге файла из превью
                                if (testfilesActionEnabled) {
                                    VirtualFile testFilesDirectory = parentDirectory.findChild("test-files");
                                    if (testFilesDirectory != null && testFilesDirectory.isDirectory()) {
                                        // Создаем раздел [custom], если он не существует
                                        if (!ini.containsKey("custom")) {
                                            ini.add("custom");
                                        }
                                        //Добавляем TEST_FILES с путем до файла test-files в config.ini
                                        ini.get("custom").put(TEST_FILES_PARAM, testFilesDirectory.getPath());
                                    }
                                }

                                // Сохраняем ini-файл
                                ini.store(iniFile);

                                // Обновляем файл config.ini в IDE
                                if (destinationFile != null) {
                                    destinationFile.refresh(false, true); // Обновляем файл
                                }

                                // Показ всплывающего уведомления
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

        // Создание кнопки "Настройки" с иконкой
        JMenuItem settingsItem = new JMenuItem("Настройки", AllIcons.General.GearPlain);
        settingsItem.setToolTipText("Открыть настройки");
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Открытие всплывающего окна для настроек
                JPanel panel = new JPanel(new GridBagLayout());
                GridBagConstraints constraints = new GridBagConstraints();
                constraints.gridx = 0;
                constraints.gridy = 0;
                constraints.anchor = GridBagConstraints.WEST;

                // Создание компонентов всплывающего окна
                JLabel downloadDirLabel = new JBLabel("DOWNLOAD_DIR: ");
                JBTextField downloadDirTextField = new JBTextField(downloadDir);
                downloadDirTextField.setPreferredSize(new Dimension(400, 30));

                JLabel authServiceAddressLabel = new JBLabel("AUTH_SERVICE_ADDRESS");
                JBTextField authServiceAddressTextField = new JBTextField(authServiceAddress);
                authServiceAddressTextField.setPreferredSize(new Dimension(400, 30));

                JCheckBox highlightActionCheckbox = new JCheckBox("HIGHLIGHT_ACTION");
                highlightActionCheckbox.setSelected(highlightActionEnabled);

                JCheckBox testfilesActionCheckbox = new JCheckBox("TEST_FILES");
                testfilesActionCheckbox.setSelected(testfilesActionEnabled);


                // Добавление компонентов в панель
                panel.add(downloadDirLabel, constraints);
                constraints.gridy++;
                panel.add(downloadDirTextField, constraints);
                constraints.gridy++;
                panel.add(authServiceAddressLabel, constraints);
                constraints.gridy++;
                panel.add(authServiceAddressTextField, constraints);
                constraints.gridy++;
                panel.add(highlightActionCheckbox, constraints);
                constraints.gridy++;
                panel.add(testfilesActionCheckbox, constraints);

                // Отображение всплывающего окна
                int option = JOptionPane.showOptionDialog(null, panel, "Настройки", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);

                if (option == JOptionPane.OK_OPTION) {
                    downloadDir = downloadDirTextField.getText();
                    authServiceAddress = authServiceAddressTextField.getText();
                    highlightActionEnabled = highlightActionCheckbox.isSelected();
                    testfilesActionEnabled = testfilesActionCheckbox.isSelected();

                    Preferences preferences = Preferences.userNodeForPackage(getClass());
                    preferences.put(PREF_DOWNLOAD_DIR, downloadDir);
                    preferences.put(PREF_AUTH_SERVICE_ADDRESS, authServiceAddress);
                    preferences.putBoolean(PREF_HIGHLIGHT_ACTION, highlightActionEnabled);
                    preferences.putBoolean(PREF_TEST_FILES_ACTION, testfilesActionEnabled);

                }
            }
        });

        // Добавление элементов в попап-меню
        for (Component component : menuComponents) {
            popupMenu.add(component);
        }
        popupMenu.addSeparator();
        popupMenu.add(settingsItem);

        // Отображение попап-меню
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
        authServiceAddress = preferences.get(PREF_AUTH_SERVICE_ADDRESS, "");
        highlightActionEnabled = preferences.getBoolean(PREF_HIGHLIGHT_ACTION, false);
        testfilesActionEnabled = preferences.getBoolean(PREF_TEST_FILES_ACTION, false);

        e.getPresentation().setEnabledAndVisible(true);
    }

    // Добавляем геттеры и сеттеры для downloadDir и authServiceAddress
    public String getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

    public String getAuthServiceAddress() {
        return authServiceAddress;
    }

    public void setAuthServiceAddress(String authServiceAddress) {
        this.authServiceAddress = authServiceAddress;
    }
}