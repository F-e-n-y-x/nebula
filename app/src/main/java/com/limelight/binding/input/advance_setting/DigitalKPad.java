package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

public class DigitalKPad extends Element {

    private String value;

    public final static int DIGITAL_PAD_DIRECTION_NO_DIRECTION = 0;
    int direction = DIGITAL_PAD_DIRECTION_NO_DIRECTION;
    public final static int DIGITAL_PAD_DIRECTION_LEFT = 1;
    public final static int DIGITAL_PAD_DIRECTION_UP = 2;
    public final static int DIGITAL_PAD_DIRECTION_RIGHT = 4;
    public final static int DIGITAL_PAD_DIRECTION_DOWN = 8;
    List<DigitalKPadListener> listeners = new ArrayList<>();

    private static final int DPAD_MARGIN = 5;

    private final Paint paint = new Paint();

    public DigitalKPad(ElementController controller, ElementBean elementBean, Context context) {
        super(controller, elementBean, context);
        int topValue = Integer.parseInt(elementBean.getTypeAttributes().get("top_value").substring(1));
        int downValue = Integer.parseInt(elementBean.getTypeAttributes().get("down_value").substring(1));
        int leftValue = Integer.parseInt(elementBean.getTypeAttributes().get("left_value").substring(1));
        int rightValue = Integer.parseInt(elementBean.getTypeAttributes().get("right_value").substring(1));

        addDigitalKPadListener(new DigitalKPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                if ((direction & DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,leftValue));
                }
                else {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,leftValue));
                }
                if ((direction & DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,rightValue));
                }
                else {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,rightValue));
                }
                if ((direction & DIGITAL_PAD_DIRECTION_UP) != 0) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,topValue));
                }
                else {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,topValue));
                }
                if ((direction & DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,downValue));
                }
                else {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,downValue));
                }
            }
        });

    }

    public void addDigitalKPadListener(DigitalKPadListener listener) {
        listeners.add(listener);
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getCorrectWidth(), 20));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        if (direction == DIGITAL_PAD_DIRECTION_NO_DIRECTION) {
            // draw no direction rect
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(getNormalColor());
            canvas.drawRect(
                    getPercent(getWidth(), 36), getPercent(getHeight(), 36),
                    getPercent(getWidth(), 63), getPercent(getHeight(), 63),
                    paint
            );
        }

        // draw left rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 ? pressedColor : getNormalColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                paint
        );


        // draw up rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_UP) > 0 ? pressedColor : getNormalColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                paint
        );

        // draw right rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 ? pressedColor : getNormalColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 66), getPercent(getHeight(), 33),
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 66),
                paint
        );

        // draw down rect
        paint.setColor(
                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 ? pressedColor : getNormalColor());
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(
                getPercent(getWidth(), 33), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight() - (paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw left up line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_UP) > 0
                ) ? pressedColor : getNormalColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 33),
                getPercent(getWidth(), 33), paint.getStrokeWidth()+DPAD_MARGIN,
                paint
        );

        // draw up right line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_UP) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0
                ) ? pressedColor : getNormalColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 66), paint.getStrokeWidth()+DPAD_MARGIN,
                getWidth() - (paint.getStrokeWidth()+DPAD_MARGIN), getPercent(getHeight(), 33),
                paint
        );

        // draw right down line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_RIGHT) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0
                ) ? pressedColor : getNormalColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getWidth()-paint.getStrokeWidth(), getPercent(getHeight(), 66),
                getPercent(getWidth(), 66), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint
        );

        // draw down left line
        paint.setColor((
                        (direction & DIGITAL_PAD_DIRECTION_DOWN) > 0 &&
                                (direction & DIGITAL_PAD_DIRECTION_LEFT) > 0
                ) ? pressedColor : getNormalColor()
        );
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(
                getPercent(getWidth(), 33), getHeight()-(paint.getStrokeWidth()+DPAD_MARGIN),
                paint.getStrokeWidth()+DPAD_MARGIN, getPercent(getHeight(), 66),
                paint
        );
    }

    private void newDirectionCallback(int direction) {
        _DBG("direction: " + direction);

        // notify listeners
        for (DigitalKPadListener listener : listeners) {
            listener.onDirectionChange(direction);
        }
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        // get masked (not specific to a pointer) action
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                direction = 0;

                if (event.getX() < getPercent(getWidth(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_LEFT;
                }
                if (event.getX() > getPercent(getWidth(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_RIGHT;
                }
                if (event.getY() > getPercent(getHeight(), 66)) {
                    direction |= DIGITAL_PAD_DIRECTION_DOWN;
                }
                if (event.getY() < getPercent(getHeight(), 33)) {
                    direction |= DIGITAL_PAD_DIRECTION_UP;
                }
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                direction = 0;
                newDirectionCallback(direction);
                invalidate();

                return true;
            }
            default: {
            }
        }

        return true;
    }

    public interface DigitalKPadListener {
        void onDirectionChange(int direction);
    }
}
