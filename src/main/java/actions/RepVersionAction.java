package actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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
            for (String stand : new String[]{"prod", "fix", "test", "pre-test"}) {
                JMenuItem menuItem = new JMenuItem(stand, AllIcons.Actions.Annotate);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Executing Command", true) {
                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {
                                String command = "make checkout stand=" + stand;
                                String workingDirectory = "C:\\Users\\TensorUser\\install_environment";
                                try {
                                    ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
                                    processBuilder.directory(new File(workingDirectory));
                                    Process process = processBuilder.start();
                                    int exitCode = process.waitFor();
                                    if (exitCode == 0) {
                                        // Команда успешно выполнена
                                        System.out.println("Команда успешно выполнена.");
                                    } else {
                                        // Команда завершилась с ошибкой
                                        System.out.println("Команда завершилась с ошибкой. Код возврата: " + exitCode);
                                    }
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
