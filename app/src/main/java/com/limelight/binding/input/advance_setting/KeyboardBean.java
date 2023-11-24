package com.limelight.binding.input.advance_setting;

public class KeyboardBean {

    private int value;
    private int valueUp;
    private int valueDown;
    private int valueLeft;
    private int valueRight;
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
    private int layer;


    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getValueUp() {
        return valueUp;
    }

    public void setValueUp(int valueUp) {
        this.valueUp = valueUp;
    }

    public int getValueDown() {
        return valueDown;
    }

    public void setValueDown(int valueDown) {
        this.valueDown = valueDown;
    }

    public int getValueLeft() {
        return valueLeft;
    }

    public void setValueLeft(int valueLeft) {
        this.valueLeft = valueLeft;
    }

    public int getValueRight() {
        return valueRight;
    }

    public void setValueRight(int valueRight) {
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

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    @Override
    public String toString() {
        return "KeyboardBean{" +
                "value=" + value +
                ", valueUp=" + valueUp +
                ", valueDown=" + valueDown +
                ", valueLeft=" + valueLeft +
                ", valueRight=" + valueRight +
                ", type=" + type +
                ", positionX=" + positionX +
                ", positionY=" + positionY +
                ", size=" + size +
                ", opacity=" + opacity +
                ", layer=" + layer +
                '}';
    }
}
