package com.oblivionburn.nlp.menu;

import android.view.MenuInflater;

public interface MenuActions {
    void confirmErase();
    void confirmExit();
    void newSession();
    void displayResponses();
    void displayDelay();
    void displayWordFix();
    void displayTips();
    void setThoughtMode(boolean value);
    void setWordFixMode(boolean value);
    void setDelayMode(boolean value);
    void setResponsesMode(boolean value);
    void setTipsMode(boolean value);
    boolean isThoughtMode();
    boolean isTipsMode();
    void scrollHistory();
    void invalidateOptionsMenu();
    String getString(int resId);
    MenuInflater getMenuInflater();
}