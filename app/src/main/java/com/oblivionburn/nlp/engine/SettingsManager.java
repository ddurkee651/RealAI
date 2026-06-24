package com.oblivionburn.nlp.engine;

import com.oblivionburn.nlp.ui.UIManager;

/**
 * Manages delay, response‑method toggles, and config persistence.
 */
public class SettingsManager {

    private final Logic logic;
    private final UIManager ui;

    private int delaySelection = 0;
    private int responseSelection = 0;

    public SettingsManager(Logic logic, UIManager ui) {
        this.logic = logic;
        this.ui = ui;
    }

    // ---- Accessors ----
    public int getDelaySelection() { return delaySelection; }
    public void setDelaySelection(int sel) { delaySelection = sel; }
    public int getResponseSelection() { return responseSelection; }
    public void setResponseSelection(int sel) { responseSelection = sel; }

    // ---- Load from config ----
    public void loadDelayFromConfig() {
        String delayStr = Data.getDelay();
        if (delayStr == null || delayStr.isEmpty()) return;
        if (delayStr.equalsIgnoreCase("Infinite")) {
            ConversationEngine.bl_DelayForever = true;
            ConversationEngine.int_Time = 0;
            delaySelection = 3;
        } else {
            ConversationEngine.bl_DelayForever = false;
            try {
                int seconds = Integer.parseInt(delayStr.replace(" seconds", "").trim());
                ConversationEngine.int_Time = seconds * 1000;
                delaySelection = Math.max(0, (seconds / 10) - 1);
            } catch (NumberFormatException e) {
                ConversationEngine.int_Time = 10000;
                delaySelection = 0;
            }
        }
    }

    // ---- Apply delay setting ----
    public void applyDelaySetting() {
        if (delaySelection == 3) {
            ConversationEngine.bl_DelayForever = true;
            ConversationEngine.int_Time = 0;
            Data.setConfig("Infinite",
                    String.valueOf(logic.isAdvanced()),
                    String.valueOf(logic.isTopicBased()),
                    String.valueOf(logic.isConditionBased()),
                    String.valueOf(logic.isProceduralBased()),
                    String.valueOf(logic.isSpeech()));
        } else {
            int seconds = (delaySelection * 10) + 10;
            ConversationEngine.bl_DelayForever = false;
            ConversationEngine.int_Time = seconds * 1000;
            Data.setConfig(seconds + " seconds",
                    String.valueOf(logic.isAdvanced()),
                    String.valueOf(logic.isTopicBased()),
                    String.valueOf(logic.isConditionBased()),
                    String.valueOf(logic.isProceduralBased()),
                    String.valueOf(logic.isSpeech()));
        }
    }

    // ---- Toggle response method ----
    public void toggleResponseMethod() {
        if (responseSelection == 0) {
            logic.setTopicBased(!logic.isTopicBased());
        } else if (responseSelection == 1) {
            logic.setConditionBased(!logic.isConditionBased());
        } else if (responseSelection == 2) {
            logic.setProceduralBased(!logic.isProceduralBased());
        }
        saveCurrentConfig();
    }

    /** Returns the current toggle state as a string for the button label. */
    public String getResponseToggleText() {
        if (responseSelection == 0) return String.valueOf(logic.isTopicBased());
        if (responseSelection == 1) return String.valueOf(logic.isConditionBased());
        return String.valueOf(logic.isProceduralBased());
    }

    private void saveCurrentConfig() {
        String delayStr = ConversationEngine.bl_DelayForever ? "Infinite" : ((delaySelection * 10) + 10) + " seconds";
        Data.setConfig(delayStr,
                String.valueOf(logic.isAdvanced()),
                String.valueOf(logic.isTopicBased()),
                String.valueOf(logic.isConditionBased()),
                String.valueOf(logic.isProceduralBased()),
                String.valueOf(logic.isSpeech()));
    }
}