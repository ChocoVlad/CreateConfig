package actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
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
import org.ini4j.Ini;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RepVersionAction extends AnAction {
    private Process currentProcess;

    private JTextField pathLibField;
    private JTextField pathTestsField;
    private JTextField mainProductField;
    private JTextField excludeRepoField;
    private JTextField pathEnvField;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (currentProcess != null) {
            currentProcess.destroy();
        }

        Project project = e.getProject();
        if (project != null) {
            JPopupMenu popupMenu = new JPopupMenu();
            for (String stand : new String[]{"prod", "fix", "fix-old", "test", "pre-test"}) {
                JMenuItem menuItem = new JMenuItem(stand, AllIcons.Nodes.Servlet);
                menuItem.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checkout на ветки по стенду " + stand, true) {
                            @Override
                            public void run(@NotNull ProgressIndicator indicator) {
                                String command = "make checkout stand=" + stand;
                                PropertiesComponent properties = PropertiesComponent.getInstance();
                                String featureFolderPath = properties.getValue("FEATURE_FOLDER_PATH", "");
                                if (!featureFolderPath.isEmpty()) {
                                    boolean pathValid = checkPathValidity(featureFolderPath);
                                    if (pathValid) {
                                        String workingDirectory = featureFolderPath;
                                        try {
                                            Notification notification = new Notification("RepVersion", "Начало выполнения команды", "Выполняется команда: " + command, NotificationType.INFORMATION);
                                            Notifications.Bus.notify(notification);

                                            ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
                                            processBuilder.directory(new File(workingDirectory));
                                            currentProcess = processBuilder.start();

                                            InputStream errorStream = currentProcess.getErrorStream();
                                            InputStreamReader isr = new InputStreamReader(errorStream);
                                            BufferedReader br = new BufferedReader(isr);
                                            String line;
                                            StringBuilder errorMessage = new StringBuilder();
                                            while ((line = br.readLine()) != null) {
                                                errorMessage.append(line).append("\n");
                                            }
                                            br.close();
                                            isr.close();

                                            int exitCode = currentProcess.waitFor();

                                            if (exitCode == 0) {
                                                System.out.println("Команда успешно выполнена.");

                                                notification = new Notification("RepVersion", "Команда выполнена успешно", "Команда успешно выполнена.", NotificationType.INFORMATION);
                                                Notifications.Bus.notify(notification);
                                            } else {
                                                System.out.println("Команда завершилась с ошибкой.");

                                                if (errorMessage.length() > 0) {
                                                    notification = new Notification("RepVersion", "Ошибка выполнения команды", "Выполнение команды завершилось с ошибкой.\n" + "Сообщение об ошибке: " + errorMessage.toString(), NotificationType.ERROR);
                                                    Notifications.Bus.notify(notification);
                                                }
                                            }
                                        } catch (IOException | InterruptedException e) {
                                            e.printStackTrace();
                                        } finally {
                                            currentProcess = null;
                                        }
                                    } else {
                                        Notification notification = new Notification("RepVersion", "Ошибка проверки пути", "Проверка пути не удалась. Убедитесь, что папка существует и содержит файл config.", NotificationType.ERROR);
                                        Notifications.Bus.notify(notification);
                                    }
                                } else {
                                    System.out.println("FEATURE_FOLDER_PATH не указан.");
                                    Notification notification = new Notification("RepVersion", "Ошибка проверки пути", "FEATURE_FOLDER_PATH не указан.", NotificationType.ERROR);
                                    Notifications.Bus.notify(notification);
                                }
                            }
                        });
                    }
                });
                popupMenu.add(menuItem);
            }

            JMenuItem settingsItem = new JMenuItem("Настройки", AllIcons.General.Settings);
            settingsItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent actionEvent) {
                    showSettingsDialog();
                }
            });

            popupMenu.addSeparator();
            popupMenu.add(settingsItem);

            Component component = e.getInputEvent().getComponent();
            if (component instanceof JComponent) {
                JComponent jComponent = (JComponent) component;
                popupMenu.show(jComponent, 0, jComponent.getHeight());
            }
        }
    }

    private void showSettingsDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel featureFolderLabel = new JLabel("FEATURE_FOLDER_PATH:");
        JTextField featureFolderField = new JTextField(15);

        pathLibField = new JTextField(20);
        pathLibField.setEnabled(false);

        pathTestsField = new JTextField(20);
        pathTestsField.setEnabled(false);

        mainProductField = new JTextField(20);
        mainProductField.setEnabled(false);

        excludeRepoField = new JTextField(20);
        excludeRepoField.setEnabled(false);

        pathEnvField = new JTextField(20);
        pathEnvField.setEnabled(false);

        PropertiesComponent properties = PropertiesComponent.getInstance();
        String featureFolderPath = properties.getValue("FEATURE_FOLDER_PATH", "");
        featureFolderField.setText(featureFolderPath);
        if (!featureFolderPath.isEmpty()) {
            checkAndFillFields(featureFolderPath);
        }

        featureFolderField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }

            public void update() {
                String featureFolderPath = featureFolderField.getText();
                properties.setValue("FEATURE_FOLDER_PATH", featureFolderPath);
                boolean pathValid = checkPathValidity(featureFolderPath);
                if (pathValid) {
                    checkAndFillFields(featureFolderPath);
                    featureFolderField.setToolTipText(null);
                    pathLibField.setEnabled(true);
                    pathTestsField.setEnabled(true);
                    mainProductField.setEnabled(true);
                    excludeRepoField.setEnabled(true);
                    pathEnvField.setEnabled(true);
                } else {
                    pathLibField.setEnabled(false);
                    pathTestsField.setEnabled(false);
                    mainProductField.setEnabled(false);
                    excludeRepoField.setEnabled(false);
                    pathEnvField.setEnabled(false);
                    if (!featureFolderPath.isEmpty()) {
                        featureFolderField.setToolTipText("Нужно указать путь до папки install_environment");
                        ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(featureFolderField, 0, 0, 0, 0, 0, 0, false));
                    }
                }
            }
        });

        panel.add(featureFolderLabel);
        panel.add(featureFolderField, gbc);
        panel.add(Box.createVerticalStrut(20), gbc);

        panel.add(new JLabel("path_lib:"), gbc);
        panel.add(pathLibField, gbc);

        panel.add(new JLabel("path_tests:"), gbc);
        panel.add(pathTestsField, gbc);

        panel.add(new JLabel("main_product:"), gbc);
        panel.add(mainProductField, gbc);

        panel.add(new JLabel("exclude_repo:"), gbc);
        panel.add(excludeRepoField, gbc);

        panel.add(new JLabel("path_env:"), gbc);
        panel.add(pathEnvField, gbc);

        int result = JOptionPane.showConfirmDialog(null, panel, "Настройки",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String pathLib = pathLibField.getText();
            String pathTests = pathTestsField.getText();
            String mainProduct = mainProductField.getText();

            if (pathLib.isEmpty() || pathTests.isEmpty() || mainProduct.isEmpty()) {
                Notification notification = new Notification("RepVersion", "Ошибка сохранения настроек", "Параметры path_lib, path_tests, main_product являются обязательными.", NotificationType.ERROR);
                Notifications.Bus.notify(notification);
            } else {
                System.out.println("FEATURE_FOLDER_PATH: " + featureFolderField.getText());
                System.out.println("path_lib: " + pathLib);
                System.out.println("path_tests: " + pathTests);
                System.out.println("main_product: " + mainProduct);
                System.out.println("exclude_repo: " + excludeRepoField.getText());
                System.out.println("path_env: " + pathEnvField.getText());

                saveSettingsToConfig(featureFolderPath);
            }
        } else {
            System.out.println("Cancelled");
        }
    }

    private void saveSettingsToConfig(String featureFolderPath) {
        Path path = Paths.get(featureFolderPath);
        Path configPath = path.resolve("config.ini");

        try {
            Ini ini = new Ini(new File(configPath.toString()));
            Ini.Section generalSection = ini.get("general");
            if (generalSection == null) {
                generalSection = ini.add("general");
            }

            generalSection.put("path_lib", pathLibField.getText());
            generalSection.put("path_tests", pathTestsField.getText());
            generalSection.put("main_product", mainProductField.getText());

            String excludeRepo = excludeRepoField.getText();
            if (excludeRepo.isEmpty()) {
                generalSection.remove("exclude_repo");
            } else {
                generalSection.put("exclude_repo", excludeRepo);
            }

            String pathEnv = pathEnvField.getText();
            if (pathEnv.isEmpty()) {
                generalSection.remove("path_env");
            } else {
                generalSection.put("path_env", pathEnv);
            }

            ini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean checkPathValidity(String featureFolderPath) {
        Path path = Paths.get(featureFolderPath);
        Path configPath = path.resolve("config.ini");
        return Files.isDirectory(path) && Files.exists(configPath);
    }

    private void checkAndFillFields(String featureFolderPath) {
        Path path = Paths.get(featureFolderPath);
        Path configPath = path.resolve("config.ini");
        if (checkPathValidity(featureFolderPath)) {
            pathLibField.setEnabled(true);
            pathTestsField.setEnabled(true);
            mainProductField.setEnabled(true);
            excludeRepoField.setEnabled(true);
            pathEnvField.setEnabled(true);
            System.out.println("Проверка пути: " + featureFolderPath);
            try {
                Ini ini = new Ini(new File(configPath.toString()));
                pathLibField.setText(ini.get("general", "path_lib"));
                pathTestsField.setText(ini.get("general", "path_tests"));
                mainProductField.setText(ini.get("general", "main_product"));
                excludeRepoField.setText(ini.get("general", "exclude_repo"));
                pathEnvField.setText(ini.get("general", "path_env"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            Notification notification = new Notification("RepVersion", "Ошибка проверки пути", "Проверка пути не удалась. Убедитесь, что папка существует и содержит файл config.", NotificationType.ERROR);
            Notifications.Bus.notify(notification);
        }
    }
}
