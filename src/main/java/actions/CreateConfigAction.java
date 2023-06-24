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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import org.ini4j.Ini;
import org.ini4j.IniPreferences;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.prefs.Preferences;

public class CreateConfigAction extends AnAction {
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

                                Ini ini = new Ini();
                                ini.load(new FileReader(iniFile));

                                // Создаем раздел [custom], если он не существует
                                if (!ini.containsKey("custom")) {
                                    ini.add("custom");
                                }

                                // Добавляем параметры DOWNLOAD_DIR и HIGHLIGHT_ACTION в раздел [custom]
                                ini.get("custom").put("DOWNLOAD_DIR", "C:\\Download");
                                ini.get("custom").put("HIGHLIGHT_ACTION", "True");

                                FileWriter writer = new FileWriter(iniFile);
                                ini.store(writer);
                                writer.close();

                                // Показ всплывающего уведомления
                                Notification notification = new Notification("ConfigCopy", "Successful", "Copy successful", NotificationType.INFORMATION);
                                Notifications.Bus.notify(notification);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                Notification notification = new Notification("ConfigCopy", "Error", "Error copying file", NotificationType.ERROR);
                                Notifications.Bus.notify(notification);
                            }
                        });
                    }
                });
                popupMenu.add(menuItem);
            }
        }

        // Отображение попап-меню
        Component component = e.getInputEvent().getComponent();
        if (component instanceof JComponent) {
            JComponent jComponent = (JComponent) component;
            popupMenu.show(jComponent, 0, jComponent.getHeight());
        }
    }
}