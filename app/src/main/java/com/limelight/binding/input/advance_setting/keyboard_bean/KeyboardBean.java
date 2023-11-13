package com.limelight.binding.input.advance_setting.keyboard_bean;

public class KeyboardBean {

    enum Type{
        BUTTON,
        SWITCH,
        PAD
    }
    private String value;
    private String valueUp;
    private String valueDown;
    private String valueLeft;
    private String valueRight;
    private Type type;
    private int positionX;
    private int positionY;
    private int size;
    private int optical;

}
