package com.oblivionburn.nlp.engine.neural;

import java.io.*;
import java.util.*;

public class Tokenizer {

    public static final int PAD_ID = 0;
    public static final int UNK_ID = 1;
    public static final int START_ID = 2;
    public static final int END_ID = 3;

    private final Map<String, Integer> wordToId = new HashMap<>();
    private final List<String> idToWord = new ArrayList<>();

    public Tokenizer() {
        idToWord.add("<PAD>");
        idToWord.add("<UNK>");
        idToWord.add("<START>");
        idToWord.add("<END>");
        wordToId.put("<PAD>", 0);
        wordToId.put("<UNK>", 1);
        wordToId.put("<START>", 2);
        wordToId.put("<END>", 3);
    }

    public int getVocabSize() {
        return idToWord.size();
    }

    public int getId(String word) {
        Integer id = wordToId.get(word);
        return id != null ? id : UNK_ID;
    }

    public String getWord(int id) {
        if (id >= 0 && id < idToWord.size()) return idToWord.get(id);
        return "<UNK>";
    }

    public void addWord(String word) {
        if (!wordToId.containsKey(word)) {
            int id = idToWord.size();
            wordToId.put(word, id);
            idToWord.add(word);
        }
    }

    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        text = text.replaceAll("([.,!?;:()\\[\\]{}\"'])", " $1 ");
        String[] parts = text.toLowerCase().trim().split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) tokens.add(part);
        }
        return tokens;
    }

    public int[] encode(String sentence, boolean addSpecialTokens) {
        List<String> tokens = tokenize(sentence);
        for (String token : tokens) addWord(token);

        int start = addSpecialTokens ? 1 : 0;
        int end = addSpecialTokens ? 1 : 0;
        int[] ids = new int[tokens.size() + start + end];
        int pos = 0;
        if (addSpecialTokens) ids[pos++] = START_ID;
        for (String token : tokens) ids[pos++] = getId(token);
        if (addSpecialTokens) ids[pos] = END_ID;
        return ids;
    }

    public String decode(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (id == PAD_ID || id == START_ID || id == END_ID) continue;
            String word = getWord(id);
            if (word.equals("<UNK>")) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(word);
        }
        return sb.toString().trim();
    }

    public void saveVocab(File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String word : idToWord) {
                writer.write(word);
                writer.newLine();
            }
        }
    }

    public void loadVocab(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addWord(line.trim());
            }
        }
    }
}
