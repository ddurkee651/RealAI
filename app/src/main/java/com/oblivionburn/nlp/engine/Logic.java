package com.oblivionburn.nlp.engine;

import android.text.TextUtils;

import com.oblivionburn.nlp.engine.neural.AlienMind;

import java.util.ArrayList;
import java.util.List;

public class Logic {

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

    private final List<String> conversationContext = new ArrayList<>();
    private final List<String> topics = new ArrayList<>();
    private final List<String> topicsThinking = new ArrayList<>();

    private AlienMind mind;

    public Logic() {}

    public void setBrain(AlienMind mind) {
        this.mind = mind;
    }

    public AlienMind getBrain() {
        return mind;
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    public synchronized String prepInputAndRespond(String rawInput) {
        String[] tokens = prepInput(rawInput);
        if (tokens == null || tokens.length == 0) return EMPTY;
        return respond(tokens, rawInput);
    }

    public synchronized String think(String rawInput) {
        if (mind == null) return EMPTY;
        return mind.generate(rawInput, 20);
    }

    public synchronized String idleRespond() {
        if (mind == null) return EMPTY;
        return mind.generate("", 15);
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
    // Core response generation (now neural)
    // ----------------------------------------------------------------

    public String respond(String[] currentTokens, String rawInput) {
        if (mind == null) return EMPTY;

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

        // Neural learning from user input
        mind.trainOnSentence(rawInput);

        if (memoryResponse != null) {
            String finalMem = Util.RulesCheck(memoryResponse);
            conversationContext.add("AI: " + finalMem);
            if (conversationContext.size() > MAX_CONTEXT_SIZE) conversationContext.remove(0);
            lastResponse = finalMem;
            newInput = true;
            return finalMem;
        }

        // Build context string for generation
        String context = buildContextString(rawInput);

        // Generate response
        String generated = mind.generate(context, 30);
        if (generated.isEmpty()) {
            generated = mind.generate("", 15);   // fallback
        }

        String finalResponse = Util.RulesCheck(generated);
        if (finalResponse.isEmpty()) return EMPTY;

        finalResponse = injectMemory(finalResponse);

        conversationContext.add("AI: " + finalResponse);
        if (conversationContext.size() > MAX_CONTEXT_SIZE) conversationContext.remove(0);
        lastResponse = finalResponse;
        newInput = true;
        return finalResponse;
    }

    /**
     * Reinforce the last response (Encourage button).
     */
    public void encourageResponse() {
        if (mind != null && lastResponse != null && !lastResponse.isEmpty()) {
            mind.reinforce(lastResponse, 3);   // train 3 times on it
        }
    }

    /**
     * Penalise the last response (Discourage button).
     */
    public void discourageResponse() {
        if (mind != null && lastResponse != null && !lastResponse.isEmpty()) {
            mind.penalize(lastResponse);
        }
    }

    // ---- Special queries (unchanged) ----
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

    // ---- Context building ----
    private String buildContextString(String latestUserInput) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, conversationContext.size() - 3);
        for (int i = start; i < conversationContext.size(); i++) {
            String turn = conversationContext.get(i);
            String clean = turn.replaceFirst("^(User|AI): ", "");
            sb.append(clean).append(" ");
        }
        sb.append(latestUserInput);
        return sb.toString().trim();
    }

    // ---- Memory injection into greetings (unchanged) ----
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
    // Tokeniser (unchanged)
    // ----------------------------------------------------------------
    public String[] prepInput(String input) {
        if (TextUtils.isEmpty(input)) return new String[0];
        List<String> charList = new ArrayList<>();
        for (char c : input.toCharArray()) charList.add(Character.toString(c));
        String[] illegal = {"|", "\\", "*", "<", "\"", ":", ">", "#"};
        for (int i = 0; i < charList.size(); i++) {
            String ch = charList.get(i);
            for (String ill : illegal) {
                if (ch.equals(ill)) { charList.remove(i); i--; break; }
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
                        charList.set(i, " ."); i += 2;
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
        for (int i = 0; i < rawTokens.length; i++) rawTokens[i] = Util.PunctuationFix_ForInput(rawTokens[i]);
        return rawTokens;
    }

    // ---- Public learning method for external use (KnowledgeInjector) ----
    public void learnFromSentence(String sentence) {
        if (mind != null) mind.trainOnSentence(sentence);
    }
}
