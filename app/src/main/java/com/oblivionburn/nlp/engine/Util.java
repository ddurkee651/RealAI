package com.oblivionburn.nlp.engine;

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
 */
public final class Util {

    private static final String EMPTY = "";

    // ---- Memory (fact) extraction ----

    /**
     * Tries to extract a fact from user input.
     * Returns a string in the format "key|value", or null if none found.
     */
    public static String extractFact(String input) {
        if (input == null || input.isEmpty()) return null;

        Pattern namePattern = Pattern.compile("(?:my name is|i am called|call me)\\s+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher nameMatcher = namePattern.matcher(input);
        if (nameMatcher.find()) {
            return "user_name|" + nameMatcher.group(1).trim();
        }

        Pattern locPattern = Pattern.compile("(?:i (?:live|am) in|i am from|from)\\s+([A-Za-z\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher locMatcher = locPattern.matcher(input);
        if (locMatcher.find()) {
            return "user_location|" + locMatcher.group(1).trim();
        }

        Pattern agePattern = Pattern.compile("(?:i am|i'm)\\s+(\\d+)\\s*(?:years? old)?", Pattern.CASE_INSENSITIVE);
        Matcher ageMatcher = agePattern.matcher(input);
        if (ageMatcher.find()) {
            return "user_age|" + ageMatcher.group(1);
        }

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

    // ---- Clean up orphaned files ----

    public static void CleanMemory(final Context context) {
        new Thread(() -> {
            File brainDir = context.getExternalFilesDir(null);
            if (brainDir == null) return;

            List<String> inputList = Data.getInputList();
            List<String> cleaned = new ArrayList<>();
            for (String phrase : inputList) {
                File outputFile = new File(brainDir, phrase + ".txt");
                if (outputFile.exists()) {
                    List<String> outputs = Data.getAllOutputs(phrase);
                    boolean keep = false;
                    for (String out : outputs) {
                        if (!out.isEmpty() && !out.matches("#.*~\\d+")) {
                            keep = true;
                            break;
                        }
                    }
                    if (keep) {
                        cleaned.add(phrase);
                    } else {
                        outputFile.delete();
                    }
                }
            }
            Data.saveInputList(cleaned);

            File[] files = brainDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    if (name.equals("Words.txt") || name.equals("InputList.txt") ||
                            name.equals("Config.ini") || name.equals("Memory.txt") ||
                            name.startsWith("Pre-") || name.startsWith("Pro-")) {
                        continue;
                    }
                    String base = name.substring(0, name.lastIndexOf('.'));
                    if (!cleaned.contains(base)) {
                        f.delete();
                    }
                }
            }
        }).start();
    }

    public static void ClearLeftovers(Context context) {
        File dir = context.getFilesDir();
        if (dir == null) return;
        for (String name : new String[]{".txt", ",.txt", "..txt"}) {
            File f = new File(dir, name);
            if (f.exists()) f.delete();
        }
    }

    // ---- Encourage / Discourage ----

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

    // ---- Erase memory ----

    public static void EraseMemory(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                EraseMemory(child);
            }
        }
        if (dir.getName().contains("Config")) return;
        dir.delete();
    }

    // ---- Toggle options ----

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
        String delay = ConversationEngine.bl_DelayForever ? "Infinite" : (ConversationEngine.int_Time / 1000) + " seconds";
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

    // ---- Statistical helpers ----

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

    // ---- Topic and phrase related ----

    public static List<String> Get_TopicRelated(List<String> topics) {
        List<String> result = new ArrayList<>();
        if (topics.isEmpty()) return result;

        List<String> inputList = Data.getInputList();
        if (inputList.isEmpty()) return result;

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

        for (String phrase : candidatePhrases) {
            List<String> outputs = Data.getOutputList_OnlyTopics(phrase);
            for (String out : outputs) {
                String[] parts = out.split("~");
                if (parts.length == 2) {
                    for (int i = 0; i < topics.size(); i++) {
                        if (parts[0].equals("#" + topics.get(i)) && topicFreqs.get(i) == maxFreq) {
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
            if (word.equals(" .") || word.equals(" $") || word.equals(" !") || word.equals(" ,")) continue;
            if (!result.contains(word)) result.add(word);
        }
        return result;
    }

    public static String Get_RandomWord() {
        List<WordData> words = Data.getWords();
        if (words.isEmpty()) return EMPTY;
        Random rnd = new Random();
        for (int i = 0; i < 100; i++) {
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

        if (key.equals(value)) return;

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

        if (combined.length() > 1) combined = PunctuationFix_ForInput(combined);
        if (firstPhrase.length() > 1) firstPhrase = PunctuationFix_ForInput(firstPhrase);
        if (key.length() > 1) key = PunctuationFix_ForInput(key);

        List<String> outputs = Data.getAllOutputs(key);
        if (key.equals(firstPhrase)) return;

        boolean found = false;
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i).contains(firstPhrase)) {
                List<String> related = Data.getRelatedOutputs(key, firstPhrase);
                for (String p : phrases) {
                    String cleaned = PunctuationFix_ForInput(p);
                    if (!related.contains(cleaned)) related.add(cleaned);
                }
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

    // ---- Rule checker ----

    public static String RulesCheck(String input) {
        if (input == null || input.isEmpty()) return EMPTY;
        StringBuilder sb = new StringBuilder(input);

        while (sb.indexOf("$") > 0) {
            sb.replace(sb.indexOf("$"), sb.indexOf("$") + 1, "?");
        }

        if (IsMultiPhrase(sb.toString())) {
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
            if (sb.length() > 0) {
                char first = sb.charAt(0);
                if (!Character.isUpperCase(first)) {
                    sb.setCharAt(0, Character.toUpperCase(first));
                }
            }
        }

        String cleaned = sb.toString()
                .replace(" ,", ",")
                .replace(" ;", ";")
                .replace(" .", ".")
                .replace(" ?", "?")
                .replace(" !", "!");

        cleaned = cleaned.trim();
        if (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last != '.' && last != '?' && last != '!') {
                cleaned += ".";
            }
        }
        return cleaned;
    }

    // ---- Topic generation ----

    public static List<String> GenTopics(String[] tokens, List<String> currentTopics) {
        List<String> low = Get_LowestFrequencies(tokens);
        List<String> result = new ArrayList<>(currentTopics);
        result.addAll(low);
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
        for (int i = 0; i < outputs.size(); i++) {
            String line = outputs.get(i);
            if (line.startsWith("#")) {
                String[] parts = line.split("~");
                if (parts.length == 2) {
                    String topic = parts[0].substring(1);
                    boolean found = false;
                    for (String t : topics) {
                        if (t.equalsIgnoreCase(topic)) {
                            int count = Integer.parseInt(parts[1]) + 1;
                            outputs.set(i, "#" + topic + "~" + count);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
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
                outputs.remove(i);
                i--;
            }
        }
        Data.saveOutput(outputs, input);

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

    // ---- Logic reference bridge ----
    private static Logic logicRef;

    public static void init(Logic logic) {
        logicRef = logic;
    }
}
