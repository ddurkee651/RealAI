package com.oblivionburn.nlp;

import android.content.Context;
import android.support.v7.widget.EditText;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;
import java.lang.reflect.Field;

/* JADX INFO: loaded from: classes.dex */
public class LiteText extends EditText {
    private final Context context;

    boolean canPaste() {
        return false;
    }

    @Override // android.widget.TextView
    public boolean isSuggestionsEnabled() {
        return false;
    }

    @Override // android.widget.TextView, android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (motionEvent == null) {
            return false;
        }
        int action = motionEvent.getAction();
        if (action == 0) {
            setInsertionDisabled();
        } else if (action == 1) {
            performClick();
        }
        return super.onTouchEvent(motionEvent);
    }

    @Override // android.view.View
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
        } catch (Exception unused) {
        }
    }

    private class ActionModeCallbackInterceptor implements ActionMode.Callback {
        private final String TAG;

        @Override // android.view.ActionMode.Callback
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            return false;
        }

        @Override // android.view.ActionMode.Callback
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override // android.view.ActionMode.Callback
        public void onDestroyActionMode(ActionMode actionMode) {
        }

        @Override // android.view.ActionMode.Callback
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        private ActionModeCallbackInterceptor() {
            this.TAG = LiteText.class.getSimpleName();
        }
    }
}
