package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital Switch on screen element. It is used to get click and double click user input.
 */
public class DigitalSwitch extends Element {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalSwitchListener {

        /**
         * onClick event will be fired on switch click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on switch long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on switch unpress.
         */
        void onRelease();
    }

    private List<DigitalSwitchListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 3000;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private DigitalSwitch movingSwitch = null;
    private int currentAction = KeyEvent.ACTION_UP;

    public int getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(int currentAction) {
        this.currentAction = currentAction;
    }

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, DigitalSwitch movingSwitch) {
        // check if the movement happened in the same layer
        if (movingSwitch.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the switch
        if ((this.movingSwitch == null || movingSwitch == this.movingSwitch)
                && this.inRange(x, y)) {
            // set switch pressed state depending on moving switch pressed state
            if (this.isPressed() != movingSwitch.isPressed()) {
                this.setPressed(movingSwitch.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement switch
        // is the saved moving switch
        else if (movingSwitch == this.movingSwitch) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving switch and emit click event
                this.movingSwitch = movingSwitch;
                onClickCallback();
            } else {
                // no longer pressed reset moving switch and emit release event
                this.movingSwitch = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllSwitches(float x, float y) {
        for (Element element : elementController.getElements()) {
            if (element != this && element instanceof DigitalSwitch) {
                ((DigitalSwitch) element).checkMovement(x, y, this);
            }
        }
    }

    public DigitalSwitch(ElementController controller, ElementBean elementBean, String elementId, int layer, Context context) {
        super(controller, elementBean, elementId,context);
        this.layer = layer;
    }

    public void addDigitalSwitchListener(DigitalSwitch.DigitalSwitchListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 25));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());
        if (isPressed() || currentAction == KeyEvent.ACTION_DOWN){
            paint.setColor(pressedColor);
        } else {
            paint.setColor(getDefaultColor());
        }

        paint.setStyle(Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        canvas.drawRect(rect, paint);

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            canvas.drawText(text, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }
    }

    private void onClickCallback() {
        _DBG("clicked");
        // notify listeners
        for (DigitalSwitch.DigitalSwitchListener listener : listeners) {
            listener.onClick();
        }

        elementController.getHandler().removeCallbacks(longClickRunnable);
        elementController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalSwitch.DigitalSwitchListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalSwitch.DigitalSwitchListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        elementController.getHandler().removeCallbacks(longClickRunnable);
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingSwitch = null;
                setPressed(true);
                onClickCallback();

                invalidate();

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                checkMovementForAllSwitches(x, y);

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllSwitches(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }
}

