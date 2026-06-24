package com.oblivionburn.nlp.engine;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Data persistence layer – all file I/O for the AI brain.
 */
public final class Data {

    private static final String TAG = "Data";
    private static final String EMPTY = "";

    // File names and keys
    private static final String DIR_BRAIN = "/Brain";
    private static final String FILE_WORDS = "Words.txt";
    private static final String FILE_INPUT_LIST = "InputList.txt";
    private static final String FILE_CONFIG = "Config.ini";
    private static final String FILE_MEMORY = "Memory.txt";
    private static final String PREFIX_PRE = "Pre-";
    private static final String PREFIX_PRO = "Pro-";
    private static final String SUBDIR_HISTORY = "History";
    private static final String SUBDIR_THOUGHTS = "Thoughts";

    private static final int MAX_HISTORY_LINES = 40;

    private static String baseDir;

    private Data() { }

    public static void initData(Context context) {
        baseDir = context.getExternalFilesDir(null).getAbsolutePath();
        ensureDirectoriesExist();
        ensureConfigExists();
    }

    // ------------------------------------------------------------------------
    // Config handling
    // ------------------------------------------------------------------------

    private static File getConfigFile() {
        return new File(baseDir + DIR_BRAIN, FILE_CONFIG);
    }

    private static void ensureConfigExists() {
        File config = getConfigFile();
        if (config.exists()) return;
        try {
            if (config.createNewFile()) {
                String defaultConfig =
                        "Delay:90 seconds\n" +
                        "Advanced:false\n" +
                        "Topic Response Method:true\n" +
                        "Condition Response Method:true\n" +
                        "Procedural Response Method:true\n" +
                        "Speech:false\n";
                try (FileWriter fw = new FileWriter(config)) {
                    fw.write(defaultConfig);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create config file", e);
        }
    }

    private static void ensureDirectoriesExist() {
        File brain = new File(baseDir + DIR_BRAIN);
        if (!brain.exists()) brain.mkdirs();
        File history = new File(baseDir + DIR_BRAIN, SUBDIR_HISTORY);
        if (!history.exists()) history.mkdirs();
        File thoughts = new File(baseDir + DIR_BRAIN, SUBDIR_THOUGHTS);
        if (!thoughts.exists()) thoughts.mkdirs();
    }

    private static String getConfigValue(String key) {
        File config = getConfigFile();
        if (!config.exists()) return EMPTY;
        try (BufferedReader br = new BufferedReader(new FileReader(config))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(key + ":")) {
                    String[] parts = line.split(":", 2);
                    return parts.length > 1 ? parts[1].trim() : EMPTY;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading config for key: " + key, e);
        }
        return EMPTY;
    }

    public static String getDelay()          { return getConfigValue("Delay"); }
    public static String getAdvanced()       { return getConfigValue("Advanced"); }
    public static String getTopicBased()     { return getConfigValue("Topic Response Method"); }
    public static String getConditionBased() { return getConfigValue("Condition Response Method"); }
    public static String getProceduralBased(){ return getConfigValue("Procedural Response Method"); }
    public static String getSpeech()         { return getConfigValue("Speech"); }

    public static void setConfig(String delay, String advanced, String topic,
                                 String condition, String procedural, String speech) {
        File config = getConfigFile();
        try {
            if (!config.exists()) config.createNewFile();
            String content = "Delay:" + delay + "\n" +
                    "Advanced:" + advanced + "\n" +
                    "Topic Response Method:" + topic + "\n" +
                    "Condition Response Method:" + condition + "\n" +
                    "Procedural Response Method:" + procedural + "\n" +
                    "Speech:" + speech + "\n";
            try (FileWriter fw = new FileWriter(config)) {
                fw.write(content);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    // ------------------------------------------------------------------------
    // Words (main vocabulary)
    // ------------------------------------------------------------------------

    public static void saveWords(List<WordData> words) {
        File file = new File(baseDir + DIR_BRAIN, FILE_WORDS);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (WordData wd : words) {
                bw.write(wd.getWord() + "~" + wd.getFrequency());
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "saveWords failed", e);
        }
    }

    public static List<WordData> getWords() {
        List<WordData> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, FILE_WORDS);
        if (!file.exists()) return result;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("~")) {
                    String[] parts = line.split("~");
                    if (parts.length == 2 && Util.tryParseInt(parts[1])) {
                        String word = parts[0];
                        int freq = Integer.parseInt(parts[1]);
                        WordData wd = new WordData();
                        wd.setWord(word);
                        wd.setFrequency(freq);
                        result.add(wd);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getWords failed", e);
        }
        return result;
    }

    // ------------------------------------------------------------------------
    // Pre‑words and Pro‑words (adjacency)
    // ------------------------------------------------------------------------

    private static File getPreFile(String word) {
        return new File(baseDir + DIR_BRAIN, PREFIX_PRE + word + ".txt");
    }

    private static File getProFile(String word) {
        return new File(baseDir + DIR_BRAIN, PREFIX_PRO + word + ".txt");
    }

    private static List<WordData> readAdjacencyFile(File file) {
        List<WordData> list = new ArrayList<>();
        if (!file.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("~")) {
                    String[] parts = line.split("~");
                    if (parts.length == 2 && Util.tryParseInt(parts[1])) {
                        WordData wd = new WordData();
                        wd.setWord(parts[0]);
                        wd.setFrequency(Integer.parseInt(parts[1]));
                        list.add(wd);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "readAdjacencyFile failed for " + file.getName(), e);
        }
        return list;
    }

    private static void writeAdjacencyFile(File file, List<WordData> list) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (WordData wd : list) {
                bw.write(wd.getWord() + "~" + wd.getFrequency());
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "writeAdjacencyFile failed for " + file.getName(), e);
        }
    }

    public static void savePreWords(List<WordData> list, String word) {
        writeAdjacencyFile(getPreFile(word), list);
    }

    public static List<WordData> getPreWords(String word) {
        return readAdjacencyFile(getPreFile(word));
    }

    public static void saveProWords(List<WordData> list, String word) {
        writeAdjacencyFile(getProFile(word), list);
    }

    public static List<WordData> getProWords(String word) {
        return readAdjacencyFile(getProFile(word));
    }

    // ------------------------------------------------------------------------
    // Input/Output lists (phrases and responses)
    // ------------------------------------------------------------------------

    public static void saveInputList(List<String> inputList) {
        File file = new File(baseDir + DIR_BRAIN, FILE_INPUT_LIST);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (String s : inputList) {
                bw.write(s);
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "saveInputList failed", e);
        }
    }

