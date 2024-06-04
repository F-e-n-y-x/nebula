package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

public class DigitalKPad extends DigitalPad {

    //加上这个防止一些无用的按键多次触发，发送大量数据，导致卡顿
    private int lastDirection = 0;
    public DigitalKPad(ElementController controller, ElementBean elementBean, Context context) {
        super(controller, elementBean, context);
        int topValue = Integer.parseInt(elementBean.getTypeAttributes().get("top_value").substring(1));
        int downValue = Integer.parseInt(elementBean.getTypeAttributes().get("down_value").substring(1));
        int leftValue = Integer.parseInt(elementBean.getTypeAttributes().get("left_value").substring(1));
        int rightValue = Integer.parseInt(elementBean.getTypeAttributes().get("right_value").substring(1));

        addDigitalPadListener(new DigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                int directionChange = lastDirection ^ direction;
                if ((directionChange & DIGITAL_PAD_DIRECTION_LEFT) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,leftValue));
                    }
                    else {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,leftValue));
                    }
                }
                if ((directionChange & DIGITAL_PAD_DIRECTION_RIGHT) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,rightValue));
                    }
                    else {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,rightValue));
                    }
                }
                if ((directionChange & DIGITAL_PAD_DIRECTION_UP) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_UP) != 0) {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,topValue));
                    }
                    else {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,topValue));
                    }
                }
                if ((directionChange & DIGITAL_PAD_DIRECTION_DOWN) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,downValue));
                    }
                    else {
                        controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,downValue));
                    }
                }
                lastDirection = direction;
            }
        });

    }
}
