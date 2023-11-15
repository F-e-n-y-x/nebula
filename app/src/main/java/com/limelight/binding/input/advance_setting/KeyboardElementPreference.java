package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

public class KeyboardElementPreference {

    private final String keyboardLayoutId;
    private final Map<String, KeyboardBean> elements = new HashMap<>();
    private final Context context;
    private final Gson gson = new Gson();

    public KeyboardElementPreference(String keyboardLayoutId, Context context){
        this.context = context;
        this.keyboardLayoutId = keyboardLayoutId;

        // 对象创建的时候读取按钮信息
        SharedPreferences preferences = context.getSharedPreferences(keyboardLayoutId,Activity.MODE_PRIVATE);
        // 从SharePreference中获取Map<String, String>格式的Element信息
        Map<String, String> elementsString = (Map<String, String>) preferences.getAll();
        // 将Map<String, String>格式转换为Map<String, KeyboardBean>
        for (Map.Entry<String, String> entry: elementsString.entrySet()){
            KeyboardBean keyboardBean = stringToJSON(entry.getValue());
            // 转换完成后放到Map<String, KeyboardBean>中
            elements.put(entry.getKey(),keyboardBean);
        }
    }

    public int addElement(String keyboardElementName, KeyboardBean element){
        if (elements.containsKey(keyboardElementName)){
            //新增失败，按钮名称已存在
            return -1;
        }

        /*
         * 两步操作：
         * 1.加入MAP中
         * 2.加入SharedPreference
         * */
        elements.put(keyboardElementName, element);
        String elementString = JSONToString(element);
        SharedPreferences.Editor editor = context.getSharedPreferences(keyboardLayoutId, Activity.MODE_PRIVATE).edit();
        editor.putString(keyboardElementName, elementString);
        editor.apply();
        //新增成功
        return 0;

    }
    public void deleteElement(String keyboardElementName){

        /*
         * 两步操作：
         * 1.从SharedPreference中删除
         * 2.从MAP中删除
         * */
        SharedPreferences.Editor editor = context.getSharedPreferences(keyboardLayoutId, Activity.MODE_PRIVATE).edit();
        editor.remove(keyboardElementName);
        editor.apply();
        elements.remove(keyboardElementName);
    }
    public Map<String, KeyboardBean> getElements() {
        return elements;
    }

    private KeyboardBean stringToJSON(String element){
        return gson.fromJson(element,KeyboardBean.class);
    }

    private String JSONToString(KeyboardBean element){
        return gson.toJson(element);
    }
}
