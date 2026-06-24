package com.oblivionburn.nlp.engine;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeInjector {

    public interface ProgressCallback {
        void onProgress(int percent, String status);
    }

    public static void inject(Context context, Uri uri, Logic logic, ProgressCallback progress) throws Exception {
        PDFBoxResourceLoader.init(context);
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
                // pick the longest column as the text content
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
        List<String> lines = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            PDDocument document = PDDocument.load(is);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();
            String[] rawLines = text.split("\\r?\\n");
            for (String line : rawLines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
        }
        return splitIntoSentences(lines);
    }

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
}
