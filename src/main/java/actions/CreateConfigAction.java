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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import org.ini4j.Wini;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.*;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.prefs.Preferences;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CreateConfigAction extends AnAction {
    private static String downloadDir; // Объявляем переменную downloadDir как статическую

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

                                // Создаем раздел [custom], если он не существует
                                if (!ini.containsKey("custom")) {
                                    ini.add("custom");
                                }

                                // Добавляем параметры DOWNLOAD_DIR и HIGHLIGHT_ACTION в раздел [custom]
                                ini.get("custom").put("DOWNLOAD_DIR", getDownloadDir());
                                ini.get("custom").put("HIGHLIGHT_ACTION", "True");

                                // Сохраняем ini-файл
                                ini.store(iniFile);

                                // Показ всплывающего уведомления
                                String configName = configFile.getName();
                                String notificationMessage = "Установлен config: " + configName;
                                Notification notification = new Notification("ConfigCopy", "Успешно", notificationMessage, NotificationType.INFORMATION);
                                Notifications.Bus.notify(notification);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                Notification notification = new Notification("ConfigCopy", "Ошибка", "Ошибка при копировании файла", NotificationType.ERROR);
                                Notifications.Bus.notify(notification);
                            }
                        });
                    }
                });
                menuComponents.add(menuItem);
            }
        }

        menuComponents.sort(Comparator.comparing(c -> ((JMenuItem) c).getText()));

        popupMenu.removeAll();
        for (Component component : menuComponents) {
            popupMenu.add(component);
        }

        // Создание кнопки "Настройки"
        JMenuItem settingsItem = new JMenuItem("Настройки");
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Открытие всплывающего окна для настроек
                String newDownloadDir = Messages.showInputDialog(project, "Введите новое значение для DOWNLOAD_DIR:", "Настройки", Messages.getQuestionIcon(), getDownloadDir(), null, null, StandardCharsets.UTF_8.name());
                if (newDownloadDir != null) {
                    setDownloadDir(newDownloadDir); // Установка нового значения downloadDir
                }
            }
        });
        popupMenu.add(settingsItem);

        // Отображение попап-меню
        Component component = e.getInputEvent().getComponent();
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            popupMenu.show(jComponent, 0, jComponent.getHeight());
        }
    }

    // Добавляем геттер и сеттер для downloadDir
    public String getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }
}