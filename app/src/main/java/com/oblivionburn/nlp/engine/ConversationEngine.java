package com.oblivionburn.nlp.engine;

import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.List;
import java.util.function.Consumer;

public class ConversationEngine {

    private final Handler handler;
    private final Logic logic;
    private final TextToSpeech tts;
    private final Runnable onHistoryUpdated;
    private final Consumer<String> onIdleResponse;

    private boolean isTyping = false;
    private boolean isBored = false;
    private int idleDelayCounter = 0;

    private TimerRunnable timerRunnable;
    private ThoughtRunnable thoughtRunnable;

    public static boolean bl_DelayForever = false;
    public static int int_Time = 10000;

    public ConversationEngine(Handler handler, Logic logic, TextToSpeech tts,
                              Runnable onHistoryUpdated,
                              Consumer<String> onIdleResponse) {
        this.handler = handler;
        this.logic = logic;
        this.tts = tts;
        this.onHistoryUpdated = onHistoryUpdated;
        this.onIdleResponse = onIdleResponse;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
        if (typing) {
            stopTimer();
            stopThinking();
        } else {
            startTimer();
            startThinking();
        }
    }

    public void processUserInput(String input) {
        if (input == null || input.isEmpty()) return;

        logic.setInitiation(false);
        logic.setUserInput(true);
        String[] tokens = logic.prepInput(input);
        if (tokens != null && tokens.length > 0) {
            List<String> history = Data.getHistory();
            String cleaned = Util.RulesCheck(input);
            history.add("User: " + cleaned);
            String response;
            try {
                response = logic.respond(tokens, cleaned);
            } catch (Exception e) {
                Log.e("ConversationEngine", "respond crashed", e);
                response = "[brain error - please try again]";
            }
            if (response != null && !response.isEmpty()) {
                history.add("AI: " + response);
            }
            if (logic.isSpeech()) {
                tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
            }
            Data.saveHistory(history);
            if (onHistoryUpdated != null) onHistoryUpdated.run();
        }
    }

    private void attentionSpan() {
        if (isTyping) return;
        logic.setNewInput(false);
        logic.setInitiation(true);
        logic.setUserInput(false);
        String response;
        try {
            response = logic.idleRespond();
        } catch (Exception e) {
            Log.e("ConversationEngine", "idleRespond crashed", e);
            response = null;
        }
        if (response != null && !response.isEmpty()) {
            if (onIdleResponse != null) onIdleResponse.accept("AI: " + response + "\n");
        }
        isBored = false;
    }

    public void startTimer() {
        if (timerRunnable == null) timerRunnable = new TimerRunnable();
        handler.removeCallbacks(timerRunnable);
        idleDelayCounter = 0;
        handler.post(timerRunnable);
    }

    public void stopTimer() {
        if (timerRunnable != null) handler.removeCallbacks(timerRunnable);
    }

    public void startThinking() {
        if (thoughtRunnable == null) thoughtRunnable = new ThoughtRunnable();
        handler.removeCallbacks(thoughtRunnable);
        handler.post(thoughtRunnable);
    }

    public void stopThinking() {
        if (thoughtRunnable != null) handler.removeCallbacks(thoughtRunnable);
    }

    private class TimerRunnable implements Runnable {
        @Override
        public void run() {
            if (isBored) return;
            if (idleDelayCounter != 0) {
                if (idleDelayCounter == 1 && !bl_DelayForever) {
                    isBored = true;
                    attentionSpan();
                    idleDelayCounter = 0;
                }
            } else {
                idleDelayCounter++;
            }
            int delay = bl_DelayForever ? 60000 : int_Time;
            if (delay <= 0) delay = 10000;
            handler.postDelayed(this, delay);
        }
    }

    private class ThoughtRunnable implements Runnable {
        @Override
        public void run() {
            logic.setUserInput(false);
            List<String> thoughts = Data.getThoughts();
            String thought;
            try {
                thought = logic.think(logic.getLastResponseThinking());
                thought = Util.RulesCheck(thought);
            } catch (Exception e) {
                Log.e("ConversationEngine", "think crashed", e);
                thought = null;
            }
            if (thought != null && !thought.isEmpty()) {
                thoughts.add("NLP: " + thought);
                Data.saveThoughts(thoughts);
            }
            handler.postDelayed(this, 2000);
        }
    }
}
