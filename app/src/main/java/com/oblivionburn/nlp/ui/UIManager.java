package com.oblivionburn.nlp.ui;

import com.oblivionburn.nlp.R;
import android.app.Activity;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import java.util.List;

public class UIManager {

    private final Activity activity;
    private final LiteText outputView;
    private final LiteText inputView;
    private final LiteText wordFixTextView;
    private final Spinner wordFixSpinner;
    private final Button wordFixButton;
    private final Button menuButton;
    private final Button encourageButton;
    private final Button discourageButton;
    private final ImageView faceImageView;

    public UIManager(Activity activity,
                     LiteText outputView,
                     LiteText inputView,
                     LiteText wordFixTextView,
                     Spinner wordFixSpinner,
                     Button wordFixButton,
                     Button menuButton,
                     Button encourageButton,
                     Button discourageButton,
                     ImageView faceImageView) {
        this.activity = activity;
        this.outputView = outputView;
        this.inputView = inputView;
        this.wordFixTextView = wordFixTextView;
        this.wordFixSpinner = wordFixSpinner;
        this.wordFixButton = wordFixButton;
        this.menuButton = menuButton;
        this.encourageButton = encourageButton;
        this.discourageButton = discourageButton;
        this.faceImageView = faceImageView;
    }

    // ---- Output / History ----
    public void clearAndShowHistory(List<String> history) {
        outputView.setText("");
        outputView.setMovementMethod(new ScrollingMovementMethod());
        for (String line : history) outputView.append(line);
        outputView.setSelection(outputView.getText().length());
    }

    public void clearAndShowThoughts(List<String> thoughts) {
        outputView.setText("");
        outputView.setMovementMethod(new ScrollingMovementMethod());
        for (String line : thoughts) outputView.append(line);
        outputView.setSelection(outputView.getText().length());
    }

    public void appendOutputLine(String line) {
        outputView.append(line);
        outputView.setSelection(outputView.getText().length());
    }

    public void setOutputText(String text) {
        outputView.setText(text);
    }

    // ---- Face image ----
    public void setFaceImage(int resId) {
        faceImageView.setImageResource(resId);
    }

    // ---- Advanced UI ----
    public void setAdvancedUIEnabled(boolean enabled) {
        int vis = enabled ? View.VISIBLE : View.INVISIBLE;
        encourageButton.setVisibility(vis);
        encourageButton.setClickable(enabled);
        encourageButton.setFocusable(enabled);
        discourageButton.setVisibility(vis);
        discourageButton.setClickable(enabled);
        discourageButton.setFocusable(enabled);
        faceImageView.setVisibility(vis);
        if (enabled) faceImageView.setImageResource(R.drawable.face_neutral);
    }

    // ----WordFix----
    public String getWordFixText() {
        return wordFixTextView.getText().toString();
    }

    public void updateWordFixText(String text) {
        wordFixTextView.setText(text);
    }

    // ---- Keyboard ----
    public void showKeyboard() {
        inputView.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 1);
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
    }

    // ---- Menu button ----
    public void setMenuButton(String text, boolean visible) {
        menuButton.setText(text);
        menuButton.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- Input/output visibility ----
    public void setInputVisible(boolean visible) {
        inputView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public void setOutputVisible(boolean visible) {
        outputView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    // ---- Spinner helpers ----
    public void setSpinnerItems(List<String> items, int selection) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wordFixSpinner.setAdapter(adapter);
        wordFixSpinner.setSelection(selection);
    }

    public void setSpinnerItems(String[] items, int selection) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wordFixSpinner.setAdapter(adapter);
        wordFixSpinner.setSelection(selection);
    }

    // ---- WordFix views ----
    public void showWordFixViews(String wordText) {
        wordFixTextView.setText(wordText);
        wordFixTextView.setVisibility(View.VISIBLE);
        wordFixTextView.setClickable(true);
        wordFixTextView.setFocusableInTouchMode(true);
        wordFixTextView.setFocusable(true);
        wordFixTextView.requestFocus();

        wordFixButton.setText(R.string.btn_accept);
        wordFixButton.setVisibility(View.VISIBLE);
        wordFixButton.setClickable(true);
        wordFixButton.setFocusable(true);

        wordFixSpinner.setVisibility(View.VISIBLE);
        wordFixSpinner.setClickable(true);
        wordFixSpinner.setFocusable(true);
    }

    public void showWordFixButtonOnly(String text) {
        wordFixButton.setText(text);
        wordFixButton.setVisibility(View.VISIBLE);
        wordFixButton.setClickable(true);
        wordFixButton.setFocusable(true);
    }

    public void hideWordFixViews() {
        wordFixSpinner.setVisibility(View.GONE);
        wordFixSpinner.setClickable(false);
        wordFixSpinner.setFocusable(false);
        wordFixTextView.setVisibility(View.GONE);
        wordFixTextView.setClickable(false);
        wordFixTextView.setFocusable(false);
        wordFixTextView.setFocusableInTouchMode(false);
        wordFixButton.setVisibility(View.GONE);
        wordFixButton.setClickable(false);
        wordFixButton.setFocusable(false);
    }

    // ---- Tips ----
    public void showTips(String tipsText) {
        outputView.setMovementMethod(LinkMovementMethod.getInstance());
        outputView.setText(tipsText);
    }
}