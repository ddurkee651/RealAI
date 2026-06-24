package com.oblivionburn.nlp.engine;

/**
 * Manages delay setting and config persistence.
 * Response methods and advanced mode are removed.
 */
public class SettingsManager {

    private final Logic logic;
    private int delaySelection = 0;

    public SettingsManager(Logic logic, com.oblivionburn.nlp.ui.UIManager ui) {
        this.logic = logic;
    }

    public int getDelaySelection() { return delaySelection; }
    public void setDelaySelection(int sel) { delaySelection = sel; }

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

    public void applyDelaySetting() {
        if (delaySelection == 3) {
            ConversationEngine.bl_DelayForever = true;
            ConversationEngine.int_Time = 0;
            Data.setConfig("Infinite",
                    "false", "true", "true", "true",
                    String.valueOf(logic.isSpeech()));
        } else {
            int seconds = (delaySelection * 10) + 10;
            ConversationEngine.bl_DelayForever = false;
            ConversationEngine.int_Time = seconds * 1000;
            Data.setConfig(seconds + " seconds",
                    "false", "true", "true", "true",
                    String.valueOf(logic.isSpeech()));
        }
    }

    // Keep getResponseToggleText and toggleResponseMethod stubs so existing code compiles,
    // but they are no longer used by the new UI.
    public int getResponseSelection() { return 0; }
    public void setResponseSelection(int sel) {}
    public String getResponseToggleText() { return "true"; }
    public void toggleResponseMethod() {}
}