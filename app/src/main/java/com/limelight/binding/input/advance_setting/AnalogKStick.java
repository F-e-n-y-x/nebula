package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.view.KeyEvent;

public class AnalogKStick extends AnalogStick {

    boolean upIsPressed = false;
    boolean downIsPressed = false;
    boolean leftIsPressed = false;
    boolean rightIsPressed = false;
    public AnalogKStick(ElementController controller, ElementBean elementBean, Context context){
        super(controller, elementBean, context);

        int topValue = Integer.parseInt(elementBean.getTypeAttributes().get("top_value").substring(1));
        int downValue = Integer.parseInt(elementBean.getTypeAttributes().get("down_value").substring(1));
        int leftValue = Integer.parseInt(elementBean.getTypeAttributes().get("left_value").substring(1));
        int rightValue = Integer.parseInt(elementBean.getTypeAttributes().get("right_value").substring(1));
        int middleValue = Integer.parseInt(elementBean.getTypeAttributes().get("middle_value").substring(1));

        addAnalogStickListener(new AnalogStick.AnalogStickListener() {
            @Override
            public void onMovement(float x, float y) {
                if (x < -0.4 && !leftIsPressed){
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,leftValue));
                    leftIsPressed = true;
                } else if (x > -0.4 && leftIsPressed) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,leftValue));
                    leftIsPressed = false;
                }
                if (x > 0.4 && !rightIsPressed){
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,rightValue));
                    rightIsPressed = true;
                } else if (x < 0.4 && rightIsPressed) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,rightValue));
                    rightIsPressed = false;
                }
                if (y < -0.4 && !downIsPressed){
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,downValue));
                    downIsPressed = true;
                } else if (y > -0.4 && downIsPressed) {
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,downValue));
                    downIsPressed = false;
                }
                if (y > 0.4 && !upIsPressed){
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,topValue));
                    upIsPressed = true;
                } else if(y < 0.4 && upIsPressed){
                    controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,topValue));
                    upIsPressed = false;
                }

            }

            @Override
            public void onClick() {
            }

            @Override
            public void onDoubleClick() {
                controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,middleValue));
            }

            @Override
            public void onRevoke() {
                controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,middleValue));
                controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,topValue));
                controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,downValue));
                controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,leftValue));
                controller.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,rightValue));

            }
        });

    }



}
