package com.oblivionburn.nlp.engine;

import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Logic {

    private static final String TAG = "Logic";
    private static final String EMPTY = "";
    private static final int MAX_CONTEXT_SIZE = 5;

    private String lastResponse = EMPTY;
    private String lastResponseThinking = EMPTY;

    private boolean initiation = false;
    private boolean newInput = false;
    private boolean userInput = false;
    private boolean advanced = false;
    private boolean topicBased = true;
    private boolean conditionBased = true;
    private boolean proceduralBased = true;
    private boolean speech = false;

    private final List<String> topics = new ArrayList<>();
    private final List<String> topicsThinking = new ArrayList<>();
    private final List<String> conversationContext = new ArrayList<>();

    private final Random random = new Random();

    public Logic() {}

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    public synchronized String prepInputAndRespond(String rawInput) {
        String[] tokens = prepInput(rawInput);
        if (tokens == null || tokens.length == 0) return EMPTY;
        return respond(tokens, rawInput);
    }

    public synchronized String think(String rawInput) {
        String[] tokens = prepInput(rawInput);
        if (tokens == null || tokens.length == 0) {
            return generateResponse(getRandomWord());
        }
        return thinkInternal(tokens);
    }

    /** Generates an idle response WITHOUT affecting lastResponse or lastResponseThinking. */
    public synchronized String idleRespond() {
        String seed = getRandomWord();
        String response = generateResponse(seed);
        String finalResponse = Util.RulesCheck(response);
        if (finalResponse == null || finalResponse.isEmpty()) return EMPTY;
        return finalResponse;
    }

    // ---- State getters/setters ----
    public boolean isInitiation() { return initiation; }
    public void setInitiation(boolean v) { initiation = v; }
    public boolean isNewInput() { return newInput; }
    public void setNewInput(boolean v) { newInput = v; }
    public boolean isUserInput() { return userInput; }
    public void setUserInput(boolean v) { userInput = v; }
    public boolean isAdvanced() { return advanced; }
    public void setAdvanced(boolean v) { advanced = v; }
    public boolean isTopicBased() { return topicBased; }
    public void setTopicBased(boolean v) { topicBased = v; }
    public boolean isConditionBased() { return conditionBased; }
    public void setConditionBased(boolean v) { conditionBased = v; }
    public boolean isProceduralBased() { return proceduralBased; }
    public void setProceduralBased(boolean v) { proceduralBased = v; }
    public boolean isSpeech() { return speech; }
    public void setSpeech(boolean v) { speech = v; }
    public String getLastResponse() { return lastResponse; }
    public String getLastResponseThinking() { return lastResponseThinking; }
    public List<String> getTopics() { return topics; }
    public List<String> getTopicsThinking() { return topicsThinking; }

    // ----------------------------------------------------------------
    // Core response generation
    // ----------------------------------------------------------------

    public String respond(String[] currentTokens, String rawInput) {
        conversationContext.add("User: " + rawInput);
        if (conversationContext.size() > MAX_CONTEXT_SIZE) conversationContext.remove(0);

        // Special queries
        String specialResponse = handleSpecialQueries(rawInput);
        if (specialResponse != null) {
            String finalSpecial = Util.RulesCheck(specialResponse);
            conversationContext.add("AI: " + finalSpecial);
            if (conversationContext.size() > MAX_CONTEXT_SIZE) conversationContext.remove(0);
            return finalSpecial;
        }

        // Semantic memory
        String memoryResponse = null;
        String queryKey = Util.detectFactQuery(rawInput);
        if (queryKey != null) {
            String value = Data.getMemory(queryKey);
            if (value != null) {
                memoryResponse = Util.buildFactResponse(queryKey, value);
            } else {
                memoryResponse = "I don't know that yet. Tell me and I'll remember.";
            }
        }
        String fact = Util.extractFact(rawInput);
        if (fact != null) {
            String[] parts = fact.split("\\|");
            if (parts.length == 2) {
                Data.saveMemory(parts[0], parts[1]);
                memoryResponse = "I've remembered that " + parts[0].replace("_", " ") + " is " + parts[1] + ".";
            }
        }

        // Always learn from user input
        if (currentTokens != null && currentTokens.length > 0) {
            updateDataStructures(currentTokens);
        }

        if (memoryResponse != null) {
            String finalMem = Util.RulesCheck(memoryResponse);
            conversationContext.add("AI: " + finalMem);
            if (conversationContext.size() > MAX_CONTEXT_SIZE) conversationContext.remove(0);
            lastResponse = finalMem;
            newInput = true;
            return finalMem;
        }

        // Context enrichment
        String[] enrichedTokens = buildEnrichedTokens(currentTokens);

        // Topic update
        if (userInput) {
            topics.clear();
            topics.addAll(Util.GenTopics(enrichedTokens, new ArrayList<>()));
            Util.AddTopics(rawInput, topics);
            lastResponseThinking = rawInput;
        } else if (initiation && topics.isEmpty()) {
            topics.add(getRandomWord());
        }

        if (topics.isEmpty()) return EMPTY;

        // Response generation
        StringBuilder response = new StringBuilder();
        boolean usedTopic = false;

        if (advanced) {
            String topic = topics.get(random.nextInt(topics.size()));
            response.append(generateResponse(topic));
            if (initiation && response.toString().equals(topic)) topics.clear();
        } else {
            if (topicBased) {
                List<String> related = Util.Get_TopicRelated(topics);
                if (!related.isEmpty()) {
                    response.append(related.get(random.nextInt(related.size())));
                    usedTopic = true;
                    if (initiation && Util.RulesCheck(response.toString()).equals(Util.Get_FirstPhrase(lastResponse))) {
                        topics.clear();
                        usedTopic = false;
                    }
                }
            }
            if (!usedTopic && conditionBased) {
                String fixedInput = Util.PunctuationFix_ForInput(rawInput);
                List<String> noRelated = Data.getOutputList_NoRelated(fixedInput);
                if (!noRelated.isEmpty()) {
                    response.append(noRelated.get(random.nextInt(noRelated.size())));
                    usedTopic = true;
                    if (initiation && Util.RulesCheck(response.toString()).equals(Util.Get_FirstPhrase(lastResponse))) {
                        topics.clear();
                        usedTopic = false;
                    }
                }
            }
            if (!usedTopic && proceduralBased) {
                String topic = topics.isEmpty() ? getRandomWord() : topics.get(random.nextInt(topics.size()));
                response.append(generateResponse(topic));
                if (initiation && Util.RulesCheck(response.toString()).equals(Util.Get_FirstPhrase(lastResponse))) {
                    topics.clear();
                }
            }
        }

        String currentResponse = response.toString();
        List<String> phraseRelated = Util.Get_PhraseRelated(currentResponse);
        for (String phrase : phraseRelated) {
            if (!phrase.equals(currentResponse)) response.append(" ").append(phrase);
        }

        String finalResponse = Util.RulesCheck(response.toString());
        if (finalResponse.isEmpty()) return EMPTY;

        finalResponse = injectMemory(finalResponse);

        conversationContext.add("AI: " + finalResponse);
        if (conversationContext.size() > MAX_CONTEXT_SIZE) conversationContext.remove(0);
        lastResponse = finalResponse;
        newInput = true;
        return finalResponse;
    }

    // ----------------------------------------------------------------
    // Special queries
    // ----------------------------------------------------------------

    private String handleSpecialQueries(String input) {
        if (input == null) return null;
        String lower = input.toLowerCase();
        if (lower.matches(".*what did i (just )?say.*") ||
            lower.matches(".*what was i (talking about|saying).*")) {
            if (conversationContext.size() >= 2) {
                String lastUser = conversationContext.get(conversationContext.size() - 2);
                if (lastUser.startsWith("User: ")) {
                    return "You just said: " + lastUser.substring(6);
                } else {
                    return "I couldn't find your last message.";
                }
            } else {
                return "You haven't said anything yet.";
            }
        }
        return null;
    }

    // ----------------------------------------------------------------
    // Context enrichment
    // ----------------------------------------------------------------

    private String[] buildEnrichedTokens(String[] currentTokens) {
        StringBuilder contextBuilder = new StringBuilder();
        int start = Math.max(0, conversationContext.size() - 3);
        for (int i = start; i < conversationContext.size(); i++) {
            String turn = conversationContext.get(i);
            String clean = turn.replaceFirst("^(User|AI): ", "");
            contextBuilder.append(clean).append(" ");
        }
        String contextString = contextBuilder.toString();
        String[] contextTokens = prepInput(contextString);

        List<String> merged = new ArrayList<>();
        if (currentTokens != null) {
            for (String t : currentTokens) merged.add(t);
        }
        if (contextTokens != null) {
            for (String t : contextTokens) {
                if (!merged.contains(t)) merged.add(t);
            }
        }
        return merged.toArray(new String[0]);
    }

    // ----------------------------------------------------------------
    // Memory injection into greetings
    // ----------------------------------------------------------------

    private String injectMemory(String response) {
        if (response == null || response.isEmpty()) return response;
        if (response.toLowerCase().matches(".*\\b(hello|hi|hey|greetings)\\b.*")) {
            String name = Data.getMemory("user_name");
            if (name != null && !response.contains(name)) {
                return response.replaceFirst("(?i)\\b(hello|hi|hey|greetings)\\b", "$0 " + name);
            }
        }
        return response;
    }

    // ----------------------------------------------------------------
    // Thinking (does NOT change lastResponseThinking)
    // ----------------------------------------------------------------

    private String thinkInternal(String[] tokens) {
        StringBuilder response = new StringBuilder();
        Util.GenTopics_ForThinking(tokens);
        if (topicsThinking.isEmpty()) {
            return generateResponse(getRandomWord());
        }

        boolean used = false;
        List<String> related = Util.Get_TopicRelated(topicsThinking);
        if (!related.isEmpty()) {
            response.append(related.get(random.nextInt(related.size())));
            used = true;
        }

        if (!used) {
            String fixedInput = Util.PunctuationFix_ForInput(lastResponseThinking);
            List<String> noRelated = Data.getOutputList_NoRelated(fixedInput);
            if (!noRelated.isEmpty()) {
                response.append(noRelated.get(random.nextInt(noRelated.size())));
                used = true;
            }
        }

        if (!used) {
            String topic = topicsThinking.get(random.nextInt(topicsThinking.size()));
            String gen = generateResponse(topic);
            response.append(gen);
            if (Util.RulesCheck(gen).equals(lastResponseThinking)) {
                response.append(generateResponse(getRandomWord()));
            }
        }

        String current = response.toString();
        List<String> phraseRelated = Util.Get_PhraseRelated(current);
        for (String phrase : phraseRelated) {
            if (!phrase.equals(current)) response.append(" ").append(phrase);
        }

        String finalResponse = Util.RulesCheck(response.toString());
        return finalResponse.isEmpty() ? EMPTY : finalResponse;
    }

    // ----------------------------------------------------------------
    // Tokeniser
    // ----------------------------------------------------------------

    public String[] prepInput(String input) {
        if (TextUtils.isEmpty(input)) return new String[0];

        List<String> charList = new ArrayList<>();
        for (char c : input.toCharArray()) charList.add(Character.toString(c));

        String[] illegal = {"|", "\\", "*", "<", "\"", ":", ">", "#"};
        for (int i = 0; i < charList.size(); i++) {
            String ch = charList.get(i);
            for (String ill : illegal) {
                if (ch.equals(ill)) {
                    charList.remove(i);
                    i--;
                    break;
                }
            }
        }

        for (int i = 0; i < charList.size(); i++) {
            String ch = charList.get(i);
            switch (ch) {
                case ",":  charList.set(i, " ,"); break;
                case ";":  charList.set(i, " ;"); break;
                case "?":  charList.set(i, " $"); break;
                case "$":  charList.set(i, " $"); break;
                case "!":  charList.set(i, " !"); break;
                case ".":
                    if (i + 2 <= charList.size() && charList.get(i + 1).equals(".")) {
                        charList.set(i, " .");
                        i += 2;
                    } else {
                        charList.set(i, " .");
                    }
                    break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String ch : charList) sb.append(ch);
        String trimmed = sb.toString().trim();
        if (TextUtils.isEmpty(trimmed)) return new String[0];

        String[] rawTokens = trimmed.split(" ");
        for (int i = 0; i < rawTokens.length; i++) {
            rawTokens[i] = Util.PunctuationFix_ForInput(rawTokens[i]);
        }
        return rawTokens;
    }

    // ----------------------------------------------------------------
    // Statistical learning
    // ----------------------------------------------------------------

    private void updateDataStructures(String[] tokens) {
        if (tokens == null || tokens.length == 0) return;

        List<WordData> allWords = Data.getWords();
        Map<String, WordData> wordMap = new HashMap<>();
        for (WordData wd : allWords) wordMap.put(wd.getWord(), wd);

        for (String token : tokens) {
            WordData existing = wordMap.get(token);
            if (existing != null) {
                existing.setFrequency(existing.getFrequency() + 1);
            } else if (!token.equals(EMPTY)) {
                WordData newWd = new WordData();
                newWd.setWord(token);
                newWd.setFrequency(1);
                allWords.add(newWd);
                wordMap.put(token, newWd);
            }
        }
        Data.saveWords(allWords);

        Map<String, List<WordData>> preUpdates = new HashMap<>();
        Map<String, List<WordData>> proUpdates = new HashMap<>();

        for (int i = 0; i < tokens.length - 1; i++) {
            String current = tokens[i];
            String next = tokens[i + 1];

            List<WordData> preList = Data.getPreWords(next);
            preList = updateAdjacencyList(preList, current);
            preUpdates.put(next, preList);

            List<WordData> proList = Data.getProWords(current);
            proList = updateAdjacencyList(proList, next);
            proUpdates.put(current, proList);
        }

        for (Map.Entry<String, List<WordData>> entry : preUpdates.entrySet()) {
            Data.savePreWords(entry.getValue(), entry.getKey());
        }
        for (Map.Entry<String, List<WordData>> entry : proUpdates.entrySet()) {
            Data.saveProWords(entry.getValue(), entry.getKey());
        }
    }

    private List<WordData> updateAdjacencyList(List<WordData> list, String targetWord) {
        if (targetWord.equals(EMPTY)) return list;
        for (WordData wd : list) {
            if (wd.getWord().equals(targetWord)) {
                wd.setFrequency(wd.getFrequency() + 1);
                return list;
            }
        }
        WordData newWd = new WordData();
        newWd.setWord(targetWord);
        newWd.setFrequency(1);
        list.add(newWd);
        return list;
    }

    // ----------------------------------------------------------------
    // Markov chain generator
    // ----------------------------------------------------------------

    private String generateResponse(String seed) {
        String leftResult = seed;
        String leftBuilt = seed;
        boolean continueLeft = true;

        while (continueLeft) {
            List<WordData> preWords = Data.getPreWords(leftResult);
            if (preWords.isEmpty()) { continueLeft = false; break; }

            List<String> candidates = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            for (WordData wd : preWords) {
                if (wd.getFrequency() > 0) {
                    candidates.add(wd.getWord());
                    weights.add(wd.getFrequency());
                }
            }
            if (weights.isEmpty()) { continueLeft = false; break; }

            int chosenWeight = choose(weights);
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < weights.size(); i++) {
                if (weights.get(i).intValue() == chosenWeight) indices.add(i);
            }
            String chosen = candidates.get(indices.get(random.nextInt(indices.size())));

            if (chosen.length() > 1 && Character.isUpperCase(chosen.charAt(0))) {
                leftBuilt = chosen + " " + leftBuilt;
                break;
            }

            boolean duplicate = false;
            for (String word : leftBuilt.split(" ")) {
                if (Util.PunctuationFix_ForInput(word).equals(chosen)) { duplicate = true; break; }
            }
            if (!duplicate) leftBuilt = chosen + " " + leftBuilt;
            leftResult = chosen;
        }

        String rightBuilt = leftBuilt;
        String rightResult = seed;
        boolean continueRight = true;

        while (continueRight) {
            List<WordData> proWords = Data.getProWords(rightResult);
            if (proWords.isEmpty()) { continueRight = false; break; }

            List<String> candidates = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            for (WordData wd : proWords) {
                if (wd.getFrequency() > 0) {
                    candidates.add(wd.getWord());
                    weights.add(wd.getFrequency());
                }
            }
            if (weights.isEmpty()) { continueRight = false; break; }

            int chosenWeight = choose(weights);
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < weights.size(); i++) {
                if (weights.get(i).intValue() == chosenWeight) indices.add(i);
            }
            String chosen = candidates.get(indices.get(random.nextInt(indices.size())));

            boolean duplicate = false;
            for (String word : rightBuilt.split(" ")) {
                if (Util.PunctuationFix_ForInput(word).equals(chosen)) { duplicate = true; break; }
            }
            if (!duplicate) {
                rightBuilt = rightBuilt + " " + chosen;
                if (chosen.equals(".") || chosen.equals("$") || chosen.equals("!")) break;
            }
            rightResult = chosen;
        }

        return rightBuilt.trim();
    }

    private int choose(List<Integer> weights) {
        int total = 0;
        for (int w : weights) total += w;
        int target = random.nextInt(total) + 1;
        int sum = 0;
        for (int w : weights) {
            sum += w;
            if (sum >= target) return w;
        }
        return weights.get(0);
    }

    private String getRandomWord() {
        List<WordData> allWords = Data.getWords();
        if (allWords.isEmpty()) return "hello";
        return allWords.get(random.nextInt(allWords.size())).getWord();
    }
}
