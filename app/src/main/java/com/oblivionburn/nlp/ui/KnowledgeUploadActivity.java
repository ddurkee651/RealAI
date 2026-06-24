package com.oblivionburn.nlp.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.oblivionburn.nlp.R;
import com.oblivionburn.nlp.engine.KnowledgeInjector;
import com.oblivionburn.nlp.engine.Logic;
import com.oblivionburn.nlp.engine.Util;

public class KnowledgeUploadActivity extends Activity {

    private static final int PICK_FILE_REQUEST = 2001;

    private TextView statusText;
    private ProgressBar progressBar;
    private Button pickFileButton;
    private Button trainButton;
    private Uri selectedFileUri;
    private Logic logic;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge_upload);

        statusText = findViewById(R.id.status_text);
        progressBar = findViewById(R.id.progress_bar);
        pickFileButton = findViewById(R.id.pick_file_button);
        trainButton = findViewById(R.id.train_button);

        logic = Util.getLogic();

        pickFileButton.setOnClickListener(v -> openFilePicker());
        trainButton.setOnClickListener(v -> startTraining());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/plain", "text/csv", "application/pdf"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            if (selectedFileUri != null) {
                statusText.setText("File selected: " + selectedFileUri.getLastPathSegment());
                trainButton.setEnabled(true);
            }
        }
    }

    private void startTraining() {
        if (selectedFileUri == null) return;

        pickFileButton.setEnabled(false);
        trainButton.setEnabled(false);
        progressBar.setProgress(0);

        new Thread(() -> {
            try {
                KnowledgeInjector.inject(this, selectedFileUri, logic, (percent, status) -> {
                    handler.post(() -> {
                        progressBar.setProgress(percent);
                        statusText.setText(status);
                    });
                });
                handler.post(() -> {
                    Toast.makeText(this, "Training complete!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    pickFileButton.setEnabled(true);
                    trainButton.setEnabled(true);
                });
            }
        }).start();
    }
}
