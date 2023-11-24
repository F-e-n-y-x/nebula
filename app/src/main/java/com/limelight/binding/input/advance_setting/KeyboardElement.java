package com.limelight.binding.input.advance_setting;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public abstract class KeyboardElement extends View {
    private int normalColor = 0xF0888888;
    protected int pressedColor = 0xF00000FF;
    private int configEditColor = 0xF0FF00FF;
    private int configDeleteColor = 0xF0FF0000;
    private int configSelectedColor = 0xF000FF00;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    private final Paint paint = new Paint();

    private String elementId;
    private KeyboardBean keyboardBean;
    protected final KeyboardController keyboardController;

    private enum Mode {
        Normal,
        Delete,
        Edit
    }

    private Mode currentMode = Mode.Normal;


    public KeyboardElement(KeyboardController keyboardController, KeyboardBean keyboardBean, String elementId, Context context){
        super(context);
        this.keyboardController = keyboardController;
        this.elementId = elementId;
        this.keyboardBean = keyboardBean;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = Math.max(newPos_x, -(keyboardBean.getSize() / 2));
        layoutParams.topMargin = Math.max(newPos_y, -(keyboardBean.getSize() / 2));
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        keyboardBean.setPositionX(newPos_x);
        keyboardBean.setPositionY(newPos_y);

        requestLayout();
    }

    protected void resizeElement(int size) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        keyboardBean.setSize(size);
        layoutParams.height = Math.max(keyboardBean.getSize(), 20);
        layoutParams.width = Math.max(keyboardBean.getSize(), 20);

        requestLayout();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);
        super.onDraw(canvas);
    }

    /*
    protected void actionShowNormalColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {}

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                normalColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }

    protected void actionShowPressedColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                pressedColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }
    */

    protected void actionEnableEdit() {
        currentMode = Mode.Edit;
    }

    protected void actionEnableDelete() {
        currentMode = Mode.Delete;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        System.out.println("wg_debug:getcolor");
        if (keyboardController.getCurrentEditElement() == this){
            return configSelectedColor;
        } else if (keyboardController.getControllerMode() == KeyboardController.ControllerMode.EditButtons)
            return configEditColor;
        else if (keyboardController.getControllerMode() == KeyboardController.ControllerMode.DeleteButtons)
            return configDeleteColor;
        else
            return normalColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    protected void showConfigurationDialog() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(getContext());

        alertBuilder.setTitle("Configuration");

        CharSequence functions[] = new CharSequence[]{
                "Edit",
                "Delete",
                /*election
                "Set n
                Disable color sormal color",
                "Set pressed color",
                */
                "Cancel"
        };

        alertBuilder.setItems(functions, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: { // Edit
                        actionEnableEdit();
                        break;
                    }
                    case 1: { // delete
                        actionEnableDelete();
                        break;
                    }
                /*
                case 2: { // set default color
                    actionShowNormalColorChooser();
                    break;
                }
                case 3: { // set pressed color
                    actionShowPressedColorChooser();
                    break;
                }
                */
                    default: { // cancel
                        actionCancel();
                        break;
                    }
                }
            }
        });
        AlertDialog alert = alertBuilder.create();
        // show menu
        alert.show();
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

        if (keyboardController.getControllerMode() == KeyboardController.ControllerMode.Active) {
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                position_pressed_x = event.getX();
                position_pressed_y = event.getY();

                if (keyboardController.getControllerMode() == KeyboardController.ControllerMode.EditButtons)
                    actionEnableEdit();
                else if (keyboardController.getControllerMode() == KeyboardController.ControllerMode.DeleteButtons)
                    actionEnableDelete();
                if (currentMode == Mode.Edit){
                    keyboardController.elementsInvalidate();
                    keyboardController.setCurrentEditElement(this);
                    keyboardController.getSizeSeekbar().setProgress(keyboardBean.getSize());
                }

                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                switch (currentMode) {
                    case Edit: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) event.getX(),
                                (int) event.getY());
                        break;
                    }
                    case Delete: {
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                actionCancel();
                return true;
            }
            default: {
            }
        }
        return true;
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
