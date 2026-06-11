package com.pryzmm.splitself.events.helper;

import com.pryzmm.splitself.SplitSelf;
import net.minecraft.text.Text;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Font;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;

public class NotepadManager {
    public static void execute(Text[] messages) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            executePowerShell(messages);
        } else {
            executeMeow(messages);
        }
    }

    private static String escapeUnicodeForPowerShell(String input) {
        StringBuilder escaped = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c > 127) {
                escaped.append(String.format("$([char]0x%04X)", (int) c));
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static void executePowerShell(Text[] messages) {
        new Thread(() -> {
            try {
                Path scriptPath = Paths.get(System.getProperty("java.io.tmpdir"), "typing_effect.ps1");

                try (OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(scriptPath.toFile()), StandardCharsets.UTF_8)) {

                    writer.write('\uFEFF');

                    writer.write("Add-Type -AssemblyName System.Windows.Forms\n");
                    writer.write("Add-Type -AssemblyName System.Drawing\n\n");

                    writer.write("$form = New-Object System.Windows.Forms.Form\n");
                    writer.write("$form.Text = 'Let me free.'\n");
                    writer.write("$form.Size = New-Object System.Drawing.Size(300, 200)\n");
                    writer.write("$form.StartPosition = 'CenterScreen'\n\n");

                    writer.write("$textBox = New-Object System.Windows.Forms.TextBox\n");
                    writer.write("$textBox.Multiline = $true\n");
                    writer.write("$textBox.ScrollBars = 'Vertical'\n");
                    writer.write("$textBox.Font = New-Object System.Drawing.Font('Consolas', 12)\n");
                    writer.write("$textBox.Dock = 'Fill'\n");
                    writer.write("$textBox.ReadOnly = $true\n");
                    writer.write("$form.Controls.Add($textBox)\n\n");

                    writer.write("$form.Show()\n");
                    writer.write("$form.Activate()\n\n");

                    writer.write("$messages = @(\n");
                    for (int i = 0; i < messages.length; i++) {
                        String messageString = messages[i].getString();
                        String escapedMessage = escapeUnicodeForPowerShell(messageString);
                        writer.write("    \"" + escapedMessage.replace("\"", "`\"").replace("`", "``") + "\"");
                        if (i < messages.length - 1) writer.write(",");
                        writer.write("\n");
                    }
                    writer.write(")\n\n");

                    writer.write("$currentText = ''\n");
                    writer.write("foreach ($message in $messages) {\n");
                    writer.write("    Start-Sleep -Milliseconds 500\n");
                    writer.write("    foreach ($char in $message.ToCharArray()) {\n");
                    writer.write("        $currentText += $char\n");
                    writer.write("        $textBox.Text = $currentText + '|'\n");
                    writer.write("        $textBox.SelectionStart = $textBox.Text.Length\n");
                    writer.write("        $textBox.ScrollToCaret()\n");
                    writer.write("        [System.Windows.Forms.Application]::DoEvents()\n");
                    writer.write("        Start-Sleep -Milliseconds (Get-Random -Minimum 0 -Maximum 250)\n");
                    writer.write("    }\n");
                    writer.write("    $textBox.Text = $currentText\n");
                    writer.write("    $currentText += \"`r`n\"\n");
                    writer.write("}\n\n");

                    writer.write("$textBox.Text = $currentText.TrimEnd()\n");
                    writer.write("while ($form.Visible) {\n");
                    writer.write("    [System.Windows.Forms.Application]::DoEvents()\n");
                    writer.write("    Start-Sleep -Milliseconds 10\n");
                    writer.write("}\n");
                }

                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe",
                        "-ExecutionPolicy", "Bypass",
                        "-Command", "$OutputEncoding = [Console]::InputEncoding = [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding; & '" + scriptPath + "'"
                );

                pb.start();

            } catch (Exception e) {
                SplitSelf.LOGGER.error("Failed to open powershell.exe", e);
            }
        }).start();
    }

    private static void executeMeow(Text[] messages) {
        SwingUtilities.invokeLater(() -> {
            try {
                JFrame frame = new JFrame("Let me free.");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);

                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

                JScrollPane scrollPane = new JScrollPane(textArea,
                        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                frame.add(scrollPane);

                frame.setVisible(true);
                frame.toFront();

                new TypingEffect(textArea, messages).start();

            } catch (Exception e) {
                SplitSelf.LOGGER.error("Failed to open notepad dialog", e);
            }
        });
    }
    
    private static class TypingEffect {
        private final JTextArea textArea;
        private final Text[] messages;
        private final Random random = new Random();
        private final StringBuilder committed = new StringBuilder();

        private int messageIndex = 0;
        private int charIndex = 0;

        TypingEffect(JTextArea textArea, Text[] messages) {
            this.textArea = textArea;
            this.messages = messages;
        }

        void start() {
            scheduleNextChar(500);
        }

        private void scheduleNextChar(int delayMs) {
            Timer timer = new Timer(delayMs, e -> typeNext());
            timer.setRepeats(false);
            timer.start();
        }

        private void typeNext() {
            if (messageIndex >= messages.length) {
                textArea.setText(committed.toString().stripTrailing());
                return;
            }

            String current = messages[messageIndex].getString();

            if (charIndex < current.length()) {
                committed.append(current.charAt(charIndex));
                charIndex++;
                textArea.setText(committed + "|");
                textArea.setCaretPosition(textArea.getDocument().getLength());
                scheduleNextChar(random.nextInt(250));
            } else {
                // finish current line add new line and go on
                textArea.setText(committed.toString());
                committed.append(System.lineSeparator());
                messageIndex++;
                charIndex = 0;
                scheduleNextChar(500);
            }
        }
    }
}