    public static List<String> getInputList() {
        List<String> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, FILE_INPUT_LIST);
        if (!file.exists()) return result;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.equals(EMPTY)) result.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "getInputList failed", e);
        }
        return result;
    }

    public static void saveOutput(List<String> outputList, String inputPhrase) {
        File file = new File(baseDir + DIR_BRAIN, inputPhrase + ".txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (String s : outputList) {
                bw.write(s);
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "saveOutput failed for " + inputPhrase, e);
        }
    }

    public static List<String> getAllOutputs(String inputPhrase) {
        List<String> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, inputPhrase + ".txt");
        if (!file.exists()) return result;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.equals(EMPTY)) result.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "getAllOutputs failed for " + inputPhrase, e);
        }
        return result;
    }

    public static List<String> getOutputList_NoRelated(String inputPhrase) {
        List<String> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, inputPhrase + ".txt");
        if (!file.exists()) return result;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals(EMPTY) || line.contains("#")) continue;
                if (line.contains("^")) {
                    result.add(line.substring(0, line.indexOf('^')));
                } else {
                    result.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getOutputList_NoRelated failed for " + inputPhrase, e);
        }
        return result;
    }

    public static List<String> getOutputList_OnlyTopics(String inputPhrase) {
        List<String> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, inputPhrase + ".txt");
        if (!file.exists()) return result;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("#")) result.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "getOutputList_OnlyTopics failed for " + inputPhrase, e);
        }
        return result;
    }

    public static List<String> getRelatedOutputs(String inputPhrase, String topicWord) {
        List<String> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, inputPhrase + ".txt");
        if (!file.exists()) return result;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.equals(EMPTY)) continue;
                if (line.contains(topicWord) && line.contains("^")) {
                    for (String part : line.split("\\^")) {
                        result.add(part);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getRelatedOutputs failed for " + inputPhrase, e);
        }
        return result;
    }

    public static List<String> getTopics(String inputPhrase) {
        List<String> result = new ArrayList<>();
        File file = new File(baseDir + DIR_BRAIN, inputPhrase + ".txt");
        if (!file.exists()) return result;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("#")) {
                    int idx = line.indexOf('~');
                    if (idx > 1) {
                        String topic = line.substring(1, idx);
                        result.add(topic);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getTopics failed for " + inputPhrase, e);
        }
        return result;
    }

    // ------------------------------------------------------------------------
    // History and Thoughts (with line limit)
    // ------------------------------------------------------------------------

    private static File getTodayHistoryFile() {
        String date = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(new Date());
        return new File(baseDir + DIR_BRAIN + "/" + SUBDIR_HISTORY, date + ".txt");
    }

    private static File getTodayThoughtsFile() {
        String date = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(new Date());
        return new File(baseDir + DIR_BRAIN + "/" + SUBDIR_THOUGHTS, date + ".txt");
    }

    private static List<String> readLimitedLines(File file) {
        List<String> all = new ArrayList<>();
        if (!file.isFile()) return all;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.equals(EMPTY)) all.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "readLimitedLines failed for " + file.getName(), e);
        }
        List<String> limited = new ArrayList<>();
        int start = Math.max(0, all.size() - MAX_HISTORY_LINES);
        for (int i = start; i < all.size(); i++) {
            limited.add(all.get(i) + "\n");
        }
        return limited;
    }

    public static void saveHistory(List<String> history) {
        File file = getTodayHistoryFile();
        try {
            if (!file.exists()) file.createNewFile();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (String s : history) {
                    bw.write(s);
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "saveHistory failed", e);
        }
    }

    public static List<String> getHistory() {
        return readLimitedLines(getTodayHistoryFile());
    }

    public static void saveThoughts(List<String> thoughts) {
        File file = getTodayThoughtsFile();
        try {
            if (!file.exists()) file.createNewFile();
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (String s : thoughts) {
                    bw.write(s);
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "saveThoughts failed", e);
        }
    }

    public static List<String> getThoughts() {
        return readLimitedLines(getTodayThoughtsFile());
    }

    // ------------------------------------------------------------------------
    // Semantic Memory
    // ------------------------------------------------------------------------

    private static File getMemoryFile() {
        return new File(baseDir + DIR_BRAIN, FILE_MEMORY);
    }

    public static String getMemory(String key) {
        File file = getMemoryFile();
        if (!file.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(key + "|")) {
                    return line.substring(key.length() + 1);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getMemory failed", e);
        }
        return null;
    }

    public static void saveMemory(String key, String value) {
        File file = getMemoryFile();
        List<String> lines = new ArrayList<>();
        boolean found = false;
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(key + "|")) {
                        if (value != null) {
                            lines.add(key + "|" + value);
                        }
                        found = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "saveMemory read failed", e);
            }
        }
        if (!found && value != null) {
            lines.add(key + "|" + value);
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "saveMemory write failed", e);
        }
    }
}
