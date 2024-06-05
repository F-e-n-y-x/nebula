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
        ElementController.SendEventHandler topSender = controller.getSendEventHandler(elementBean.getTypeAttributes().get("top_value"));
        ElementController.SendEventHandler downSender = controller.getSendEventHandler(elementBean.getTypeAttributes().get("down_value"));
        ElementController.SendEventHandler leftSender = controller.getSendEventHandler(elementBean.getTypeAttributes().get("left_value"));
        ElementController.SendEventHandler rightSender = controller.getSendEventHandler(elementBean.getTypeAttributes().get("right_value"));

        addDigitalPadListener(new DigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                int directionChange = lastDirection ^ direction;
                if ((directionChange & DIGITAL_PAD_DIRECTION_LEFT) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                        leftSender.sendEvent(true);
                    }
                    else {
                        leftSender.sendEvent(false);
                    }
                }
                if ((directionChange & DIGITAL_PAD_DIRECTION_RIGHT) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                        rightSender.sendEvent(true);
                    }
                    else {
                        rightSender.sendEvent(false);
                    }
                }
                if ((directionChange & DIGITAL_PAD_DIRECTION_UP) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_UP) != 0) {
                        topSender.sendEvent(true);
                    }
                    else {
                        topSender.sendEvent(false);
                    }
                }
                if ((directionChange & DIGITAL_PAD_DIRECTION_DOWN) != 0 ){
                    if ((direction & DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                        downSender.sendEvent(true);
                    }
                    else {
                        downSender.sendEvent(false);
                    }
                }
                lastDirection = direction;
            }
        });

    }
}
