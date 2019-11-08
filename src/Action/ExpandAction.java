package Action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.String;
import org.json.*;

public class ExpandAction extends AnAction {

    private AnActionEvent event;
    private Editor editor;

    @Override
    public void actionPerformed(AnActionEvent e) {
        event = e;
        editor = PlatformDataKeys.EDITOR.getData(event.getDataContext());
        run();
    }

    private int getCursorOffset() {
        return editor.getCaretModel().getOffset();
    }

    private void setCursorPosition(int offset) {
        editor.getCaretModel().moveToOffset(offset);
    }

    private String getText(int start, int end) {
        final Document document = editor.getDocument();
        TextRange range = new TextRange(start, end);

        return document.getText(range);
    }

    private int getLineBeginningOffset() {
        final int cursorOffset = getCursorOffset();
        int lineBeginningOffset = 0;

        for(int i = cursorOffset; i > 0; i--) {
            if (getText(i - 1, i).charAt(0) == '\n') {
                lineBeginningOffset = i;
                break;
            }
        }

        return lineBeginningOffset;
    }

    private String getTextFromBeginningOfLineToCursor() {
        final int cursorOffset = getCursorOffset();
        final int lineBeginningOffset = getLineBeginningOffset();

        return getText(lineBeginningOffset, cursorOffset);
    }

    private void handleJson(String json) {
        JSONObject obj = new JSONObject(json);
        String text = obj.getString("text");
        int cursorPosition = obj.getInt("cursorPosition");
        int lineBeginningOffset = getLineBeginningOffset();

        replaceText(text);
        setCursorPosition(lineBeginningOffset + cursorPosition);
    }

    private void replaceText(String text) {
        Document document = editor.getDocument();
        final int start = getLineBeginningOffset();
        final int end = getCursorOffset();
        WriteCommandAction.runWriteCommandAction(event.getProject(), () ->
                document.replaceString(start, end, text)
        );
    }

    private void run() {
        VirtualFile file = event.getData(PlatformDataKeys.VIRTUAL_FILE);
        String extension = file.getExtension();
        if (extension == null) {
            extension = "";
        }
        String text = getTextFromBeginningOfLineToCursor();
        String[] command = { "osascript", "-l", "JavaScript", "-e", "Application('TeaCode').expandAsJson('" + escapeString(text) + "', { 'extension': '" + escapeString(extension) + "' })" };
        executeCommand(command);
    }

    private String escapeString(String string) {
        string = string.replace("\\", "\\\\");
        return string.replace("\'", "\\\'");
    }

    private void executeCommand(String[] command) {
        StringBuffer output = new StringBuffer();

        try {
            Process process;
            process = Runtime.getRuntime().exec(command);
            process.waitFor();
            InputStreamReader inputStream = new InputStreamReader(process.getInputStream());
            BufferedReader reader = new BufferedReader(inputStream);

            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            String json = output.toString();

            if (json == "null") {
                return;
            }
            if (json.isEmpty()) {
                return;
            }

            handleJson(json);
        } catch (Exception e) {
            String message = "Could not run TeaCode. Please make sure it's installed. You can download the app from www.apptorium.com/teacode";
            Messages.showMessageDialog(event.getProject(), message, "Information", Messages.getInformationIcon());
        }
    }
}
