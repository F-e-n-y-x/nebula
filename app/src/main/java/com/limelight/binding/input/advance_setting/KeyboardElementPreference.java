package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.limelight.binding.input.advance_setting.keyboard_bean.KeyboardBean;

import java.util.HashMap;
import java.util.Map;

public class KeyboardElementPreference {

    private final String keyboardElementPreference;
    private final Map<String, KeyboardBean> elements;
    private final Context context;

    public KeyboardElementPreference(String keyboardElementPreference, Context context){
        this.context = context;
        this.keyboardElementPreference = keyboardElementPreference;
        elements = new HashMap<>();

        // 对象创建的时候读取按钮信息
        SharedPreferences preferences = context.getSharedPreferences(keyboardElementPreference,Activity.MODE_PRIVATE);
        // 从SharePreference中获取Map<String, String>格式的Element信息
        Map<String, String> elementsString = (Map<String, String>) preferences.getAll();
        // 将Map<String, String>格式转换为Map<String, KeyboardBean>
        for (Map.Entry<String, String> entry: elementsString.entrySet()){
            KeyboardBean keyboardBean = stringToJSON(entry.getValue());
            // 转换完成后放到Map<String, KeyboardBean>中
            elements.put(entry.getKey(),keyboardBean);
        }
    }

    public void addElement(String keyboardElementName, KeyboardBean element){
        /*
         * 两步操作：
         * 1.加入MAP中
         * 2.加入SharedPreference
         * */
        elements.put(keyboardElementName, element);
        String elementString = JSONToString(element);
        SharedPreferences.Editor editor = context.getSharedPreferences(keyboardElementPreference, Activity.MODE_PRIVATE).edit();
        editor.putString(keyboardElementName, elementString);
        editor.apply();

    }
    public void deleteElement(String keyboardElementName){
        /*
         * 两步操作：
         * 1.从SharedPreference中删除
         * 2.从MAP中删除
         * */
        SharedPreferences.Editor editor = context.getSharedPreferences(keyboardElementPreference, Activity.MODE_PRIVATE).edit();
        editor.remove(keyboardElementName);
        editor.apply();
        elements.remove(keyboardElementName);
    }
    public Map<String, KeyboardBean> getElements() {
        return elements;
    }

    private KeyboardBean stringToJSON(String element){
        return null;
    }

    private String JSONToString(KeyboardBean elements){
        return null;
    }
}
