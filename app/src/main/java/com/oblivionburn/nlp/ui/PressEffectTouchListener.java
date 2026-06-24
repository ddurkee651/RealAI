package com.oblivionburn.nlp.ui;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.oblivionburn.nlp.R;

public class PressEffectTouchListener implements View.OnTouchListener {
    private final int imageRes;
    private final Runnable pressStart;
    private final Runnable pressEnd;
    private final ImageView faceImageView;
    private final Handler handler = new Handler();
    private boolean isPressed = false;

    public PressEffectTouchListener(int imageRes, Runnable pressStart, Runnable pressEnd,
                                    ImageView faceImageView) {
        this.imageRes = imageRes;
        this.pressStart = pressStart;
        this.pressEnd = pressEnd;
        this.faceImageView = faceImageView;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                faceImageView.setImageResource(imageRes);
                isPressed = true;
                pressStart.run();
                handler.postDelayed(() -> {
                    if (!isPressed) faceImageView.setImageResource(R.drawable.face_neutral);
                }, 250);
                break;
            case MotionEvent.ACTION_UP:
                isPressed = false;
                pressEnd.run();
                handler.postDelayed(() -> faceImageView.setImageResource(R.drawable.face_neutral), 250);
                v.performClick();
                break;
            case MotionEvent.ACTION_CANCEL:
                isPressed = false;
                pressEnd.run();
                faceImageView.setImageResource(R.drawable.face_neutral);
                break;
        }
        return true;
    }
}