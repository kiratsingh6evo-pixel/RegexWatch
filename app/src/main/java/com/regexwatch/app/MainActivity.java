package com.regexwatch.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Color;

import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    LinearLayout layout;
    EditText patternInput;
    TextView status, results, filesCount, matchesCount;
    Uri folderUri;

    ExecutorService executor =
        Executors.newSingleThreadExecutor();

    int files = 0;
    int matches = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 30, 25, 30);
        layout.setBackgroundColor(Color.rgb(15, 17, 25));

        TextView title = new TextView(this);
        title.setText("REGEXWATCH");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        filesCount = addText("Files scanned: 0");
        matchesCount = addText("Matches found: 0");

        Button folderButton = new Button(this);
        folderButton.setText("SELECT FOLDER");
        layout.addView(folderButton);

        folderButton.setOnClickListener(v -> {
            Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

            intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            );

            startActivityForResult(intent, 100);
        });

        patternInput = new EditText(this);
        patternInput.setSingleLine(true);
        patternInput.setHint("Enter regex pattern");
        patternInput.setTextColor(Color.WHITE);
        layout.addView(patternInput);

        Button scanButton = new Button(this);
        scanButton.setText("START SCAN");
        layout.addView(scanButton);

        status = addText("Status: Ready");
        results = addText("Results will appear here.");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        scanButton.setOnClickListener(v -> startScan());
    }

    TextView addText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(17);
        view.setPadding(5, 18, 5, 18);
        layout.addView(view);
        return view;
    } 
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 &&
            resultCode == RESULT_OK && data != null) {

            folderUri = data.getData();

            int flags = data.getFlags() &
                Intent.FLAG_GRANT_READ_URI_PERMISSION;

            getContentResolver()
                .takePersistableUriPermission(folderUri, flags);

            status.setText("Folder selected successfully!");
        }
    }

    void startScan() {
        if (folderUri == null) {
            status.setText("Please select a folder first.");
            return;
        }

        String expression = patternInput.getText().toString();

        if (expression.isEmpty()) {
            status.setText("Enter a regex pattern.");
            return;
        }

        final Pattern regex;

        try {
            regex = Pattern.compile(expression);
        } catch (Exception e) {
            status.setText("Invalid regex pattern.");
            return;
        }

        files = 0;
        matches = 0;

        results.setText("Scanning...");
        status.setText("Status: Scanning");

        Uri selectedFolder = folderUri;

        executor.execute(() -> {
            StringBuilder output = new StringBuilder();

            scanFolder(selectedFolder, regex, output, 0);

            runOnUiThread(() -> {
                filesCount.setText("Files scanned: " + files);
                matchesCount.setText("Matches found: " + matches);
                results.setText(output.toString());
                status.setText("Status: Scan complete");
            });
        });
    }

    void scanFolder(Uri treeUri, Pattern regex,
                    StringBuilder output, int depth) {

        if (depth > 15) return;

        String documentId =
            DocumentsContract.getDocumentId(treeUri);

        Uri childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, documentId
            );

        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                },
                null, null, null)) {

            if (cursor == null) return;

            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);

                Uri childUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, id
                    );

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    scanFolder(childUri, regex, output, depth + 1);
                    continue;
                }

                String lower = name.toLowerCase(
                    java.util.Locale.ROOT
                );

                if (!(lower.endsWith(".txt") ||
                      lower.endsWith(".csv") ||
                      lower.endsWith(".json"))) {
                    continue;
                }

                try {
                    StringBuilder content = new StringBuilder();

                    try (InputStream input =
                         getContentResolver().openInputStream(childUri)) {

                        if (input == null) continue;

                        byte[] buffer = new byte[4096];
                        int read;
                        int total = 0;

                        while ((read = input.read(buffer)) != -1) {
                            total += read;

                            if (total > 2 * 1024 * 1024) {
                                content.setLength(0);
                                break;
                            }

                            content.append(new String(
                                buffer, 0, read,
                                StandardCharsets.UTF_8
                            ));
                        }
                    }

                    if (content.length() == 0) continue;

                    files++;

                    Matcher matcher = regex.matcher(content);

                    while (matcher.find()) {
                        matches++;

                        if (output.length() < 30000) {
                            output.append("\nFILE: ")
                                  .append(name)
                                  .append("\nMATCH: ")
                                  .append(matcher.group())
                                  .append("\n");
                        }
                    }

                } catch (Exception ignored) {
                    // Skip unreadable files.
                }
            }

        } catch (Exception ignored) {
            output.append("\nCould not read a folder.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}