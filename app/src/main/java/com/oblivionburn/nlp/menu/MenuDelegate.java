package com.oblivionburn.nlp.menu;

import android.view.Menu;
import android.view.MenuItem;

import com.oblivionburn.nlp.R;
import com.oblivionburn.nlp.engine.ConversationEngine;
import com.oblivionburn.nlp.engine.Logic;
import com.oblivionburn.nlp.engine.Util;
import com.oblivionburn.nlp.ui.UIManager;

public class MenuDelegate {

    private final MenuActions actions;
    private final UIManager ui;
    private final Logic logic;
    private final ConversationEngine engine;

    private MenuItem miAdvanced;
    private MenuItem miSpeech;

    public MenuDelegate(MenuActions actions, UIManager ui, Logic logic, ConversationEngine engine) {
        this.actions = actions;
        this.ui = ui;
        this.logic = logic;
        this.engine = engine;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        actions.getMenuInflater().inflate(R.menu.main, menu);
        miAdvanced = menu.findItem(R.id.advanced);
        miSpeech = menu.findItem(R.id.speech);
        updateMenuTitles();
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        miAdvanced = menu.findItem(R.id.advanced);
        miSpeech = menu.findItem(R.id.speech);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.advanced) {
            Util.ToggleAdvanced(item, logic);
            updateMenuTitles();
            return true;
        } else if (id == R.id.erase_brain) {
            actions.confirmErase();
            return true;
        } else if (id == R.id.exit_app) {
            actions.confirmExit();
            return true;
        } else if (id == R.id.new_session) {
            actions.newSession();
            return true;
        } else if (id == R.id.response_types) {
            actions.displayResponses();
            return true;
        } else if (id == R.id.setdelay) {
            actions.displayDelay();
            return true;
        } else if (id == R.id.speech) {
            Util.ToggleSpeech(item, logic);
            updateMenuTitles();
            return true;
        } else if (id == R.id.thought_log) {
            engine.stopTimer();
            engine.startThinking();
            actions.setThoughtMode(true);
            return true;
        } else if (id == R.id.tips) {
            actions.displayTips();
            return true;
        } else if (id == R.id.word_fix) {
            actions.displayWordFix();
            return true;
        }
        return false;
    }

    public boolean onMenuOpened(int featureId, Menu menu) {
        ui.setOutputVisible(false);
        ui.setInputVisible(false);
        ui.setMenuButton("", false);
        ui.setAdvancedUIEnabled(false);
        ui.hideKeyboard();
        engine.stopTimer();
        engine.stopThinking();
        return true;
    }

    public void onPanelClosed(int featureId, Menu menu) {
        ui.setOutputVisible(true);
        ui.setInputVisible(true);
        ui.setMenuButton(actions.getString(R.string.menu_button), true);
        ui.setAdvancedUIEnabled(true);
        engine.startTimer();
        engine.startThinking();
        ui.showKeyboard();
        if (!actions.isThoughtMode() && !actions.isTipsMode()) {
            actions.scrollHistory();
        }
        actions.invalidateOptionsMenu();
    }

    private void updateMenuTitles() {
        if (miAdvanced != null) {
            miAdvanced.setTitle("Advanced Mode: " + logic.isAdvanced());
        }
        if (miSpeech != null) {
            miSpeech.setTitle("Speech: " + logic.isSpeech());
        }
    }
}