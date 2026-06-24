package com.oblivionburn.nlp;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.oblivionburn.nlp.engine.ConversationEngine;
import com.oblivionburn.nlp.engine.Data;
import com.oblivionburn.nlp.engine.Logic;
import com.oblivionburn.nlp.engine.SettingsManager;
import com.oblivionburn.nlp.engine.Util;
import com.oblivionburn.nlp.engine.WordData;
import com.oblivionburn.nlp.menu.MenuActions;
import com.oblivionburn.nlp.menu.MenuDelegate;
import com.oblivionburn.nlp.ui.LiteText;
import com.oblivionburn.nlp.ui.PressEffectTouchListener;
import com.oblivionburn.nlp.ui.UIManager;
import com.oblivionburn.nlp.ui.WordFixManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity
        implements AdapterView.OnItemSelectedListener, TextToSpeech.OnInitListener, MenuActions {

    private static final String TAG = "REALAI";

    private UIManager ui;
    private MenuDelegate menuDelegate;
    private WordFixManager wordFixManager;
    private SettingsManager settings;

    private LiteText outputView;
    private LiteText inputView;
    private Button wordFixButton;

    private boolean isThoughtMode = false;
    private boolean isWordFixMode = false;
    private boolean isDelayMode = false;
    private boolean isResponsesMode = false;
    private boolean isTipsMode = false;

    private int wordFixSelection = 0;

    private Logic logic;
    private TextToSpeech tts;
    private ConversationEngine engine;
    private File brainDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(1);
        setContentView(R.layout.activity_main);

        Data.initData(this);

        outputView = findViewById(R.id.txt_Output);
        outputView.setMaxLines(Integer.MAX_VALUE);
        inputView = findViewById(R.id.txt_Input);
        Button menuButton = findViewById(R.id.btn_Menu);
        Button encourageButton = findViewById(R.id.btn_Encourage);
        Button discourageButton = findViewById(R.id.btn_Discourage);
        wordFixButton = findViewById(R.id.btn_WordFix);
        Spinner wordFixSpinner = findViewById(R.id.sp_WordFix);
        LiteText wordFixTextView = findViewById(R.id.txt_WordFix);
        ImageView faceImageView = findViewById(R.id.img_Face);

        ui = new UIManager(this, outputView, inputView, wordFixTextView,
                wordFixSpinner, wordFixButton, menuButton, encourageButton,
                discourageButton, faceImageView);
        wordFixSpinner.setOnItemSelectedListener(this);

        brainDir = new File(getExternalFilesDir(null), "Brain");
        logic = new Logic();
        Util.init(logic);

        tts = new TextToSpeech(getApplicationContext(), this);

        engine = new ConversationEngine(mainHandler, logic, tts,
                this::scrollHistory,
                line -> { ui.appendOutputLine(line); ui.setFaceImage(R.drawable.face_neutral); });

        menuDelegate = new MenuDelegate(this, ui, logic, engine);
        wordFixManager = new WordFixManager(this, ui);
        settings = new SettingsManager(logic, ui);
        settings.loadDelayFromConfig();

        createBrainDirectories();
        setupListeners();

        engine.startTimer();
        engine.startThinking();

        displayTipsMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        engine.stopTimer();
        engine.stopThinking();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        engine.stopTimer();
        engine.stopThinking();
        mainHandler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (isThoughtMode) closeThoughtMode();
        else if (isWordFixMode || isDelayMode || isResponsesMode) closeWordFixMode();
        else if (isTipsMode) closeTipsMode();
        else confirmExitDialog();
    }

    // ----------------------------------------------------------------
    // Initialisation helpers
    // ----------------------------------------------------------------

    private void createBrainDirectories() {
        if (!brainDir.exists()) brainDir.mkdirs();
        File historyDir = new File(brainDir, "History");
        File thoughtDir = new File(brainDir, "Thoughts");
        historyDir.mkdirs();
        thoughtDir.mkdirs();
        try {
            new File(brainDir, "Words.txt").createNewFile();
            new File(brainDir, "InputList.txt").createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create brain files", e);
        }
    }

    private void setupListeners() {
        inputView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ui.setFaceImage(R.drawable.face_neutral);
                engine.setTyping(s.length() != 0);
            }
        });

        inputView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                onSend(v);
                return true;
            }
            return false;
        });

        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                onSend(v);
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_Encourage).setOnTouchListener(
                new PressEffectTouchListener(R.drawable.face_encourage,
                        () -> {}, () -> {},
                        findViewById(R.id.img_Face)));
        findViewById(R.id.btn_Discourage).setOnTouchListener(
                new PressEffectTouchListener(R.drawable.face_discourage,
                        () -> {}, () -> {},
                        findViewById(R.id.img_Face)));
    }

    // ----------------------------------------------------------------
    // UI callbacks
    // ----------------------------------------------------------------

    private void scrollHistory() {
        ui.clearAndShowHistory(Data.getHistory());
        ui.setFaceImage(R.drawable.face_neutral);
    }

    // ----------------------------------------------------------------
    // Button handlers
    // ----------------------------------------------------------------

    public void onSend(View view) {
        engine.processUserInput(inputView.getText().toString());
        inputView.setText("");
    }

    public void onMenu(View view) {
        if (isWordFixMode || isDelayMode || isResponsesMode) {
            closeWordFixMode();
        } else if (isThoughtMode) {
            closeThoughtMode();
        } else if (isTipsMode) {
            closeTipsMode();
        } else {
            openOptionsMenu();
        }
    }

    public void WordFix(View view) {
        if (isWordFixMode) {
            String newWord = ui.getWordFixText();
            wordFixManager.applyWordFix(wordFixSelection, newWord, this::closeWordFixMode);
        } else if (isDelayMode) {
            settings.applyDelaySetting();
            closeWordFixMode();
        } else if (isResponsesMode) {
            settings.toggleResponseMethod();
            wordFixButton.setText(settings.getResponseToggleText());
        }
    }

    public void Encourage(View view) {
        Util.CleanMemory(this);
        Util.Encourage();
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        ui.clearAndShowHistory(history);
        logic.setNewInput(false);
    }

    public void Discourage(View view) {
        Util.CleanMemory(this);
        Util.Discourage();
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        ui.clearAndShowHistory(history);
        logic.setNewInput(false);
    }

    public void NewSession() {
        logic.setNewInput(false);
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        Util.CleanMemory(this);
        ui.setInputVisible(true);
        ui.setMenuButton(getString(R.string.menu_button), true);
        ui.setAdvancedUIEnabled(true);
        ui.clearAndShowHistory(history);
        ui.showKeyboard();
    }

    // ----------------------------------------------------------------
    // Mode display helpers
    // ----------------------------------------------------------------

    private void displayWordFixMode() {
        wordFixManager.showWordFix(wordFixSelection);
        engine.stopTimer();
        isWordFixMode = true;
    }

    private void displayDelayMode() {
        String[] options = {"10 seconds", "20 seconds", "30 seconds", "Infinite"};
        ui.setSpinnerItems(options, settings.getDelaySelection());
        ui.showWordFixButtonOnly(getString(R.string.btn_accept));
        engine.stopTimer();
        isDelayMode = true;
    }

    private void displayResponsesMode() {
        ui.setMenuButton(getString(R.string.ok_button), true);
        String[] options = {"Topic Response Method", "Condition Response Method", "Procedural Response Method"};
        ui.setSpinnerItems(options, settings.getResponseSelection());
        ui.showWordFixButtonOnly(settings.getResponseToggleText());
        engine.stopTimer();
        isResponsesMode = true;
    }

    private void displayTipsMode() {
        ui.setInputVisible(false);
        ui.setMenuButton(getString(R.string.ok_button), true);
        ui.setAdvancedUIEnabled(false);
        String tips = "Here are some tips for teaching the AI: \n\n" +
                "1. The AI learns from observing how you respond to what it says... so, if it says \"Hello.\" and you say \"How are you?\" it will learn that \"How are you?\" is a possible response to \"Hello.\". If you say something it has never seen before, it will repeat it to see how -you- would respond to it. Learning by imitation, like a young child, is not the only way it learns as you will soon discover.\n\n" +
                "2. It will generate stuff that sounds nonsensical early on... this is part of the learning process, similar to the way children phrase things in ways that don't quite make sense early on. \n\n" +
                "3. If it says something that doesn't make sense, you can discourage the AI by pressing the Discourage button. This will also reset the session so that whatever you say next won't be considered a response to what was last said. \n\n" +
                "4. In contrast to Discouraging the AI, there is a button to Encourage it and let it know it has used words properly. \n\n" +
                "5. Limit your response to a single sentence or question. \n\n" +
                "6. Use complete sentences when responding. Start with a capital letter and end with a punctuation mark. \n\n" +
                "7. Avoid contractions (use \"it is\" instead of \"it's\"). \n\n" +
                "8. The AI runs in real-time and will try to initiate conversation on its own if idle for too long. To adjust how long it waits before assuming you're idle, or to make it never check for idleness, check out the Set Delay option in the Menu. \n\n" +
                "9. The AI cannot see/hear/taste/smell/feel any 'things' you refer to, so it can never have any contextual understanding of what exactly the 'thing' is (the way you understand it). This also means it'll never understand you trying to reference it (or yourself) directly, as it can never have a concept of anything external being something different from it without spatial recognition gained from sight/touch/sound. \n\n" +
                "10. In general... keep it simple. The simpler you speak to it, the better it learns. \n\n" +
                "For help, check Discord: https://discord.gg/s894BGn \n\n" +
                "For more information and details of how the AI works, check the Forum: http://realai.freeforums.net/#category-3 \n\n";
        ui.showTips(tips);
        engine.stopTimer();
        isTipsMode = true;
    }

    // ---- Mode close helpers ----
    private void closeWordFixMode() {
        ui.hideWordFixViews();
        ui.setOutputVisible(true);
        ui.setInputVisible(true);
        ui.setMenuButton(getString(R.string.menu_button), true);
        ui.setAdvancedUIEnabled(true);
        ui.clearAndShowHistory(Data.getHistory());
        ui.showKeyboard();
        isWordFixMode = false;
        isDelayMode = false;
        isResponsesMode = false;
        engine.startTimer();
    }

    private void closeThoughtMode() {
        ui.setInputVisible(true);
        ui.setMenuButton(getString(R.string.menu_button), true);
        ui.setAdvancedUIEnabled(true);
        ui.clearAndShowHistory(Data.getHistory());
        ui.showKeyboard();
        isThoughtMode = false;
        engine.startTimer();
    }

    private void closeTipsMode() {
        ui.setInputVisible(true);
        ui.setMenuButton(getString(R.string.menu_button), true);
        ui.setAdvancedUIEnabled(true);
        ui.clearAndShowHistory(Data.getHistory());
        ui.showKeyboard();
        isTipsMode = false;
        engine.startTimer();
        engine.startThinking();
    }

    // ----------------------------------------------------------------
    // Confirm dialogs
    // ----------------------------------------------------------------

    private void confirmExitDialog() {
        engine.stopTimer();
        new AlertDialog.Builder(this)
                .setTitle("System Message")
                .setMessage("Exit the NLP Program?")
                .setPositiveButton("Yes", (d, which) -> finishAffinity())
                .setNegativeButton("No", (d, which) -> engine.startTimer())
                .setCancelable(false).show();
    }

    private void confirmEraseDialog() {
        engine.stopTimer();
        new AlertDialog.Builder(this)
                .setTitle("System Message")
                .setMessage("Erase all memory?")
                .setPositiveButton("Yes", (d, which) -> {
                    Util.EraseMemory(brainDir);
                    outputView.setText("");
                    inputView.setText("");
                    createBrainDirectories();
                    ui.clearAndShowHistory(Data.getHistory());
                    Toast.makeText(this, "Brain erased.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", (d, which) -> engine.startTimer())
                .setCancelable(false).show();
    }

    // ----------------------------------------------------------------
    // Menu bridge
    // ----------------------------------------------------------------

    @Override public boolean onCreateOptionsMenu(Menu menu) { return menuDelegate.onCreateOptionsMenu(menu); }
    @Override public boolean onPrepareOptionsMenu(Menu menu) { return menuDelegate.onPrepareOptionsMenu(menu); }
    @Override public boolean onOptionsItemSelected(MenuItem item) { return menuDelegate.onOptionsItemSelected(item); }
    @Override public boolean onMenuOpened(int featureId, Menu menu) { return menuDelegate.onMenuOpened(featureId, menu); }
    @Override public void onPanelClosed(int featureId, Menu menu) { menuDelegate.onPanelClosed(featureId, menu); }

    // ----------------------------------------------------------------
    // MenuActions implementation
    // ----------------------------------------------------------------

    @Override public void confirmErase() { confirmEraseDialog(); }
    @Override public void confirmExit() { confirmExitDialog(); }
    @Override public void newSession() { NewSession(); }
    @Override public void displayResponses() { displayResponsesMode(); }
    @Override public void displayDelay() { displayDelayMode(); }
    @Override public void displayWordFix() { displayWordFixMode(); }
    @Override public void displayTips() { displayTipsMode(); }
    @Override public void setThoughtMode(boolean v) { isThoughtMode = v; }
    @Override public void setWordFixMode(boolean v) { isWordFixMode = v; }
    @Override public void setDelayMode(boolean v) { isDelayMode = v; }
    @Override public void setResponsesMode(boolean v) { isResponsesMode = v; }
    @Override public void setTipsMode(boolean v) { isTipsMode = v; }
    @Override public boolean isThoughtMode() { return isThoughtMode; }
    @Override public boolean isTipsMode() { return isTipsMode; }
    @Override public void scrollHistory() { ui.clearAndShowHistory(Data.getHistory()); ui.setFaceImage(R.drawable.face_neutral); }
    @Override public void invalidateOptionsMenu() { super.invalidateOptionsMenu(); }

    // ----------------------------------------------------------------
    // Spinner listener
    // ----------------------------------------------------------------

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (isWordFixMode) {
            wordFixSelection = position;
            List<WordData> words = Data.getWords();
            if (position < words.size()) {
                ui.updateWordFixText(words.get(position).getWord());
            }
        } else if (isDelayMode) {
            settings.setDelaySelection(position);
        } else if (isResponsesMode) {
            settings.setResponseSelection(position);
            wordFixButton.setText(settings.getResponseToggleText());
        }
    }

    @Override public void onNothingSelected(AdapterView<?> parent) {}

    // ----------------------------------------------------------------
    // TTS
    // ----------------------------------------------------------------

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
    }
}