package com.limelight.binding.input.advance_setting;

import java.util.Map;

public class ElementBean {

    private String name;
    /*
    * type:
    * 0:BUTTON
    * 1:SWITCH
    * 2:PAD
    * 3:M-BUTTON
    * 4:K-STICK
    * 5:K-ISTICK
    * 6:G-STICK
    * 7:G-ISTICK
    * */
    private int type;
    /*
    * typeAttributes are unique attributes of the element
    * typeAttributes format:
    * "attribute1^value1!attribute2^#value21,value22,value23$!"
    *  ^ = : , ! = ; , # = [ , $ = ]
    * */
    private Map<String,String> typeAttributes;
    private int positionX;
    private int positionY;
    private int width;
    private int height;
    private int opacity;
    private int color;
    private int layer;
    /*
     * otherAttributes are an extension of the basic attributes
     * otherAttributes format:
     * "attribute1^value1!attribute2^#value21,value22,value23$!"
     *  ^ = : , ! = ; , # = [ , $ = ]
     * */
    private Map<String,String> otherAttributes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Map<String,String> getTypeAttributes() {
        return typeAttributes;
    }

    public void setTypeAttributes(Map<String,String> typeAttributes) {
        this.typeAttributes = typeAttributes;
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

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getOpacity() {
        return opacity;
    }

    public void setOpacity(int opacity) {
        this.opacity = opacity;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public Map<String,String> getOtherAttributes() {
        return otherAttributes;
    }

    public void setOtherAttributes(Map<String,String> otherAttributes) {
        this.otherAttributes = otherAttributes;
    }

    @Override
    public String toString() {
        return "ElementBean{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", typeAttributes='" + typeAttributes + '\'' +
                ", positionX=" + positionX +
                ", positionY=" + positionY +
                ", sizeX=" + width +
                ", sizeY=" + height +
                ", opacity=" + opacity +
                ", color=" + color +
                ", layer=" + layer +
                ", otherAttributes='" + otherAttributes + '\'' +
                '}';
    }
}
