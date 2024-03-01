package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.FrameLayout;

import com.limelight.Game;
import com.limelight.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElementController {

    private final Context context;
    private final Game game;
    private final Handler handler;
    private ElementPreference elementPreference;

    private final ControllerManager controllerManager;


    private final List<Element> elements = new ArrayList<>();
    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();
    private Map<Integer, Runnable> mouseEventRunnableMap = new HashMap<>();
    private FrameLayout elementsLayout;
    public ElementController(ControllerManager controllerManager, FrameLayout layout, final Context context) {
        this.elementsLayout = layout;
        this.context = context;
        this.game = (Game) context;
        this.controllerManager = controllerManager;
        layout.findViewById(R.id.element_touch_view).setOnTouchListener(game);

        handler = new Handler(Looper.getMainLooper());
    }

    Handler getHandler() {
        return handler;
    }




    public boolean isContainedElement(String name){
        for (Element element : elements) {
            if (element.getElementId().equals(name)){
                return true;
            }
        }
        return false;
    }

    public void loadElementConfig(String configId){
        removeElementsFromScreen();
        elementPreference = new ElementPreference(configId,context);
        for (ElementBean elementBean : elementPreference.getElements()){
            addElementToScreen(elementBean);
        }
    }

    public void deleteElementConfig(String configId){
        new ElementPreference(configId,context).clear();
    }

    public void deleteElement(Element element){
        elementsLayout.removeView(element);
        elements.remove(element);
        elementPreference.deleteElement(element.getElementId());
    }

    public void saveElement(Element element){
        elementPreference.addElement(element.getElementBean());
    }


    public void addElement(ElementBean elementBean){
        addElementToScreen(elementBean);
        elementPreference.addElement(elementBean);
    }

    private void addElementToScreen(ElementBean elementBean){
        Element element = null;
        switch (elementBean.getType()){
            case ElementBean.TYPE_BUTTON:{
                element = new DigitalButton(this,elementBean,context);
                break;
            }
            case ElementBean.TYPE_SWITCH:{
                element = new DigitalSwitch(this,elementBean,context);
                break;
            }
            case ElementBean.TYPE_K_PAD:{
                element = new DigitalKPad(this,elementBean,context);
                break;
            }
            case ElementBean.TYPE_M_BUTTON:{
                element = addMButton(elementBean);
                break;
            }
            case ElementBean.TYPE_K_STICK:{
                element = addKStick(elementBean);
                break;
            }
            case ElementBean.TYPE_K_ISTICK:{
                element = addKIStick(elementBean);
                break;
            }
            case ElementBean.TYPE_G_STICK:{
                element = addGStick(elementBean);
                break;
            }
            case ElementBean.TYPE_G_ISTICK:{
                element = addGIStick(elementBean);
                break;
            }

        }
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(elementBean.getWidth(), elementBean.getHeight());
        layoutParams.setMargins(elementBean.getPositionX() - elementBean.getWidth()/2, elementBean.getPositionY() - elementBean.getHeight()/2, 0, 0);

        elementsLayout.addView(element, layoutParams);

    }

    private Element addMButton(ElementBean elementBean){
        return null;
    }
    private Element addKStick(ElementBean elementBean){
        return null;
    }
    private Element addKIStick(ElementBean elementBean){
        return null;
    }
    private Element addGStick(ElementBean elementBean){
        return null;
    }
    private Element addGIStick(ElementBean elementBean){
        return null;
    }


    //其他辅助方法----------------------------------
    public List<Element> getElements() {
        return elements;
    }
    public void removeElementsFromScreen() {
        for (Element element : elements) {
            elementsLayout.removeView(element);
        }
        elements.clear();
    }

    public Element selectElement(float x, float y){
        for (Element element : elements) {
            if (element.inRange(x,y)){
                return element;
            }
        }
        return null;
    }

    public void setOpacity(int opacity) {
        for (Element element : elements) {
            element.setOpacity(opacity);
        }
    }


    public void sendKeyEvent(KeyEvent keyEvent) {
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


        handler.postDelayed(runnable, 50);
        handler.postDelayed(runnable, 75);
    }
    public void sendMouseEvent(int mouseId, boolean down){
        game.mouseButtonEvent(mouseId, down);
        if (mouseEventRunnableMap.containsKey(mouseId)){
            handler.removeCallbacks(mouseEventRunnableMap.get(mouseId));
        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                game.mouseButtonEvent(mouseId, down);
            }
        };
        //把这个按键的runnable放到map中，以便这个按键重新发送的时候，重置runnable。
        mouseEventRunnableMap.put(mouseId,runnable);

        handler.postDelayed(runnable, 50);
        handler.postDelayed(runnable, 75);
    }

}


