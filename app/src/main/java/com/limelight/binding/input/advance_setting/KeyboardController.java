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
        MoveButtons,
        ResizeButtons
    }

    private final Context context;
    private final Game game;
    private final Handler handler;

    private FrameLayout frame_layout = null;

    ControllerMode currentMode = ControllerMode.Active;

    private final List<KeyboardElement> elements = new ArrayList<>();
    private final AdvanceSettingPreference advanceSettingPreference;
    private KeyboardElementPreference keyboardElementPreference;
    private KeyboardLayoutPreference keyboardLayoutPreference;
    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();

    public KeyboardController(FrameLayout layout, final Context context) {
        this.frame_layout = layout;
        this.context = context;
        this.game = (Game) context;
        this.handler = new Handler(Looper.getMainLooper());
        this.advanceSettingPreference = new AdvanceSettingPreference(context);
        this.keyboardLayoutPreference = new KeyboardLayoutPreference(context);
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

    public void removeElements() {
        for (KeyboardElement element : elements) {
            frame_layout.removeView(element);
        }
        elements.clear();
    }

    public void setOpacity(int opacity) {
        for (KeyboardElement element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void addElement(KeyboardElement element, int x, int y, int size) {
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(size, size);
        layoutParams.setMargins(x, y, 0, 0);

        frame_layout.addView(element, layoutParams);
    }

    public void loadCurrentLayout(){
        String currentLayout = advanceSettingPreference.getCurrentLayoutName();
        String currentLayoutId = keyboardLayoutPreference.getLayoutId(currentLayout);
        keyboardElementPreference = new KeyboardElementPreference(currentLayoutId,context);
        Map<String,KeyboardBean> elementsMap = keyboardElementPreference.getElements();
        for (Map.Entry<String,KeyboardBean> entry: elementsMap.entrySet()){
            KeyboardBean keyboardBean = entry.getValue();
            switch (keyboardBean.getType()){
                case 0:
                    addElement(
                            createButton(entry.getKey(),keyboardBean,keyboardBean.getLayer()),
                            keyboardBean.getPositionX(),
                            keyboardBean.getPositionY(),
                            keyboardBean.getSize());
                    break;
                case 1:
                    addElement(
                            createSwitch(entry.getKey(),keyboardBean,keyboardBean.getLayer()),
                            keyboardBean.getPositionX(),
                            keyboardBean.getPositionY(),
                            keyboardBean.getSize());
                    break;
                case 2:
                    addElement(
                            createPad(entry.getKey(),keyboardBean),
                            keyboardBean.getPositionX(),
                            keyboardBean.getPositionY(),
                            keyboardBean.getSize());
                    break;
            }
        }
    }

    public KeyboardElement createPad(String elementId, KeyboardBean keyboardBean){
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
    public KeyboardElement createButton(String elementId, KeyboardBean keyboardBean, int layer){
        KeyboardDigitalButton keyboardDigitalButton = new KeyboardDigitalButton(this, keyboardBean, elementId, layer, context);
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
    public KeyboardElement createSwitch(String elementId, KeyboardBean keyboardBean, int layer){
        return new KeyboardDigitalButton(this, keyboardBean, elementId, layer, context);
    }

    public List<KeyboardElement> getElements() {
        return elements;
    }

    public void refreshLayout() {
        removeElements();
        loadCurrentLayout();
    }

    public ControllerMode getControllerMode() {
        return currentMode;
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


