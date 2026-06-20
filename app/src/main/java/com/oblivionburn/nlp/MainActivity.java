package com.oblivionburn.nlp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Refactored MainActivity – Bluetooth removed.
 */
public class MainActivity extends Activity
        implements AdapterView.OnItemSelectedListener, TextToSpeech.OnInitListener {

    private static final String TAG = "REALAI";
    private static final String EMPTY = "";
    private static final int DELAY_INTERVAL_MS = 2000;

    // UI components
    private LiteText outputView;
    private LiteText inputView;
    private LiteText wordFixTextView;
    private Spinner wordFixSpinner;
    private Button wordFixButton;
    private Button menuButton;
    private Button encourageButton;
    private Button discourageButton;
    private ImageView faceImageView;

    // Menus
    private MenuItem miNewSession;
    private MenuItem miThoughts;
    private MenuItem miTips;
    private MenuItem miWordFix;
    private MenuItem miSetDelay;
    private MenuItem miSetResponse;
    private MenuItem miEraseBrain;
    private MenuItem miAdvanced;
    private MenuItem miExit;

    // State flags
    private boolean isTyping = false;
    private boolean isThoughtMode = false;
    private boolean isWordFixMode = false;
    private boolean isDelayMode = false;
    private boolean isResponsesMode = false;
    private boolean isTipsMode = false;
    private boolean isEncouragePressed = false;
    private boolean isDiscouragePressed = false;
    private boolean isBored = false;

    private int idleDelayCounter = 0;
    private int delaySelection = 0;
    private int responseSelection = 0;
    private int wordFixSelection = 0;

    // Core objects
    private Logic logic;
    private TextToSpeech tts;

    // Directories
    private File brainDir;
    private File historyDir;
    private File thoughtDir;

    // Handlers with weak references
    private final MainHandler mainHandler = new MainHandler(this);
    private TimerRunnable timerRunnable;
    private ThoughtRunnable thoughtRunnable;
    private RespondRunnable respondRunnable;

    // Stored menu for dynamic updates
    private Menu menu;

    // Static flags (used also by Util)
    public static boolean bl_DelayForever = false;
    public static int int_Time = 10000;  // default 10 seconds

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(1);
        setContentView(R.layout.activity_main);

        Data.initData(this);
        loadDelayFromConfig();

        initViews();

        brainDir = new File(getExternalFilesDir(null), "Brain");
        historyDir = new File(brainDir, "History");
        thoughtDir = new File(brainDir, "Thoughts");

        logic = new Logic();
        Util.init(logic);

        tts = new TextToSpeech(getApplicationContext(), this);

        createBrainDirectories();
        setupListeners();

        startTimer();
        startThinking();

        displayTips();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        stopThinking();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        stopThinking();
        mainHandler.removeCallbacksAndMessages(null);

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (isThoughtMode) {
            closeThoughtMode();
        } else if (isWordFixMode || isDelayMode || isResponsesMode) {
            closeWordFixMode();
        } else if (isTipsMode) {
            closeTipsMode();
        } else {
            confirmExit();
        }
    }

    // ------------------------------------------------------------------------
    // Initialisation helpers
    // ------------------------------------------------------------------------

    private void initViews() {
        outputView = findViewById(R.id.txt_Output);
        outputView.setMaxLines(Integer.MAX_VALUE);
        inputView = findViewById(R.id.txt_Input);
        menuButton = findViewById(R.id.btn_Menu);
        encourageButton = findViewById(R.id.btn_Encourage);
        discourageButton = findViewById(R.id.btn_Discourage);
        wordFixButton = findViewById(R.id.btn_WordFix);
        wordFixSpinner = findViewById(R.id.sp_WordFix);
        wordFixTextView = findViewById(R.id.txt_WordFix);
        faceImageView = findViewById(R.id.img_Face);

        wordFixSpinner.setOnItemSelectedListener(this);
    }

    private void createBrainDirectories() {
        if (!brainDir.exists()) brainDir.mkdirs();
        if (!historyDir.exists()) historyDir.mkdirs();
        if (!thoughtDir.exists()) thoughtDir.mkdirs();

        try {
            File wordsFile = new File(brainDir, "Words.txt");
            if (!wordsFile.exists()) wordsFile.createNewFile();
            File inputListFile = new File(brainDir, "InputList.txt");
            if (!inputListFile.exists()) inputListFile.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create brain files", e);
        }
    }

    // ------------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------------

    private void setupListeners() {
        inputView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                faceImageView.setImageResource(R.drawable.face_neutral);
                if (inputView.getText().toString().equals(EMPTY)) {
                    isTyping = false;
                    startTimer();
                    startThinking();
                } else {
                    isTyping = true;
                    logic.setInitiation(false);
                    stopTimer();
                    stopThinking();
                }
            }
        });

        inputView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                onSend(v);
                return true;
            }
            return false;
        });

        encourageButton.setOnTouchListener(new PressEffectTouchListener(
                R.drawable.face_encourage,
                () -> isEncouragePressed = true,
                () -> isEncouragePressed = false
        ));

        discourageButton.setOnTouchListener(new PressEffectTouchListener(
                R.drawable.face_discourage,
                () -> isDiscouragePressed = true,
                () -> isDiscouragePressed = false
        ));
    }

    // ------------------------------------------------------------------------
    // Handlers and Runnables
    // ------------------------------------------------------------------------

    private static class MainHandler extends Handler {
        private final WeakReference<MainActivity> activityRef;

        MainHandler(MainActivity activity) {
            super(Looper.getMainLooper());
            this.activityRef = new WeakReference<>(activity);
        }
    }

    private class TimerRunnable implements Runnable {
        @Override
        public void run() {
            MainActivity activity = MainActivity.this;
            if (activity.isBored) return;

            if (activity.idleDelayCounter != 0) {
                if (activity.idleDelayCounter == 1 && !bl_DelayForever) {
                    activity.isBored = true;
                    activity.attentionSpan();
                    activity.idleDelayCounter = 0;
                }
            } else {
                activity.idleDelayCounter++;
            }

            int delay = bl_DelayForever ? 60000 : int_Time;
            if (delay <= 0) delay = 10000;
            mainHandler.postDelayed(this, delay);
        }
    }

    private class ThoughtRunnable implements Runnable {
        @Override
        public void run() {
            MainActivity activity = MainActivity.this;
            logic.setUserInput(false);
            List<String> thoughts = Data.getThoughts();
            String thought = logic.think(logic.getLastResponseThinking());
            thought = Util.RulesCheck(thought);
            if (thought != null && !thought.equals(EMPTY)) {
                thoughts.add("NLP: " + thought);
                Data.saveThoughts(thoughts);
                Util.CleanMemory(activity);
            }
            if (activity.isThoughtMode) {
                activity.outputView.post(activity::scrollThoughts);
            }
            mainHandler.postDelayed(this, DELAY_INTERVAL_MS);
        }
    }

    private class RespondRunnable implements Runnable {
        @Override
        public void run() {
            MainActivity activity = MainActivity.this;
            String input = activity.inputView.getText().toString();

            if (input.length() == 0) {
                return; // nothing to process
            }

            logic.setInitiation(false);
            logic.setUserInput(true);
            String[] tokens = logic.prepInput(input);
            if (tokens != null && tokens.length > 0) {
                List<String> history = Data.getHistory();
                String cleanedInput = Util.RulesCheck(input);
                history.add("User: " + cleanedInput);
                String response = logic.respond(tokens, cleanedInput);
                if (response != null && !response.equals(EMPTY)) {
                    history.add("AI: " + response);
                }
                if (logic.isSpeech()) {
                    activity.tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
                }
                Data.saveHistory(history);
                Util.CleanMemory(activity);
                activity.outputView.post(activity::scrollHistory);
                activity.inputView.setText(EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Public methods called from UI
    // ------------------------------------------------------------------------

    public void onSend(View view) {
        mainHandler.post(respondRunnable);
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
            new WordFixTask().execute();
        } else if (isDelayMode) {
            applyDelaySetting();
        } else if (isResponsesMode) {
            toggleResponseMethod();
        }
    }

    public void Encourage(View view) {
        Util.CleanMemory(this);
        Util.Encourage();
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        outputView.post(this::scrollHistory);
        logic.setNewInput(false);
    }

    public void Discourage(View view) {
        Util.CleanMemory(this);
        Util.Discourage();
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        outputView.post(this::scrollHistory);
        logic.setNewInput(false);
    }

    public void NewSession() {
        logic.setNewInput(false);
        List<String> history = Data.getHistory();
        history.add("---New Session---");
        Data.saveHistory(history);
        Util.CleanMemory(this);
        inputView.setVisibility(View.VISIBLE);
        menuButton.setText(R.string.menu_button);
        menuButton.setVisibility(View.VISIBLE);
        enableAdvancedUI(true);
        outputView.post(this::scrollHistory);
        showKeyboard();
    }

    // ------------------------------------------------------------------------
    // Internal logic
    // ------------------------------------------------------------------------

    private void attentionSpan() {
        if (isTyping) return;
        logic.setNewInput(false);
        logic.setInitiation(true);
        logic.setUserInput(false);
        String response = logic.respond(new String[0], EMPTY);
        if (response == null || response.equals(EMPTY)) return;

        List<String> history = Data.getHistory();
        history.add("AI: " + response);
        Data.saveHistory(history);
        Util.CleanMemory(this);
        outputView.post(this::scrollHistory);
    }

    // ------------------------------------------------------------------------
    // Timer / Thinking control
    // ------------------------------------------------------------------------

    private void startTimer() {
        if (timerRunnable == null) timerRunnable = new TimerRunnable();
        mainHandler.removeCallbacks(timerRunnable);
        idleDelayCounter = 0;
        mainHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) mainHandler.removeCallbacks(timerRunnable);
    }

    private void startThinking() {
        if (thoughtRunnable == null) thoughtRunnable = new ThoughtRunnable();
        mainHandler.removeCallbacks(thoughtRunnable);
        mainHandler.post(thoughtRunnable);
    }

    private void stopThinking() {
        if (thoughtRunnable != null) mainHandler.removeCallbacks(thoughtRunnable);
    }

    // ------------------------------------------------------------------------
    // UI helpers
    // ------------------------------------------------------------------------

    private void scrollHistory() {
        outputView.setText(EMPTY);
        outputView.setMovementMethod(new ScrollingMovementMethod());
        List<String> history = Data.getHistory();
        for (String line : history) {
            outputView.append(line);
        }
        outputView.setSelection(outputView.getText().length());
        faceImageView.setImageResource(R.drawable.face_neutral);
        isBored = false;
    }

    private void scrollThoughts() {
        outputView.setText(EMPTY);
        outputView.setMovementMethod(new ScrollingMovementMethod());
        List<String> thoughts = Data.getThoughts();
        for (String line : thoughts) {
            outputView.append(line);
        }
        outputView.setSelection(outputView.getText().length());
    }

    private void enableAdvancedUI(boolean enable) {
        int vis = enable ? View.VISIBLE : View.INVISIBLE;
        encourageButton.setVisibility(vis);
        encourageButton.setClickable(enable);
        encourageButton.setFocusable(enable);
        discourageButton.setVisibility(vis);
        discourageButton.setClickable(enable);
        discourageButton.setFocusable(enable);
        faceImageView.setVisibility(vis);
        if (enable) faceImageView.setImageResource(R.drawable.face_neutral);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(inputView.getWindowToken(), 0);
    }

    private void showKeyboard() {
        inputView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 1);
    }

    // ------------------------------------------------------------------------
    // Menu handlers
    // ------------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.main, menu);
        updateMenuTitles(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        miNewSession = menu.findItem(R.id.new_session);
        miThoughts = menu.findItem(R.id.thought_log);
        miTips = menu.findItem(R.id.tips);
        miWordFix = menu.findItem(R.id.word_fix);
        miSetDelay = menu.findItem(R.id.setdelay);
        miSetResponse = menu.findItem(R.id.response_types);
        miEraseBrain = menu.findItem(R.id.erase_brain);
        miAdvanced = menu.findItem(R.id.advanced);
        miExit = menu.findItem(R.id.exit_app);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.advanced) {
            Util.ToggleAdvanced(item, logic);
            updateMenuTitles(menu);
            return true;
        } else if (id == R.id.erase_brain) {
            confirmErase();
            return true;
        } else if (id == R.id.exit_app) {
            confirmExit();
            return true;
        } else if (id == R.id.new_session) {
            NewSession();
            return true;
        } else if (id == R.id.response_types) {
            displayResponses();
            return true;
        } else if (id == R.id.setdelay) {
            displayDelay();
            return true;
        } else if (id == R.id.speech) {
            Util.ToggleSpeech(item, logic);
            updateMenuTitles(menu);
            return true;
        } else if (id == R.id.thought_log) {
            stopTimer();
            startThinking();
            isThoughtMode = true;
            return true;
        } else if (id == R.id.tips) {
            displayTips();
            return true;
        } else if (id == R.id.word_fix) {
            displayWordFix();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        outputView.setVisibility(View.INVISIBLE);
        inputView.setVisibility(View.INVISIBLE);
        menuButton.setVisibility(View.INVISIBLE);
        enableAdvancedUI(false);
        hideKeyboard();
        stopTimer();
        stopThinking();
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        if (!isThoughtMode && !isTipsMode && !isWordFixMode && !isDelayMode && !isResponsesMode) {
            outputView.setVisibility(View.VISIBLE);
            inputView.setVisibility(View.VISIBLE);
            menuButton.setText(R.string.menu_button);
            menuButton.setVisibility(View.VISIBLE);
            enableAdvancedUI(true);
            startTimer();
            startThinking();
        } else {
            menuButton.setText(R.string.ok_button);
            menuButton.setVisibility(View.VISIBLE);
            if (isThoughtMode || isTipsMode) {
                outputView.setVisibility(View.VISIBLE);
            }
        }
        super.onPanelClosed(featureId, menu);
    }

    private void updateMenuTitles(Menu menu) {
        MenuItem advanced = menu.findItem(R.id.advanced);
        if (advanced != null) {
            advanced.setTitle("Advanced Mode: " + logic.isAdvanced());
        }
        MenuItem speech = menu.findItem(R.id.speech);
        if (speech != null) {
            speech.setTitle("Speech: " + logic.isSpeech());
        }
    }

    // ------------------------------------------------------------------------
    // Sub‑dialogs (WordFix, Delay, Responses, Tips)
    // ------------------------------------------------------------------------

    private void displayWordFix() {
        List<WordData> words = Data.getWords();
        List<String> wordList = new ArrayList<>();
        for (WordData wd : words) wordList.add(wd.getWord());
        if (wordList.isEmpty()) {
            Toast.makeText(this, "No words to fix.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, wordList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wordFixSpinner.setAdapter(adapter);
        wordFixSpinner.setSelection(0);
        wordFixSpinner.setVisibility(View.VISIBLE);
        wordFixSpinner.setClickable(true);
        wordFixSpinner.setFocusable(true);

        if (wordFixSelection >= wordList.size()) wordFixSelection = wordList.size() - 1;
        wordFixTextView.setText(wordList.get(wordFixSelection));
        wordFixTextView.setVisibility(View.VISIBLE);
        wordFixTextView.setClickable(true);
        wordFixTextView.setFocusableInTouchMode(true);
        wordFixTextView.setFocusable(true);
        wordFixTextView.requestFocus();

        wordFixButton.setText(R.string.btn_accept);
        wordFixButton.setVisibility(View.VISIBLE);
        wordFixButton.setClickable(true);
        wordFixButton.setFocusable(true);

        stopTimer();
        isWordFixMode = true;
    }

    private void displayDelay() {
        String[] options = {"10 seconds", "20 seconds", "30 seconds", "Infinite"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wordFixSpinner.setAdapter(adapter);
        wordFixSpinner.setSelection(delaySelection);
        wordFixSpinner.setVisibility(View.VISIBLE);
        wordFixSpinner.setClickable(true);
        wordFixSpinner.setFocusable(true);

        wordFixButton.setText(R.string.btn_accept);
        wordFixButton.setVisibility(View.VISIBLE);
        wordFixButton.setClickable(true);
        wordFixButton.setFocusable(true);

        stopTimer();
        isDelayMode = true;
    }

    private void displayResponses() {
        menuButton.setText(R.string.ok_button);
        menuButton.setVisibility(View.VISIBLE);

        String[] options = {"Topic Response Method", "Condition Response Method", "Procedural Response Method"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        wordFixSpinner.setAdapter(adapter);
        wordFixSpinner.setSelection(responseSelection);
        wordFixSpinner.setVisibility(View.VISIBLE);
        wordFixSpinner.setClickable(true);
        wordFixSpinner.setFocusable(true);

        String current = "";
        if (responseSelection == 0) current = String.valueOf(logic.isTopicBased());
        else if (responseSelection == 1) current = String.valueOf(logic.isConditionBased());
        else if (responseSelection == 2) current = String.valueOf(logic.isProceduralBased());
        wordFixButton.setText(current);
        wordFixButton.setVisibility(View.VISIBLE);
        wordFixButton.setClickable(true);
        wordFixButton.setFocusable(true);

        stopTimer();
        isResponsesMode = true;
    }

    private void displayTips() {
        inputView.setVisibility(View.INVISIBLE);
        menuButton.setText(R.string.ok_button);
        enableAdvancedUI(false);

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
        outputView.setMovementMethod(LinkMovementMethod.getInstance());
        outputView.setText(tips);
        stopTimer();
        isTipsMode = true;
    }

    // ------------------------------------------------------------------------
    // Close modes
    // ------------------------------------------------------------------------

    private void closeWordFixMode() {
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

        outputView.setVisibility(View.VISIBLE);
        inputView.setVisibility(View.VISIBLE);
        menuButton.setText(R.string.menu_button);
        menuButton.setVisibility(View.VISIBLE);
        enableAdvancedUI(true);
        outputView.post(this::scrollHistory);
        showKeyboard();
        isWordFixMode = false;
        isDelayMode = false;
        isResponsesMode = false;
        startTimer();
    }

    private void closeThoughtMode() {
        inputView.setVisibility(View.VISIBLE);
        menuButton.setText(R.string.menu_button);
        menuButton.setVisibility(View.VISIBLE);
        enableAdvancedUI(true);
        outputView.post(this::scrollHistory);
        showKeyboard();
        isThoughtMode = false;
        startTimer();
    }

    private void closeTipsMode() {
        inputView.setVisibility(View.VISIBLE);
        menuButton.setText(R.string.menu_button);
        menuButton.setVisibility(View.VISIBLE);
        enableAdvancedUI(true);
        outputView.post(this::scrollHistory);
        showKeyboard();
        isTipsMode = false;
        startTimer();
        startThinking();
    }

    // ------------------------------------------------------------------------
    // Async task for WordFix
    // ------------------------------------------------------------------------

    private class WordFixTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            List<WordData> words = Data.getWords();
            String oldWord = words.get(wordFixSelection).getWord();
            String newWord = wordFixTextView.getText().toString();

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
                    if (wd.getWord().equals(oldWord)) {
                        wd.setWord(newWord);
                    }
                }
                Data.savePreWords(pre, word);

                List<WordData> pro = Data.getProWords(word);
                for (WordData wd : pro) {
                    if (wd.getWord().equals(oldWord)) {
                        wd.setWord(newWord);
                    }
                }
                Data.saveProWords(pro, word);
            }

            words.get(wordFixSelection).setWord(newWord);
            Data.saveWords(words);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            closeWordFixMode();
            Toast.makeText(MainActivity.this, "Word replaced.", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------------
    // Apply delay setting
    // ------------------------------------------------------------------------

    private void applyDelaySetting() {
        if (delaySelection == 3) {
            Data.setConfig("Infinite",
                    String.valueOf(logic.isAdvanced()),
                    String.valueOf(logic.isTopicBased()),
                    String.valueOf(logic.isConditionBased()),
                    String.valueOf(logic.isProceduralBased()),
                    String.valueOf(logic.isSpeech()));
            bl_DelayForever = true;
            int_Time = 0;
        } else {
            int seconds = (delaySelection * 10) + 10;
            Data.setConfig(seconds + " seconds",
                    String.valueOf(logic.isAdvanced()),
                    String.valueOf(logic.isTopicBased()),
                    String.valueOf(logic.isConditionBased()),
                    String.valueOf(logic.isProceduralBased()),
                    String.valueOf(logic.isSpeech()));
            bl_DelayForever = false;
            int_Time = seconds * 1000;
        }
        closeWordFixMode();
    }

    // ------------------------------------------------------------------------
    // Toggle response method
    // ------------------------------------------------------------------------

    private void toggleResponseMethod() {
        if (responseSelection == 0) {
            logic.setTopicBased(!logic.isTopicBased());
            wordFixButton.setText(String.valueOf(logic.isTopicBased()));
        } else if (responseSelection == 1) {
            logic.setConditionBased(!logic.isConditionBased());
            wordFixButton.setText(String.valueOf(logic.isConditionBased()));
        } else if (responseSelection == 2) {
            logic.setProceduralBased(!logic.isProceduralBased());
            wordFixButton.setText(String.valueOf(logic.isProceduralBased()));
        }
        String delayStr = bl_DelayForever ? "Infinite" : "30 seconds";
        Data.setConfig(delayStr,
                String.valueOf(logic.isAdvanced()),
                String.valueOf(logic.isTopicBased()),
                String.valueOf(logic.isConditionBased()),
                String.valueOf(logic.isProceduralBased()),
                String.valueOf(logic.isSpeech()));
    }

    // ------------------------------------------------------------------------
    // Confirm dialogs
    // ------------------------------------------------------------------------

    private void confirmExit() {
        stopTimer();
        new AlertDialog.Builder(this)
                .setTitle("System Message")
                .setMessage("Exit the NLP Program?")
                .setPositiveButton("Yes", (d, which) -> finishAffinity())
                .setNegativeButton("No", (d, which) -> startTimer())
                .setCancelable(false)
                .show();
    }

    private void confirmErase() {
        stopTimer();
        new AlertDialog.Builder(this)
                .setTitle("System Message")
                .setMessage("Erase all memory?")
                .setPositiveButton("Yes", (d, which) -> {
                    Util.EraseMemory(brainDir);
                    outputView.setText(EMPTY);
                    inputView.setText(EMPTY);
                    createBrainDirectories();
                    outputView.post(this::scrollHistory);
                    Toast.makeText(this, "Brain erased.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", (d, which) -> startTimer())
                .setCancelable(false)
                .show();
    }

    // ------------------------------------------------------------------------
    // AdapterView.OnItemSelectedListener
    // ------------------------------------------------------------------------

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (isWordFixMode) {
            List<WordData> words = Data.getWords();
            wordFixSelection = position;
            if (position < words.size()) {
                wordFixTextView.setText(words.get(position).getWord());
            }
        } else if (isDelayMode) {
            delaySelection = position;
        } else if (isResponsesMode) {
            responseSelection = position;
            if (position == 0) wordFixButton.setText(String.valueOf(logic.isTopicBased()));
            else if (position == 1) wordFixButton.setText(String.valueOf(logic.isConditionBased()));
            else if (position == 2) wordFixButton.setText(String.valueOf(logic.isProceduralBased()));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    // ------------------------------------------------------------------------
    // TextToSpeech.OnInitListener
    // ------------------------------------------------------------------------

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
        }
    }

    // ------------------------------------------------------------------------
    // Press-effect touch listener
    // ------------------------------------------------------------------------

    private class PressEffectTouchListener implements View.OnTouchListener {
        private final int imageRes;
        private final Runnable pressStart;
        private final Runnable pressEnd;
        private final Handler handler = new Handler();
        private final Runnable resetRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPressed) return;
                handler.removeCallbacks(this);
                faceImageView.setImageResource(R.drawable.face_neutral);
            }
        };
        private boolean isPressed = false;

        PressEffectTouchListener(int imageRes, Runnable pressStart, Runnable pressEnd) {
            this.imageRes = imageRes;
            this.pressStart = pressStart;
            this.pressEnd = pressEnd;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    faceImageView.setImageResource(imageRes);
                    isPressed = true;
                    pressStart.run();
                    handler.postDelayed(resetRunnable, 250);
                    break;
                case MotionEvent.ACTION_UP:
                    isPressed = false;
                    pressEnd.run();
                    handler.removeCallbacks(resetRunnable);
                    handler.postDelayed(resetRunnable, 250);
                    v.performClick();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    isPressed = false;
                    pressEnd.run();
                    handler.removeCallbacks(resetRunnable);
                    faceImageView.setImageResource(R.drawable.face_neutral);
                    break;
            }
            return true;
        }
    }

    // ------------------------------------------------------------------------
    // Load saved delay from config
    // ------------------------------------------------------------------------

    private void loadDelayFromConfig() {
        String delayStr = Data.getDelay();
        if (delayStr == null || delayStr.isEmpty()) {
            return;
        }
        if (delayStr.equalsIgnoreCase("Infinite")) {
            bl_DelayForever = true;
            int_Time = 0;
        } else {
            bl_DelayForever = false;
            try {
                String numberPart = delayStr.replace(" seconds", "").trim();
                int seconds = Integer.parseInt(numberPart);
                int_Time = seconds * 1000;
            } catch (NumberFormatException e) {
                int_Time = 10000;
            }
        }
    }
}
