package com.oblivionburn.nlp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;   // <-- use AndroidX

import java.lang.reflect.Field;

public class LiteText extends AppCompatEditText {   // <-- extend AndroidX version
    private final Context context;

    boolean canPaste() { return false; }

    @Override
    public boolean isSuggestionsEnabled() { return false; }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (motionEvent == null) return false;
        int action = motionEvent.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            setInsertionDisabled();
        } else if (action == MotionEvent.ACTION_UP) {
            performClick();
        }
        return super.onTouchEvent(motionEvent);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public LiteText(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public LiteText(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.context = context;
        init();
    }

    public LiteText(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.context = context;
        init();
    }

    private void init() {
        setCustomSelectionActionModeCallback(new ActionModeCallbackInterceptor());
        setLongClickable(false);
    }

    private void setInsertionDisabled() {
        try {
            Field declaredField = TextView.class.getDeclaredField("mEditor");
            declaredField.setAccessible(true);
            Object obj = declaredField.get(this);
            Field declaredField2 = Class.forName("android.widget.Editor").getDeclaredField("mInsertionControllerEnabled");
            declaredField2.setAccessible(true);
            declaredField2.set(obj, false);
        } catch (Exception ignored) {
        }
    }

    private class ActionModeCallbackInterceptor implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }

        @Override
        public void onDestroyActionMode(ActionMode mode) {}
    }
}
