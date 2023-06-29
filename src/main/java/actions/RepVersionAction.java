package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Component;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class RepVersionAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            for (String stand : new String[]{"prod", "fix", "fix-old", "test", "pre-test"}) {
                JMenuItem menuItem = new JMenuItem(stand);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        String command = "make checkout stand=" + StringUtil.escapeChar(stand, '\'');
                        executeCommandInTerminal(project, command);
                        executeCommandInTerminal(project, "make pull");
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

    private void executeCommandInTerminal(Project project, String command) {
        try {
            Process process = Runtime.getRuntime().exec("cmd.exe /c " + command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                // Команда успешно выполнена
                String output = readProcessOutput(process);
                Messages.showMessageDialog(project, "Command executed successfully.\n\nOutput:\n" + output, "Terminal Command", Messages.getInformationIcon());
            } else {
                // Возникла ошибка при выполнении команды
                String errorOutput = readProcessOutput(process);
                Messages.showErrorDialog(project, "Command execution failed.\n\nError Output:\n" + errorOutput, "Terminal Command Error");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Messages.showErrorDialog(project, "An error occurred while executing the command:\n" + e.getMessage(), "Terminal Command Error");
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }
}