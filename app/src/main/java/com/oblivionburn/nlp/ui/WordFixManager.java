package com.oblivionburn.nlp.ui;

import android.app.Activity;
import android.os.AsyncTask;
import android.widget.Toast;

import com.oblivionburn.nlp.engine.Data;
import com.oblivionburn.nlp.engine.WordData;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the "Correct Spelling" feature – word replacement across the brain.
 */
public class WordFixManager {

    private final Activity activity;
    private final UIManager ui;

    public WordFixManager(Activity activity, UIManager ui) {
        this.activity = activity;
        this.ui = ui;
    }

    /** Show the word‑fix UI with the current vocabulary. */
    public void showWordFix(int wordFixSelection) {
        List<WordData> words = Data.getWords();
        List<String> wordList = new ArrayList<>();
        for (WordData wd : words) wordList.add(wd.getWord());
        if (wordList.isEmpty()) {
            Toast.makeText(activity, "No words to fix.", Toast.LENGTH_SHORT).show();
            return;
        }
        ui.setSpinnerItems(wordList, 0);
        int sel = Math.min(wordFixSelection, wordList.size() - 1);
        ui.showWordFixViews(wordList.get(sel));
    }

    /** Run the background word‑replacement task. */
    public void applyWordFix(int wordFixSelection, String newWord, Runnable onComplete) {
        new WordFixTask(wordFixSelection, newWord, onComplete).execute();
    }

    private class WordFixTask extends AsyncTask<Void, Void, Void> {
        private final int selection;
        private final String newWord;
        private final Runnable onComplete;

        WordFixTask(int selection, String newWord, Runnable onComplete) {
            this.selection = selection;
            this.newWord = newWord;
            this.onComplete = onComplete;
        }

        @Override
        protected Void doInBackground(Void... params) {
            List<WordData> words = Data.getWords();
            if (selection >= words.size()) return null;
            String oldWord = words.get(selection).getWord();

            List<String> inputList = Data.getInputList();
            for (int i = 0; i < inputList.size(); i++) {
                String input = inputList.get(i);
                List<String> outputs = Data.getAllOutputs(input);
                for (int j = 0; j < outputs.size(); j++) {
                    if (outputs.get(j).contains(oldWord)) {
                        outputs.set(j, outputs.get(j).replace(oldWord, newWord));
                    }
                }
                Data.saveOutput(outputs, input);
                if (input.contains(oldWord)) {
                    inputList.set(i, input.replace(oldWord, newWord));
                }
            }
            Data.saveInputList(inputList);

            List<String> allWords = new ArrayList<>();
            for (WordData wd : words) allWords.add(wd.getWord());
            for (String word : allWords) {
                List<WordData> pre = Data.getPreWords(word);
                for (WordData wd : pre) {
                    if (wd.getWord().equals(oldWord)) wd.setWord(newWord);
                }
                Data.savePreWords(pre, word);

                List<WordData> pro = Data.getProWords(word);
                for (WordData wd : pro) {
                    if (wd.getWord().equals(oldWord)) wd.setWord(newWord);
                }
                Data.saveProWords(pro, word);
            }

            words.get(selection).setWord(newWord);
            Data.saveWords(words);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (onComplete != null) onComplete.run();
            Toast.makeText(activity, "Word replaced.", Toast.LENGTH_SHORT).show();
        }
    }
}