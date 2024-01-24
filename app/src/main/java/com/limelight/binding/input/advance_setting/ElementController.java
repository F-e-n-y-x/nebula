package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.Game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElementController {

    private final int[] positionScale = new int[4];//minX,maxX,minY,maxY
    private final int[] sizeScale = new int[4];//minWidth,maxWidth,minHeight,maxHeight

    private final Context context;
    private final Game game;
    private final Handler handler;


    private final List<Element> elements = new ArrayList<>();
    private Map<Integer, Runnable> keyEventRunnableMap = new HashMap<>();
    private FrameLayout elementsLayout;
    public ElementController(FrameLayout layout, final Context context) {
        this.elementsLayout = layout;
        this.context = context;
        this.game = (Game) context;

        handler = new Handler(Looper.getMainLooper());
    }

    public int[] getPositionScale() {
        return positionScale;
    }

    public int[] getSizeScale() {
        return sizeScale;
    }

    Handler getHandler() {
        return handler;
    }

    public void hide() {
        for (Element element : elements) {
            element.setVisibility(View.INVISIBLE);
        }
    }

    public void show() {
        for (Element element : elements) {
            element.setVisibility(View.VISIBLE);
        }
    }
    public void removeElement(Element element){
        elementsLayout.removeView(element);
        elements.remove(element);
    }
    public void setOpacity(int opacity) {
        for (Element element : elements) {
            element.setOpacity(opacity);
        }
    }

    public void loadElements(Collection<ElementBean> elementBeans){
        removeElements();
        for (ElementBean elementBean : elementBeans){
            addElement(elementBean);
        }
    }


    public void addElement(ElementBean elementBean){
        Element element = null;
        switch (elementBean.getType()){
            case 0:{
                element = addButton(elementBean);
                break;
            }
            case 1:{
                element = addSwitch(elementBean);
                break;
            }
            case 2:{
                element = addPad(elementBean);
                break;
            }
            case 3:{
                element = addMButton(elementBean);
                break;
            }
            case 4:{
                element = addKStick(elementBean);
                break;
            }
            case 5:{
                element = addKIStick(elementBean);
                break;
            }
            case 6:{
                element = addGStick(elementBean);
                break;
            }
            case 7:{
                element = addGIStick(elementBean);
                break;
            }

        }
        elements.add(element);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(elementBean.getWidth(), elementBean.getHeight());
        layoutParams.setMargins(elementBean.getPositionX(), elementBean.getPositionY(), 0, 0);

        elementsLayout.addView(element, layoutParams);

    }
    private Element addButton(ElementBean elementBean){
        return null;
    }
    private Element addSwitch(ElementBean elementBean){
        return null;
    }
    private Element addPad(ElementBean elementBean){
        return null;
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

    public boolean positionIsInvalid(int positionX,int positionY){
        return true;
    }

    public boolean sizeIsInvalid(int length,int high){
        return true;
    }


    //其他辅助方法----------------------------------
    public List<Element> getElements() {
        return elements;
    }
    public void removeElements() {
        for (Element element : elements) {
            elementsLayout.removeView(element);
        }
        elements.clear();
    }
    /**
     * 这个函数用于重绘所有的对象，如果不用这个函数，选择编辑对象的时候
     * 会导致之前选择的对象不能恢复原来的颜色
     */
    public void elementsInvalidate(){
        for (Element element : elements) {
            element.invalidate();
        }
    }


    public void moveElement(Element element, int x, int y){

        element.moveElement(x, y);
    }
    public void resizeElement(Element element, int width, int height) {

        element.resizeElement(width, height);
    }


    public void refreshLayout() {
        removeElements();
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

        handler.postDelayed(runnable, 25);
        handler.postDelayed(runnable, 50);
        handler.postDelayed(runnable, 75);
    }

}


