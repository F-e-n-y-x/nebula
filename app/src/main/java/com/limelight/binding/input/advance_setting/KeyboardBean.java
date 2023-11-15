package com.limelight.binding.input.advance_setting;

public class KeyboardBean {

    private String value;
    private String valueUp;
    private String valueDown;
    private String valueLeft;
    private String valueRight;
    /*
    * type有三个值
    * 0:BUTTON
    * 1:SWITCH
    * 2:PAD
    * */
    private int type;
    private int positionX;
    private int positionY;
    private int size;
    private int opacity;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValueUp() {
        return valueUp;
    }

    public void setValueUp(String valueUp) {
        this.valueUp = valueUp;
    }

    public String getValueDown() {
        return valueDown;
    }

    public void setValueDown(String valueDown) {
        this.valueDown = valueDown;
    }

    public String getValueLeft() {
        return valueLeft;
    }

    public void setValueLeft(String valueLeft) {
        this.valueLeft = valueLeft;
    }

    public String getValueRight() {
        return valueRight;
    }

    public void setValueRight(String valueRight) {
        this.valueRight = valueRight;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getPositionX() {
        return positionX;
    }

    public void setPositionX(int positionX) {
        this.positionX = positionX;
    }

    public int getPositionY() {
        return positionY;
    }

    public void setPositionY(int positionY) {
        this.positionY = positionY;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getOpacity() {
        return opacity;
    }

    public void setOpacity(int opacity) {
        this.opacity = opacity;
    }
}
