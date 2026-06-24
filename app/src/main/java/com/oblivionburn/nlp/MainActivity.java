package com.oblivionburn.nlp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.oblivionburn.nlp.engine.ConversationEngine;
import com.oblivionburn.nlp.engine.Data;
import com.oblivionburn.nlp.engine.Logic;
import com.oblivionburn.nlp.engine.SettingsManager;
import com.oblivionburn.nlp.engine.Util;
import com.oblivionburn.nlp.engine.WordData;
import com.oblivionburn.nlp.engine.neural.AlienMind;
import com.oblivionburn.nlp.engine.neural.Tokenizer;
import com.oblivionburn.nlp.ui.KnowledgeUploadActivity;
import com.oblivionburn.nlp.ui.LiteText;
import com.oblivionburn.nlp.ui.PressEffectTouchListener;
import com.oblivionburn.nlp.ui.UIManager;
import com.oblivionburn.nlp.ui.WordFixManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity
        implements AdapterView.OnItemSelectedListener, TextToSpeech.OnInitListener {

    private static final String TAG = "REALAI";

    private UIManager ui;
    private WordFixManager wordFixManager;
    private SettingsManager settings;

    private LiteText outputView;
    private LiteText inputView;
    private Button wordFixButton;

    private boolean isThoughtMode = false;
    private boolean isWordFixMode = false;
    private boolean isDelayMode = false;
    private boolean isResponsesMode = false;

    private int wordFixSelection = 0;

    private Logic logic;
    private AlienMind brain;
    private TextToSpeech tts;
    private ConversationEngine engine;
    private File brainDir;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

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
        Button encourageButton = findViewById(R.id.btn_Encourage);
        Button discourageButton = findViewById(R.id.btn_Discourage);
        wordFixButton = findViewById(R.id.btn_WordFix);
        Spinner wordFixSpinner = findViewById(R.id.sp_WordFix);
        LiteText wordFixTextView = findViewById(R.id.txt_WordFix);
        ImageView faceImageView = findViewById(R.id.img_Face);

        ui = new UIManager(this, outputView, inputView, wordFixTextView,
                wordFixSpinner, wordFixButton, encourageButton,
                discourageButton, faceImageView);
        wordFixSpinner.setOnItemSelectedListener(this);

        brainDir = new File(getExternalFilesDir(null), "Brain");
        logic = new Logic();
        Util.init(logic);

        // Neural brain setup
        Tokenizer tokenizer = new Tokenizer();
        brain = new AlienMind(tokenizer);
        File brainFile = new File(getExternalFilesDir(null), "alien_brain.dat");
        File vocabFile = new File(getExternalFilesDir(null), "alien_vocab.txt");
        if (brainFile.exists() && vocabFile.exists()) {
            try {
                tokenizer.loadVocab(vocabFile);
                brain.load(brainFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load brain", e);
            }
        }
        logic.setBrain(brain);

        tts = new TextToSpeech(getApplicationContext(), this);

        engine = new ConversationEngine(mainHandler, logic, tts,
                this::scrollHistory,
                line -> { ui.appendOutputLine(line); ui.setFaceImage(R.drawable.face_neutral); });

        wordFixManager = new WordFixManager(this, ui);
        settings = new SettingsManager(logic, ui);
        settings.loadDelayFromConfig();

        createBrainDirectories();
        setupListeners();

        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(drawerToggle);

        engine.startTimer();
        engine.startThinking();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
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
        if (brain != null) {
            try {
                File brainFile = new File(getExternalFilesDir(null), "alien_brain.dat");
                File vocabFile = new File(getExternalFilesDir(null), "alien_vocab.txt");
                brain.getTokenizer().saveVocab(vocabFile);
                brain.save(brainFile);
            } catch (IOException e) {
                Log.e(TAG, "Failed to save brain", e);
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (isThoughtMode) closeThoughtMode();
        else if (isWordFixMode || isDelayMode || isResponsesMode) closeWordFixMode();
        else if (drawerLayout.isDrawerOpen(findViewById(R.id.nav_view))) drawerLayout.closeDrawers();
        else confirmExitDialog();
    }

    // ----------------------------------------------------------------
    // Drawer
    // ----------------------------------------------------------------

    private boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        drawerLayout.closeDrawers();

        if (id == R.id.nav_new_session) {
            NewSession();
        } else if (id == R.id.nav_upload_knowledge) {
            startActivity(new Intent(this, KnowledgeUploadActivity.class));
        } else if (id == R.id.nav_word_fix) {
            displayWordFixMode();
        } else if (id == R.id.nav_view_thinking) {
            item.setChecked(!item.isChecked());
            isThoughtMode = item.isChecked();
            if (isThoughtMode) {
                engine.stopTimer();
                engine.startThinking();
            } else {
                engine.startTimer();
            }
        } else if (id == R.id.nav_settings) {
            showSettingsDialog();
        } else if (id == R.id.nav_erase_brain) {
            confirmEraseDialog();
        } else if (id == R.id.nav_exit) {
            confirmExitDialog();
        }
        return true;
    }

    public void onDrawerToggle(View view) {
        if (drawerLayout.isDrawerOpen(findViewById(R.id.nav_view))) {
            drawerLayout.closeDrawers();
        } else {
            drawerLayout.openDrawer(findViewById(R.id.nav_view));
        }
    }

    // ----------------------------------------------------------------
    // Settings dialog (Delay + Speech only)
    // ----------------------------------------------------------------

    private void showSettingsDialog() {
        String[] delays = {"10 seconds", "20 seconds", "30 seconds", "Infinite"};
        int checkedDelay = settings.getDelaySelection();
        boolean speechOn = logic.isSpeech();

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Settings")
                .setSingleChoiceItems(delays, checkedDelay, (dialog, which) -> {
                    settings.setDelaySelection(which);
                    settings.applyDelaySetting();
                })
                .setPositiveButton(speechOn ? "Speech: OFF" : "Speech: ON", (dialog, which) -> {
                    logic.setSpeech(!logic.isSpeech());
                    // re‑save config with new speech state
                    String delayStr = ConversationEngine.bl_DelayForever ? "Infinite" : (ConversationEngine.int_Time / 1000) + " seconds";
                    Data.setConfig(delayStr,
                            String.valueOf(logic.isAdvanced()),
                            String.valueOf(logic.isTopicBased()),
                            String.valueOf(logic.isConditionBased()),
                            String.valueOf(logic.isProceduralBased()),
                            String.valueOf(logic.isSpeech()));
                })
                .setNegativeButton("Close", null)
                .show();
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
    // UI callback
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
        logic.encourageResponse();
        ui.setFaceImage(R.drawable.face_encourage);
        mainHandler.postDelayed(() -> ui.setFaceImage(R.drawable.face_neutral), 500);
    }

    public void Discourage(View view) {
        logic.discourageResponse();
        ui.setFaceImage(R.drawable.face_discourage);
        mainHandler.postDelayed(() -> ui.setFaceImage(R.drawable.face_neutral), 500);
        NewSession();
    }

    public void NewSession() {
        logic.setNewInput(false);
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        Util.CleanMemory(this);
        ui.setInputVisible(true);
        ui.setAdvancedUIEnabled(true);
        ui.clearAndShowHistory(history);
        ui.showKeyboard();
    }

    // ----------------------------------------------------------------
    // Mode helpers
    // ----------------------------------------------------------------

    private void displayWordFixMode() {
        wordFixManager.showWordFix(wordFixSelection);
        engine.stopTimer();
        isWordFixMode = true;
    }

    private void closeWordFixMode() {
        ui.hideWordFixViews();
        ui.setOutputVisible(true);
        ui.setInputVisible(true);
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
        ui.setAdvancedUIEnabled(true);
        ui.clearAndShowHistory(Data.getHistory());
        ui.showKeyboard();
        isThoughtMode = false;
        engine.startTimer();
    }

    // ----------------------------------------------------------------
    // Confirm dialogs
    // ----------------------------------------------------------------

    private void confirmExitDialog() {
        engine.stopTimer();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Exit")
                .setMessage("Exit RealAI?")
                .setPositiveButton("Yes", (d, which) -> finishAffinity())
                .setNegativeButton("No", (d, which) -> engine.startTimer())
                .setCancelable(false).show();
    }

    private void confirmEraseDialog() {
        engine.stopTimer();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Erase Brain")
                .setMessage("Erase all learned knowledge?")
                .setPositiveButton("Yes", (d, which) -> {
                    Util.EraseMemory(brainDir);
                    outputView.setText("");
                    inputView.setText("");
                    createBrainDirectories();
                    ui.clearAndShowHistory(Data.getHistory());
                    Toast.makeText(this, "Brain erased.", Toast.LENGTH_SHORT).show();
                    engine.startTimer();
                })
                .setNegativeButton("No", (d, which) -> engine.startTimer())
                .setCancelable(false).show();
    }

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
