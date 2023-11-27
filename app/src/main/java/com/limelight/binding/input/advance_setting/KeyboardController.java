package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.Game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardController {

    public enum ControllerMode {
        Active,
        EditButtons,
        DeleteButtons
    }

    private final Context context;
    private final Game game;
    private final Handler handler;

    ControllerMode currentMode = ControllerMode.Active;

    private final List<KeyboardElement> elements = new ArrayList<>();
    private KeyboardElementPreference keyboardElementPreference;
    private KeyboardLayoutPreference keyboardLayoutPreference;
    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();
    private FrameLayout elementsLayout;
    private AdvanceSettingController advanceSettingController;
    private KeyboardElement currentSelectedElement;
    public KeyboardController(FrameLayout layout, AdvanceSettingController advanceSettingController, final Context context) {
        this.elementsLayout = layout;
        this.context = context;
        this.game = (Game) context;
        this.advanceSettingController = advanceSettingController;

        handler = new Handler(Looper.getMainLooper());
        keyboardLayoutPreference = new KeyboardLayoutPreference(context);
    }

    public void saveLayout(){
        keyboardElementPreference.saveElements();
    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (KeyboardElement element : elements) {
            element.setVisibility(View.INVISIBLE);
        }
    }

    public void show() {
        for (KeyboardElement element : elements) {
            element.setVisibility(View.VISIBLE);
        }
    }


    public boolean addElement(String elementId, KeyboardBean keyboardBean){
        if (keyboardElementPreference.addElement(elementId,keyboardBean) != 0){
            return false;
        }
        createElement(elementId,keyboardBean);
        return true;
    }
    public void removeSelectedElement(){
        elementsLayout.removeView(currentSelectedElement);
        elements.remove(currentSelectedElement);
        keyboardElementPreference.deleteElement(currentSelectedElement.getElementId());
        currentSelectedElement = null;
    }
    public void setOpacity(int opacity) {
        for (KeyboardElement element : elements) {
            element.setOpacity(opacity);
        }
    }

    public void loadCurrentLayout(){
        String currentLayout = advanceSettingController.getAdvanceSettingPreference().getCurrentLayoutName();
        String currentLayoutId = keyboardLayoutPreference.getLayoutId(currentLayout);
        keyboardElementPreference = new KeyboardElementPreference(currentLayoutId,context);
        Map<String,KeyboardBean> elementsMap = keyboardElementPreference.getElements();
        for (Map.Entry<String,KeyboardBean> entry: elementsMap.entrySet()){
            createElement(entry.getKey(),entry.getValue());
        }
    }

    private void createElement(String elementId, KeyboardBean keyboardBean) {
        KeyboardElement keyboardElement = null;
        switch (keyboardBean.getType()){
            case 0:
                keyboardElement = createButton(elementId,keyboardBean,keyboardBean.getLayer());
                break;
            case 1:
                keyboardElement = createSwitch(elementId,keyboardBean,keyboardBean.getLayer());
                break;
            case 2:
                keyboardElement = createPad(elementId,keyboardBean);
                break;
        }
        elements.add(keyboardElement);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(keyboardBean.getSize(), keyboardBean.getSize());
        layoutParams.setMargins(keyboardBean.getPositionX(), keyboardBean.getPositionY(), 0, 0);

        elementsLayout.addView(keyboardElement, layoutParams);
    }

    private KeyboardElement createPad(String elementId, KeyboardBean keyboardBean){
        KeyboardDigitalPad keyboardDigitalPad = new KeyboardDigitalPad(this, keyboardBean, elementId, context);
        keyboardDigitalPad.addDigitalPadListener(new KeyboardDigitalPad.DigitalPadListener() {
            @Override
            public void onDirectionChange(int direction) {
                int keyCodeLeft = keyboardBean.getValueLeft();
                int keyCodeRight = keyboardBean.getValueRight();
                int keyCodeUp = keyboardBean.getValueUp();
                int keyCodeDown = keyboardBean.getValueDown();
                if ((direction & KeyboardDigitalPad.DIGITAL_PAD_DIRECTION_LEFT) != 0) {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCodeLeft));
                }
                else {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCodeLeft));
                }
                if ((direction & KeyboardDigitalPad.DIGITAL_PAD_DIRECTION_RIGHT) != 0) {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCodeRight));
                }
                else {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCodeRight));
                }
                if ((direction & KeyboardDigitalPad.DIGITAL_PAD_DIRECTION_UP) != 0) {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCodeUp));
                }
                else {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCodeUp));
                }
                if ((direction & KeyboardDigitalPad.DIGITAL_PAD_DIRECTION_DOWN) != 0) {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCodeDown));
                }
                else {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCodeDown));
                }
            }
        });
        return keyboardDigitalPad;
    }

    private KeyboardElement createButton(String elementId, KeyboardBean keyboardBean, int layer){
        KeyboardDigitalButton keyboardDigitalButton = new KeyboardDigitalButton(this, keyboardBean, elementId, layer, context);
        keyboardDigitalButton.setText(elementId);
        keyboardDigitalButton.addDigitalButtonListener(new KeyboardDigitalButton.DigitalButtonListener() {
            @Override
            public void onClick() {
                int keyCode = keyboardBean.getValue();
                sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCode));
            }

            @Override
            public void onLongClick() {

            }

            @Override
            public void onRelease() {
                int keyCode = keyboardBean.getValue();
                sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCode));
            }
        });
        return keyboardDigitalButton;
    }

    private KeyboardElement createSwitch(String elementId, KeyboardBean keyboardBean, int layer){
        KeyboardDigitalSwitch keyboardDigitalSwitch = new KeyboardDigitalSwitch(this, keyboardBean, elementId, layer, context);
        keyboardDigitalSwitch.setText(elementId);
        keyboardDigitalSwitch.addDigitalSwitchListener(new KeyboardDigitalSwitch.DigitalSwitchListener() {
            @Override
            public void onClick() {
                int keyCode = keyboardBean.getValue();
                if (keyboardDigitalSwitch.getCurrentAction() == KeyEvent.ACTION_UP){
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,keyCode));
                    keyboardDigitalSwitch.setCurrentAction(KeyEvent.ACTION_DOWN);
                } else {
                    sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCode));
                    keyboardDigitalSwitch.setCurrentAction(KeyEvent.ACTION_UP);
                }

            }

            @Override
            public void onLongClick() {

            }

            @Override
            public void onRelease() {

            }
        });
        return keyboardDigitalSwitch;
    }

    //其他辅助方法----------------------------------
    public List<KeyboardElement> getElements() {
        return elements;
    }
    public void removeElements() {
        for (KeyboardElement element : elements) {
            elementsLayout.removeView(element);
        }
        elements.clear();
    }

    //编辑、删除模式切换相关---------------------------
    public KeyboardElement getCurrentSelectedElement() {
        return currentSelectedElement;
    }
    public void setCurrentSelectedElement(KeyboardElement currentSelectedElement) {
        this.currentSelectedElement = currentSelectedElement;
    }
    /**
     * 这个函数用于重绘所有的对象，如果不用这个函数，选择编辑对象的时候
     * 会导致之前选择的对象不能恢复原来的颜色
     */
    public void elementsInvalidate(){
        for (KeyboardElement element : elements) {
            element.invalidate();
        }
    }
    public ControllerMode getControllerMode() {
        return currentMode;
    }
    public void setControllerMode(ControllerMode currentMode){
        this.currentMode = currentMode;
        elementsInvalidate();
    }

    //编辑模式相关---------------------------
    public void resizeElement(int size) {
        if (currentSelectedElement == null){
            return;
        }
        currentSelectedElement.resizeElement(size);
    }
    protected void setSeekbarProgress(int progress){
        advanceSettingController.getEditElement().setSeekbarValue(progress);
    }

    //删除模式相关---------------------------
    protected void confirmDeleteElement(String DeleteElementName){
        advanceSettingController.getDeleteElement().confirmDeleteElement(DeleteElementName);
    }


    public void refreshLayout() {
        removeElements();
        loadCurrentLayout();
    }

    void sendKeyEvent(KeyEvent keyEvent) {
        game.onKey(null,keyEvent.getKeyCode(),keyEvent);
        //如果map中有对应按键的runnable，则删除该按键的runnable。
        if (keyEventRunnableMap.containsKey(keyEvent.getKeyCode())){
            handler.removeCallbacks(keyEventRunnableMap.get(keyEvent.getKeyCode()));
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                game.onKey(null,keyEvent.getKeyCode(),keyEvent);
            }
        };
        //把这个按键的runnable放到map中，以便这个按键重新发送的时候，重置runnable。
        keyEventRunnableMap.put(keyEvent.getKeyCode(),runnable);

        handler.postDelayed(runnable, 25);
        handler.postDelayed(runnable, 50);
        handler.postDelayed(runnable, 75);
    }

}


