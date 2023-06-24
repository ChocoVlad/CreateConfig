//@formatter:off
// -*- coding: utf-8 -*-
//@formatter:on

package actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
    private static final String PREF_AUTH_SERVICE_ADDRESS = "authServiceAddress";
    private static final String TEST_FILES_PARAM = "TEST_FILES";

    private String downloadDir;
    private String authServiceAddress;

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

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
                JMenuItem menuItem = new JMenuItem(configFile.getName());
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

                                // Проверяем наличие папки "test-files" в родительском каталоге файла из превью
                                VirtualFile testFilesDirectory = parentDirectory.findChild("test-files");
                                if (testFilesDirectory != null && testFilesDirectory.isDirectory()) {
                                    // Проверяем наличие раздела custom в config.ni
                                    if (!ini.containsKey("custom")) {
                                        ini.add("custom");
                                    }
                                    //Добавляем TEST_FILES с путем до файла test-files в config.ini
                                    ini.get("custom").put(TEST_FILES_PARAM, testFilesDirectory.getPath());
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

        // Создание кнопки "Настройки"
        JMenuItem settingsItem = new JMenuItem("Настройки");
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Открытие всплывающего окна для настроек
                JPanel panel = new JPanel(new GridLayout(2, 2));
                JBLabel downloadDirLabel = new JBLabel("DOWNLOAD_DIR:");
                JBTextField downloadDirField = new JBTextField(downloadDir);
                JBLabel authServiceAddressLabel = new JBLabel("AUTH_SERVICE_ADDRESS:");
                JBTextField authServiceAddressField = new JBTextField(authServiceAddress);

                panel.add(downloadDirLabel);
                panel.add(downloadDirField);
                panel.add(authServiceAddressLabel);
                panel.add(authServiceAddressField);

                int result = JOptionPane.showOptionDialog(
                        null, // Родительское окно
                        panel, // Компонент содержимого
                        "Настройки", // Заголовок окна
                        JOptionPane.OK_CANCEL_OPTION, // Тип диалога
                        JOptionPane.PLAIN_MESSAGE, // Тип сообщения
                        null, // Иконка (может быть null)
                        new String[]{"OK", "Cancel"}, // Опции диалога
                        "OK" // Опция по умолчанию
                );

                if (result == 0) {
                    String newDownloadDir = downloadDirField.getText();
                    String newAuthServiceAddress = authServiceAddressField.getText();
                    setDownloadDir(newDownloadDir); // Установка нового значения downloadDir
                    setAuthServiceAddress(newAuthServiceAddress); // Установка нового значения authServiceAddress

                    // Сохранение настроек
                    Preferences preferences = Preferences.userNodeForPackage(CreateConfigAction.class);
                    preferences.put(PREF_DOWNLOAD_DIR, newDownloadDir);
                    preferences.put(PREF_AUTH_SERVICE_ADDRESS, newAuthServiceAddress);
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

        // Загрузка сохраненных настроек
        Preferences preferences = Preferences.userNodeForPackage(CreateConfigAction.class);
        downloadDir = preferences.get(PREF_DOWNLOAD_DIR, "");
        authServiceAddress = preferences.get(PREF_AUTH_SERVICE_ADDRESS, "");
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