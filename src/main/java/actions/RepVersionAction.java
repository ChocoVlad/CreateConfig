package actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class RepVersionAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            for (String stand : new String[]{"prod", "fix", "fix-old", "test", "pre-test"}) {
                JMenuItem menuItem = new JMenuItem(stand, AllIcons.Actions.Annotate);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checkout на ветки по стенду " + stand, true) {
                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {
                                String command = "make checkout stand=" + stand;
                                String workingDirectory = "C:\\Users\\TensorUser\\install_environment";
                                try {
                                    // Стартовое информационное окно
                                    Notification notification = new Notification("RepVersion", "Начало выполнения команды", "Выполняется команда: " + command, NotificationType.INFORMATION);
                                    Notifications.Bus.notify(notification);

                                    ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
                                    processBuilder.directory(new File(workingDirectory));
                                    Process process = processBuilder.start();
                                    int exitCode = process.waitFor();

                                    if (exitCode == 0) {
                                        // Команда успешно выполнена
                                        System.out.println("Команда успешно выполнена.");

                                        // Информационное окно при успешном выполнении
                                        notification = new Notification("RepVersion", "Команда выполнена успешно", "Команда успешно выполнена.", NotificationType.INFORMATION);
                                        Notifications.Bus.notify(notification);
                                    } else {
                                        // Команда завершилась с ошибкой
                                        System.out.println("Команда завершилась с ошибкой. Код возврата: " + exitCode);

                                        // Информационное окно при ошибке выполнения
                                        notification = new Notification("RepVersion", "Ошибка выполнения команды", "Выполнение команды завершилось с ошибкой. Код возврата: " + exitCode, NotificationType.ERROR);
                                        Notifications.Bus.notify(notification);
                                    }

                                    // Задержка в 4 секунды перед закрытием уведомления
                                    Thread.sleep(4000);
                                    notification.expire();
                                } catch (IOException | InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                });
                popupMenu.add(menuItem);
            }
            Component component = e.getInputEvent().getComponent();
            if (component instanceof JComponent) {
                JComponent jComponent = (JComponent) component;
                popupMenu.show(jComponent, 0, jComponent.getHeight());
            }
        }
    }
}
