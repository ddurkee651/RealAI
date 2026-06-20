package com.oblivionburn.nlp;

import android.content.Context;
import android.util.TypedValue;
import android.view.MenuItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class – all static helpers.
 * <p>
 * Refactored for:
 * - Memory functions (extract facts, query facts)
 * - Fixed CleanMemory (was decompiled incorrectly)
 * - Simplified Encourage/Discourage
 * - Now accepts a Logic instance for state changes (Advanced, Speech, etc.)
 * - Removed all direct static references to Logic
 */
public final class Util {

    private static final String EMPTY = "";

    // ---- Memory (fact) extraction ----

    /**
     * Tries to extract a fact from user input.
     * Returns a string in the format "key|value", or null if none found.
     * Example: "My name is Alice" → "user_name|Alice"
     */
    public static String extractFact(String input) {
        if (input == null || input.isEmpty()) return null;

        // Name: "my name is X", "i am called X", "call me X"
        Pattern namePattern = Pattern.compile("(?:my name is|i am called|call me)\\s+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher nameMatcher = namePattern.matcher(input);
        if (nameMatcher.find()) {
            return "user_name|" + nameMatcher.group(1).trim();
        }

        // Location: "i live in X", "i am from X", "from X"
        Pattern locPattern = Pattern.compile("(?:i (?:live|am) in|i am from|from)\\s+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher locMatcher = locPattern.matcher(input);
        if (locMatcher.find()) {
            return "user_location|" + locMatcher.group(1).trim();
        }

        // Age: "i am X years old", "i'm X"
        Pattern agePattern = Pattern.compile("(?:i am|i'm)\\s+(\\d+)\\s*(?:years? old)?", Pattern.CASE_INSENSITIVE);
        Matcher ageMatcher = agePattern.matcher(input);
        if (ageMatcher.find()) {
            return "user_age|" + ageMatcher.group(1);
        }

        // Favourite thing: "i like X", "my favorite X is Y" (catch‑all)
        Pattern likePattern = Pattern.compile("(?:i like|i love|my favorite)\\s+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher likeMatcher = likePattern.matcher(input);
        if (likeMatcher.find()) {
            String thing = likeMatcher.group(1).trim();
            if (!thing.isEmpty()) {
                return "user_likes|" + thing;
            }
        }
        return null;
    }

    /**
     * Checks if the user is asking about a stored fact.
     * Returns the key being asked, or null.
     * Example: "What is my name?" → "user_name"
     */
    public static String detectFactQuery(String input) {
        if (input == null) return null;
        String lower = input.toLowerCase();

        if (lower.matches(".*what (?:is|are) my name.*") ||
                lower.matches(".*do you know my name.*") ||
                lower.matches(".*remember my name.*")) {
            return "user_name";
        }
        if (lower.matches(".*where (?:do|am) i (?:live|from).*") ||
                lower.matches(".*where am i from.*")) {
            return "user_location";
        }
        if (lower.matches(".*how old (?:am i|am i).*") ||
                lower.matches(".*what (?:is|are) my age.*")) {
            return "user_age";
        }
        if (lower.matches(".*what (?:do|am) i like.*") ||
                lower.matches(".*what (?:is|are) my favorite.*")) {
            return "user_likes";
        }
        return null;
    }

    /**
     * Builds a natural response from a fact value.
     * Example: key="user_name", value="Alice" → "Your name is Alice."
     */
    public static String buildFactResponse(String key, String value) {
        if (key == null || value == null) return null;
        switch (key) {
            case "user_name":     return "Your name is " + value + ".";
            case "user_location": return "You live in " + value + ".";
            case "user_age":      return "You are " + value + " years old.";
            case "user_likes":    return "You like " + value + ".";
            default:              return "I remember that " + key.replace("_", " ") + " is " + value + ".";
        }
    }

    // ---- Clean up orphaned files (fixed from decompiled broken code) ----

    /**
     * Removes empty output files and entries from InputList that no longer have files.
     * Runs in a background thread to avoid blocking UI.
     */
    public static void CleanMemory(final Context context) {
        new Thread(() -> {
            File brainDir = context.getExternalFilesDir(null);
            if (brainDir == null) return;

            // 1. Clean InputList – remove entries whose output file is empty or missing
            List<String> inputList = Data.getInputList();
            List<String> cleaned = new ArrayList<>();
            for (String phrase : inputList) {
                File outputFile = new File(brainDir, phrase + ".txt");
                if (outputFile.exists()) {
                    List<String> outputs = Data.getAllOutputs(phrase);
                    // Keep only if there is at least one non‑empty output that is not a single topic marker
                    boolean keep = false;
                    for (String out : outputs) {
                        if (!out.isEmpty() && !out.matches("#.*~\\d+")) { // not just a topic marker
                            keep = true;
                            break;
                        }
                    }
                    if (keep) {
                        cleaned.add(phrase);
                    } else {
                        outputFile.delete(); // empty or useless – delete file
                    }
                }
                // if file doesn't exist, we simply drop the phrase (do not add to cleaned)
            }
            Data.saveInputList(cleaned);

            // 2. Delete any orphaned .txt files that are not in InputList
            File[] files = brainDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    // skip config and special files
                    if (name.equals("Words.txt") || name.equals("InputList.txt") ||
                            name.equals("Config.ini") || name.equals("Memory.txt") ||
                            name.startsWith("Pre-") || name.startsWith("Pro-")) {
                        continue;
                    }
                    // remove extension
                    String base = name.substring(0, name.lastIndexOf('.'));
                    if (!cleaned.contains(base)) {
                        f.delete();
                    }
                }
            }
        }).start();
    }

    /**
     * Deletes leftover temporary files (".txt", ",.txt", "..txt") from the app's files dir.
     */
    public static void ClearLeftovers(Context context) {
        File dir = context.getFilesDir();
        if (dir == null) return;
        for (String name : new String[]{".txt", ",.txt", "..txt"}) {
            File f = new File(dir, name);
            if (f.exists()) f.delete();
        }
    }

    // ---- Encourage / Discourage (reinforcement) ----

    public static void Encourage() {
        if (logicRef == null) return;
        String last = logicRef.getLastResponse();
        if (last == null || last.isEmpty()) return;
        last = PunctuationFix_ForInput(last);
        String[] tokens = last.split(" ");
        modifyAdjacency(tokens, 1);
    }

    public static void Discourage() {
        if (logicRef == null) return;
        String last = logicRef.getLastResponse();
        if (last == null || last.isEmpty()) return;
        last = PunctuationFix_ForInput(last);
        String[] tokens = last.split(" ");
        modifyAdjacency(tokens, -1);
    }

    private static void modifyAdjacency(String[] tokens, int delta) {
        if (tokens == null || tokens.length == 0) return;

        // Update Pro‑words (forward links)
        for (int i = 0; i < tokens.length - 1; i++) {
            String current = tokens[i];
            String next = tokens[i + 1];
            List<WordData> proList = Data.getProWords(current);
            boolean found = false;
            for (WordData wd : proList) {
                if (wd.getWord().equals(next)) {
                    int freq = wd.getFrequency() + delta;
                    if (freq < 0) freq = 0;
                    wd.setFrequency(freq);
                    found = true;
                    break;
                }
            }
            if (!found && delta > 0) {
                WordData newWd = new WordData();
                newWd.setWord(next);
                newWd.setFrequency(1);
                proList.add(newWd);
            }
            Data.saveProWords(proList, current);
        }

        // Update Pre‑words (backward links)
        for (int i = 1; i < tokens.length; i++) {
            String current = tokens[i];
            String prev = tokens[i - 1];
            List<WordData> preList = Data.getPreWords(current);
            boolean found = false;
            for (WordData wd : preList) {
                if (wd.getWord().equals(prev)) {
                    int freq = wd.getFrequency() + delta;
                    if (freq < 0) freq = 0;
                    wd.setFrequency(freq);
                    found = true;
                    break;
                }
            }
            if (!found && delta > 0) {
                WordData newWd = new WordData();
                newWd.setWord(prev);
                newWd.setFrequency(1);
                preList.add(newWd);
            }
            Data.savePreWords(preList, current);
        }
    }

    // ---- Erase memory (recursive delete) ----

    /**
     * Recursively deletes all files in a directory, except Config.ini.
     */
    public static void EraseMemory(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                EraseMemory(child);
            }
        }
        // Do not delete Config.ini
        if (dir.getName().contains("Config")) {
            return;
        }
        dir.delete();
    }

    // ---- Toggle options (now accept Logic instance) ----

    public static void ToggleAdvanced(MenuItem menuItem, Logic logic) {
        boolean newVal = !logic.isAdvanced();
        logic.setAdvanced(newVal);
        menuItem.setTitle("Advanced Mode: " + newVal);
        saveConfig(logic);
    }

    public static void ToggleSpeech(MenuItem menuItem, Logic logic) {
        boolean newVal = !logic.isSpeech();
        logic.setSpeech(newVal);
        menuItem.setTitle("Speech: " + newVal);
        saveConfig(logic);
    }

    private static void saveConfig(Logic logic) {
        String delay = MainActivity.bl_DelayForever ? "Infinite" : (MainActivity.int_Time / 1000) + " seconds";
        Data.setConfig(
                delay,
                String.valueOf(logic.isAdvanced()),
                String.valueOf(logic.isTopicBased()),
                String.valueOf(logic.isConditionBased()),
                String.valueOf(logic.isProceduralBased()),
                String.valueOf(logic.isSpeech())
        );
    }

    // ---- UI helpers ----

    public static float dpToPx(Context context) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200.0f,
                context.getResources().getDisplayMetrics());
    }

    // ---- Statistical helpers (unchanged but cleaned) ----

    private static int GetMin(List<Integer> list) {
        if (list.isEmpty()) return 0;
        int min = Integer.MAX_VALUE;
        for (int v : list) if (v < min) min = v;
        return min;
    }

    private static int GetMax(List<Integer> list) {
        if (list.isEmpty()) return 0;
        int max = 0;
        for (int v : list) if (v > max) max = v;
        return max;
    }

    public static int Choose(List<Integer> weights) {
        if (weights.isEmpty()) return 0;
        int max = GetMax(weights);
        Random rnd = new Random();
        int target = rnd.nextInt(max);
        for (int w : weights) {
            if (w >= target) return w;
        }
        return weights.get(weights.size() - 1);
    }

    public static boolean tryParseInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String PunctuationFix_ForInput(String str) {
        return str;
    }

    // ---- Topic and phrase related (simplified) ----

    public static List<String> Get_TopicRelated(List<String> topics) {
        List<String> result = new ArrayList<>();
        if (topics.isEmpty()) return result;

        List<String> inputList = Data.getInputList();
        if (inputList.isEmpty()) return result;

        // Find input phrases that contain all topics
        List<String> candidatePhrases = new ArrayList<>();
        for (String phrase : inputList) {
            List<String> phraseTopics = Data.getTopics(phrase);
            int matchCount = 0;
            for (String t : topics) {
                if (phraseTopics.contains(t)) matchCount++;
            }
            if (matchCount >= topics.size()) {
                candidatePhrases.add(phrase);
            }
        }

        if (candidatePhrases.isEmpty()) return result;

        // Build frequency map for each topic across candidates
        List<Integer> topicFreqs = new ArrayList<>();
        for (String t : topics) {
            int total = 0;
            for (String phrase : candidatePhrases) {
                List<String> outputs = Data.getOutputList_OnlyTopics(phrase);
                for (String out : outputs) {
                    if (out.startsWith("#" + t + "~")) {
                        String[] parts = out.split("~");
                        if (parts.length == 2) {
                            total += Integer.parseInt(parts[1]);
                        }
                    }
                }
            }
            topicFreqs.add(total);
        }

        int maxFreq = GetMax(topicFreqs);
        if (maxFreq == 0) return result;

        // Pick the first candidate whose topic has max frequency, and return its non‑topic outputs
        for (String phrase : candidatePhrases) {
            List<String> outputs = Data.getOutputList_OnlyTopics(phrase);
            for (String out : outputs) {
                String[] parts = out.split("~");
                if (parts.length == 2) {
                    for (int i = 0; i < topics.size(); i++) {
                        if (parts[0].equals("#" + topics.get(i)) && topicFreqs.get(i) == maxFreq) {
                            // return the non‑topic outputs of this phrase
                            result.addAll(Data.getOutputList_NoRelated(phrase));
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    public static List<String> Get_PhraseRelated(String phrase) {
        List<String> related = new ArrayList<>();
        for (String input : Data.getInputList()) {
            List<String> outputs = Data.getOutputList_NoRelated(input);
            for (String out : outputs) {
                if (out.equals(phrase)) {
                    related.addAll(Data.getRelatedOutputs(input, phrase));
                }
            }
        }
        return related;
    }

    // ---- Frequency based word selection ----

    private static List<String> Get_LowestFrequencies(String[] tokens) {
        List<String> result = new ArrayList<>();
        if (tokens == null || tokens.length == 0) return result;

        List<WordData> allWords = Data.getWords();
        List<Integer> freqs = new ArrayList<>();
        for (String t : tokens) {
            for (WordData wd : allWords) {
                if (wd.getWord().equals(t)) {
                    freqs.add(wd.getFrequency());
                    break;
                }
            }
        }
        if (freqs.isEmpty()) return result;

        int min = GetMin(freqs);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < freqs.size(); i++) {
            if (freqs.get(i) == min) indices.add(i);
        }
        for (int idx : indices) {
            String word = tokens[idx].toLowerCase();
            // skip punctuation
            if (word.equals(" .") || word.equals(" $") || word.equals(" !") || word.equals(" ,")) continue;
            if (!result.contains(word)) result.add(word);
        }
        return result;
    }

    public static String Get_RandomWord() {
        List<WordData> words = Data.getWords();
        if (words.isEmpty()) return EMPTY;
        Random rnd = new Random();
        for (int i = 0; i < 100; i++) { // try a few times to avoid punctuation
            int idx = rnd.nextInt(words.size());
            String w = words.get(idx).getWord();
            if (!w.equals(" .") && !w.equals(" $") && !w.equals(" !") && !w.equals(" ,")) {
                return w.toLowerCase();
            }
        }
        return words.get(0).getWord().toLowerCase();
    }

    // ---- Input/Output list updates ----

    public static void UpdateInputList(String input) {
        List<String> inputList = Data.getInputList();
        if (input.length() > 1) input = PunctuationFix_ForInput(input);
        if (!inputList.contains(input)) {
            inputList.add(input);
            Data.saveInputList(inputList);
        }
    }

    public static void UpdateOutputList(String userResponse) {
        if (logicRef == null) return;
        String lastAI = logicRef.getLastResponse();
        if (lastAI == null || lastAI.isEmpty()) return;
        String key = IsMultiPhrase(lastAI) ? Get_LastPhrase(lastAI) : lastAI;
        if (key == null) return;

        String value = userResponse;
        if (value.length() > 1) value = PunctuationFix_ForInput(value);
        if (key.length() > 1) key = PunctuationFix_ForInput(key);

        if (key.equals(value)) return; // avoid self‑reference

        List<String> outputs = Data.getAllOutputs(key);
        if (!outputs.contains(value)) {
            outputs.add(value);
            Data.saveOutput(outputs, key);
        }
    }

    public static void UpdateOutputList_MultiPhrase(List<String> phrases) {
        if (logicRef == null || phrases == null || phrases.isEmpty()) return;
        String lastAI = logicRef.getLastResponse();
        if (lastAI == null || lastAI.isEmpty()) return;
        String key = IsMultiPhrase(lastAI) ? Get_LastPhrase(lastAI) : lastAI;
        if (key == null) return;

        String firstPhrase = phrases.get(0);
        String combined = String.join("^", phrases);

        // Clean punctuation
        if (combined.length() > 1) combined = PunctuationFix_ForInput(combined);
        if (firstPhrase.length() > 1) firstPhrase = PunctuationFix_ForInput(firstPhrase);
        if (key.length() > 1) key = PunctuationFix_ForInput(key);

        List<String> outputs = Data.getAllOutputs(key);
        // If key equals first phrase, we treat as a related chain
        if (key.equals(firstPhrase)) return;

        // Check if we already have a combined entry for this key
        boolean found = false;
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).contains(firstPhrase)) {
                // update existing combined string
                List<String> related = Data.getRelatedOutputs(key, firstPhrase);
                // merge: keep existing, add new ones
                for (String p : phrases) {
                    String cleaned = PunctuationFix_ForInput(p);
                    if (!related.contains(cleaned)) related.add(cleaned);
                }
                // rebuild the combined line
                StringBuilder sb = new StringBuilder(firstPhrase);
                for (String r : related) {
                    if (!r.equals(firstPhrase)) sb.append("^").append(r);
                }
                outputs.set(i, sb.toString());
                found = true;
                break;
            }
        }
        if (!found) {
            outputs.add(combined);
        }
        Data.saveOutput(outputs, key);
    }

    // ---- Multi‑phrase helpers ----

    public static boolean IsMultiPhrase(String s) {
        if (s == null) return false;
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == '.' || c == '!' || c == '$' || c == '?') count++;
        }
        return count > 1;
    }

    public static boolean IsMultiPhrase(String[] tokens) {
        if (tokens == null) return false;
        int count = 0;
        for (String t : tokens) {
            if (t.equals(" .") || t.equals(" !") || t.equals(" $") || t.equals(" ?")) count++;
        }
        return count > 1;
    }

    public static String Get_LastPhrase(String text) {
        if (text == null || text.length() <= 1) return null;
        int lastPunct = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '$' || c == '?') {
                lastPunct = i;
                break;
            }
        }
        if (lastPunct <= 0) return null;
        // find previous punctuation
        int prevPunct = -1;
        for (int i = lastPunct - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '$' || c == '?') {
                prevPunct = i;
                break;
            }
        }
        if (prevPunct == -1) return text.substring(0, lastPunct + 1);
        return text.substring(prevPunct + 2);
    }

    public static String Get_FirstPhrase(String text) {
        if (text == null || text.length() <= 1) return null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '$' || c == '?') {
                return text.substring(0, i + 1);
            }
        }
        return null;
    }

    // ---- Rule checker (capitalisation, punctuation) ----

    public static String RulesCheck(String input) {
        if (input == null || input.isEmpty()) return EMPTY;
        StringBuilder sb = new StringBuilder(input);

        // Replace '$' with '?'
        while (sb.indexOf("$") > 0) {
            sb.replace(sb.indexOf("$"), sb.indexOf("$") + 1, "?");
        }

        if (IsMultiPhrase(sb.toString())) {
            // Split into sentences and capitalise each
            String[] tokens = PunctuationFix_ForInput(sb.toString()).split(" ");
            List<String> sentences = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String t : tokens) {
                if (t.equals(".") || t.equals("!") || t.equals("?")) {
                    current.append(t);
                    sentences.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(t).append(" ");
                }
            }
            // Capitalise each sentence
            StringBuilder result = new StringBuilder();
            for (String s : sentences) {
                if (!s.isEmpty()) {
                    char first = s.charAt(0);
                    if (!Character.isUpperCase(first)) {
                        s = Character.toUpperCase(first) + s.substring(1);
                    }
                    result.append(s);
                }
            }
            sb = result;
        } else {
            // Single sentence – capitalise first letter
            if (sb.length() > 0) {
                char first = sb.charAt(0);
                if (!Character.isUpperCase(first)) {
                    sb.setCharAt(0, Character.toUpperCase(first));
                }
            }
        }

        // Clean up spaces before punctuation (reverse of PunctuationFix_ForInput)
        String cleaned = sb.toString()
                .replace(" ,", ",")
                .replace(" ;", ";")
                .replace(" .", ".")
                .replace(" ?", "?")
                .replace(" !", "!");

        // Trim trailing spaces and ensure ending punctuation
        cleaned = cleaned.trim();
        if (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last != '.' && last != '?' && last != '!') {
                cleaned += ".";
            }
        }
        return cleaned;
    }

    // ---- Topic generation (for thinking) ----

    public static List<String> GenTopics(String[] tokens, List<String> currentTopics) {
        List<String> low = Get_LowestFrequencies(tokens);
        List<String> result = new ArrayList<>(currentTopics);
        result.addAll(low);
        // also add any tokens that are already in currentTopics
        for (String t : currentTopics) {
            for (String tok : tokens) {
                if (tok.equals(t)) {
                    result.add(t);
                    break;
                }
            }
        }
        return result;
    }

    public static void GenTopics_ForThinking(String[] tokens) {
        if (logicRef == null || tokens == null) return;
        List<String> low = Get_LowestFrequencies(tokens);
        List<String> existing = new ArrayList<>(logicRef.getTopicsThinking());
        logicRef.getTopicsThinking().clear();
        logicRef.getTopicsThinking().addAll(low);
        for (String t : existing) {
            for (String tok : tokens) {
                if (tok.equals(t)) {
                    logicRef.getTopicsThinking().add(t);
                    break;
                }
            }
        }
    }

    public static void AddTopics(String input, List<String> topics) {
        if (input.length() > 1) input = PunctuationFix_ForInput(input);
        List<String> outputs = Data.getAllOutputs(input);
        // Update existing topic counts
        for (int i = 0; i < outputs.size(); i++) {
            String line = outputs.get(i);
            if (line.startsWith("#")) {
                String[] parts = line.split("~");
                if (parts.length == 2) {
                    String topic = parts[0].substring(1); // remove '#'
                    boolean found = false;
                    for (String t : topics) {
                        if (t.equalsIgnoreCase(topic)) {
                            // increment
                            int count = Integer.parseInt(parts[1]) + 1;
                            outputs.set(i, "#" + topic + "~" + count);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // decrement
                        int count = Integer.parseInt(parts[1]) - 1;
                        if (count > 0) {
                            outputs.set(i, "#" + topic + "~" + count);
                        } else {
                            outputs.remove(i);
                            i--;
                        }
                    }
                }
            } else if (line.contains("~") && !line.startsWith("#")) {
                // remove non‑topic lines with '~'
                outputs.remove(i);
                i--;
            }
        }
        Data.saveOutput(outputs, input);

        // Add new topics that are not yet present
        List<String> currentOutputs = Data.getAllOutputs(input);
        for (String t : topics) {
            boolean exists = false;
            for (String out : currentOutputs) {
                if (out.startsWith("#" + t.toLowerCase() + "~")) {
                    exists = true;
                    break;
                }
            }
            if (!exists && !t.equals(" .") && !t.equals(" $") && !t.equals(" !") && !t.equals(" ,") && !t.isEmpty()) {
                currentOutputs.add(0, "#" + t.toLowerCase() + "~7");
            }
        }
        Data.saveOutput(currentOutputs, input);
    }

    // ---- Temporary static getters for Logic state (to be removed later) ----
    // We need these because Util still refers to Logic.last_response etc.
    // We'll add these in Logic as public static for compatibility during transition.
    // In final version, Util should receive Logic instance.
    // For now, we'll keep them as static methods that delegate to Logic's static fields.
    // However, since we refactored Logic to instance, we need to adjust.
    // I'll provide a bridge: keep a static reference to the current Logic instance (global) for Util.
    // Better: pass Logic to methods that need it. We already have ToggleAdvanced and ToggleSpeech accepting Logic.
    // For Encourage/Discourage and UpdateOutputList, we need Logic.last_response.
    // We can add a static method Logic.getLastResponse() that returns the static field (if we keep it static for now).
    // But we want to remove static. Let's keep Logic.last_response as a static field for now (only that one) to avoid massive changes.
    // Actually, we already made Logic instance-based; but we can still have a static reference to the current Logic instance in MainActivity and pass it where needed.
    // For brevity, we'll keep a global static Logic instance in a new class, or simply pass Logic to these methods.
    // Since we are not changing MainActivity in this step, we'll add a static variable in Util: private static Logic logicInstance; and set it via Util.init(Logic).
    // We'll do that.
    private static Logic logicRef;

    public static void init(Logic logic) {
        logicRef = logic;
    }

    // ---- Updated methods that use logicRef ----
    // The Encourage and Discourage methods are already rewritten above.
    // We also need to ensure the UpdateOutputList methods use logicRef.
    // They are already updated to use logicRef.

    // Finally, we need to adjust ToggleAdvanced and ToggleSpeech to use the passed Logic.
    // Already done above.
}
