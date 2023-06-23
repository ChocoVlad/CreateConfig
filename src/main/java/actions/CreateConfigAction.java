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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
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

                // Подменю для кнопок "Run" и "Debug"
                JPopupMenu subMenu = new JPopupMenu();
                JMenuItem runItem = new JMenuItem("Run");
                JMenuItem debugItem = new JMenuItem("Debug");
                subMenu.add(runItem);
                subMenu.add(debugItem);

                menuItem.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (!subMenu.isVisible()) {
                            Point location = menuItem.getLocationOnScreen();
                            subMenu.setLocation(location.x + menuItem.getWidth(), location.y);
                            subMenu.setVisible(true);
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (subMenu.isVisible()) {
                            subMenu.setVisible(false);
                        }
                    }
                });

                runItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Application application = ApplicationManager.getApplication();
                        application.runWriteAction(() -> {
                            try {
                                VirtualFile destinationFile = currentFile.getParent().createChildData(this, "config.ini");
                                destinationFile.setBinaryContent(configFile.contentsToByteArray());

                                // Показ всплывающего уведомления
                                Notification notification = new Notification("ConfigCopy", "Successful", "Copy successful", NotificationType.INFORMATION);
                                Notifications.Bus.notify(notification);
                            } catch (IOException ex) {
                                ex.printStackTrace();

                                // Показ всплывающего уведомления об ошибке
                                Notification notification = new Notification("ConfigCopy", "Error", "An error occurred while copying the config file", NotificationType.ERROR);
                                Notifications.Bus.notify(notification);
                            }
                        });
                    }
                });

                debugItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        // Ваш код для запуска отладки файла
                        // ...
                    }
                });

                menuItem.add(subMenu);
                popupMenu.add(menuItem);
            }
        }

        // Отображение попап-меню
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor != null) {
            JComponent editorComponent = editor.getComponent();
            int x = editorComponent.getLocationOnScreen().x;
            int y = editorComponent.getLocationOnScreen().y + editorComponent.getHeight();
            popupMenu.show(editorComponent, x, y);
        }
    }
}