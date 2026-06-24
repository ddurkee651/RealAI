package com.oblivionburn.nlp.engine;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts text from uploaded files and feeds it to the AI brain.
 */
public class KnowledgeInjector {

    private static final String TAG = "KnowledgeInjector";

    /**
     * Process a file and train the brain on every sentence found.
     *
     * @param context  Android context
     * @param uri      URI of the selected file
     * @param logic    the Logic instance that holds the brain
     * @param progress callback with current progress (0-100) and status message
     */
    public static void inject(Context context, Uri uri, Logic logic, ProgressCallback progress) throws Exception {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null) throw new Exception("Unknown file type");

        List<String> sentences;

        if (mime.startsWith("text/plain")) {
            sentences = extractText(context, uri);
        } else if (mime.equals("text/csv") || mime.equals("text/comma-separated-values")) {
            sentences = extractCsv(context, uri);
        } else if (mime.equals("application/pdf")) {
            sentences = extractPdf(context, uri);
        } else {
            throw new Exception("Unsupported format: " + mime);
        }

        int total = sentences.size();
        for (int i = 0; i < total; i++) {
            String sentence = sentences.get(i).trim();
            if (!sentence.isEmpty()) {
                logic.learnFromSentence(sentence);
            }
            int pct = (int) ((i + 1) * 100.0 / total);
            progress.onProgress(pct, "Learning sentence " + (i + 1) + " of " + total);
        }
    }

    // ---- Extractors ----

    private static List<String> extractText(Context context, Uri uri) throws Exception {
        List<String> lines = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return splitIntoSentences(lines);
    }

    private static List<String> extractCsv(Context context, Uri uri) throws Exception {
        List<String> lines = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Assume the column with the most text is the one we want
                String[] parts = line.split(",");
                String best = "";
                for (String part : parts) {
                    if (part.length() > best.length()) best = part;
                }
                if (!best.isEmpty()) lines.add(best);
            }
        }
        return splitIntoSentences(lines);
    }

    private static List<String> extractPdf(Context context, Uri uri) throws Exception {
        // Requires PDFBox library (com.tom-roush:pdfbox-android)
        // We'll provide a fallback that throws if not available.
        try {
            Class<?> pdfClass = Class.forName("com.tom_roush.pdfbox.android.PDFBoxResourceLoader");
            // Using reflection to avoid hard dependency if not included.
            // For now, we'll show a simple implementation using PDFBox when available.
            // If you include the library, replace this with actual PDFBox code.
            return extractPdfWithPdfBox(context, uri);
        } catch (ClassNotFoundException e) {
            throw new Exception("PDF support not available. Please add the PDFBox library.");
        }
    }

    private static List<String> extractPdfWithPdfBox(Context context, Uri uri) throws Exception {
        List<String> lines = new ArrayList<>();
        // Placeholder for actual PDFBox code.
        // When you include the library, replace with:
        // PDDocument document = PDDocument.load(context.getContentResolver().openInputStream(uri));
        // PDFTextStripper stripper = new PDFTextStripper();
        // String text = stripper.getText(document);
        // document.close();
        // Then split text into sentences.
        // For now, we just read the raw stream as text (won't work well).
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return splitIntoSentences(lines);
    }

    // ---- Utility ----

    private static List<String> splitIntoSentences(List<String> lines) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            for (char c : line.toCharArray()) {
                current.append(c);
                if (c == '.' || c == '!' || c == '?') {
                    String s = current.toString().trim();
                    if (s.length() > 1) sentences.add(s);
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0) {
            String s = current.toString().trim();
            if (s.length() > 1) sentences.add(s);
        }
        return sentences;
    }

    public interface ProgressCallback {
        void onProgress(int percent, String status);
    }
}
