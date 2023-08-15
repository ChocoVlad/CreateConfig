package actions;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

public class ConfigCompletionContributor extends CompletionContributor {
    public ConfigCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                new CompletionProvider<CompletionParameters>() {
                    public void addCompletions(@NotNull CompletionParameters parameters,
                                               @NotNull ProcessingContext context,
                                               @NotNull CompletionResultSet resultSet) {

                        // Получаем текущий документ и его путь
                        PsiFile file = parameters.getOriginalFile();
                        Path filePath = Paths.get(file.getVirtualFile().getPath());

                        // Определяем наличие папки config на уровне с файлом
                        Path configFolderPath = filePath.getParent().resolve("config");
                        if (!Files.isDirectory(configFolderPath)) return;

                        // Получаем ключи из .ini файлов в папке config
                        Set<String> keys = IniFileReader.readKeysFromIniFiles(configFolderPath);

                        // Получаем текущую строку, на которой находится курсор
                        Editor editor = parameters.getEditor();
                        int caretOffset = editor.getCaretModel().getOffset();
                        Document document = editor.getDocument();
                        int lineNumber = document.getLineNumber(caretOffset);

                        // Определение начала и конца строки
                        int lineStartOffset = document.getLineStartOffset(lineNumber);
                        int lineEndOffset = document.getLineEndOffset(lineNumber);

                        // Извлечение текста текущей строки
                        String currentLineText = document.getText(new TextRange(lineStartOffset, lineEndOffset));

                        String[] prefixes = {
                                "Config.",
                                "Config().",
                                "Config().get('",
                                "Config.get('",
                                "Config().get(\\\"",
                                "Config.get(\\\"",
                                "config.get('",
                                "config.get(\\\"",
                                "config."
                        };

                        boolean shouldAutocomplete = Arrays.stream(prefixes).anyMatch(currentLineText::contains);

                        if (!shouldAutocomplete) return;

                        // Добавляем ключи в автодополнение, преобразуя их в верхний регистр и устанавливая без учета регистра
                        for (String key : keys) {
                            resultSet.addElement(LookupElementBuilder.create(key.toUpperCase()).withCaseSensitivity(false));
                        }
                    }
                }
        );
    }
}
