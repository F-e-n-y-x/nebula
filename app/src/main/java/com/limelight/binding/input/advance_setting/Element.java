package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public abstract class Element extends View {
    private int normalColor = 0xF0888888;
    protected int pressedColor = 0xF00000FF;
    private int configEditColor = 0xF0FF00FF;
    private int configDeleteColor = 0xF0FF0000;
    private int configSelectedColor = 0xF000FF00;
    private int defaultColor = normalColor;
    private final Paint paint = new Paint();

    private String elementId;
    private ElementBean elementBean;
    protected final ElementController elementController;



    public Element(ElementController elementController, ElementBean elementBean, Context context){
        super(context);
        this.elementController = elementController;
        this.elementId = elementBean.getName();
        this.elementBean = elementBean;
    }

    public ElementBean getKeyboardBean() {
        return elementBean;
    }

    public String getElementId() {
        return elementId;
    }

    protected void moveElement(int x,int y) {

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.leftMargin = x;
        layoutParams.topMargin = y;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
    }

    protected void resizeElement(int width,int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;

        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);
        super.onDraw(canvas);
    }


    protected int getDefaultColor() {
        return defaultColor;
    }

    protected void setDefaultColor(int defaultColor) {
        this.defaultColor = defaultColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.getActionIndex() != 0) {
            return true;
        }

        return onElementTouchEvent(event);
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static void _DBG(String text) {
        System.out.println(text);
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }


    public void setOpacity(int opacity) {
        int hexOpacity = opacity * 255 / 100;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);

        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }
}
