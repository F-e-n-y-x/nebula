package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ElementPreference {

    final private static String ELEMENT_TABLE_PREFIX = "element_";

    private final String elementLayoutName;
    private final Map<String, ElementBean> elements = new HashMap<>();
    private final Context context;
    private final Gson gson = new Gson();

    public ElementPreference(String configurationId, Context context){
        this.context = context;
        this.elementLayoutName = ELEMENT_TABLE_PREFIX + configurationId;

        // 对象创建的时候读取按钮信息
        SharedPreferences preferences = context.getSharedPreferences(configurationId,Activity.MODE_PRIVATE);
        // 从SharePreference中获取Map<String, String>格式的Element信息
        Map<String, String> elementsString = (Map<String, String>) preferences.getAll();
        // 将Map<String, String>格式转换为Map<String, KeyboardBean>
        for (Map.Entry<String, String> entry: elementsString.entrySet()){
            ElementBean elementBean = stringToJSON(entry.getValue());
            // 转换完成后放到Map<String, KeyboardBean>中
            elements.put(entry.getKey(), elementBean);
        }

        //wg_debug
        System.out.println("wg_debug elements preference:" + elements);
    }

    public int addElement(ElementBean element){
        String elementName = element.getName();
        /*
         * 两步操作：
         * 1.加入MAP中
         * 2.加入SharedPreference
         * */
        elements.put(elementName, element);
        String elementString = JSONToString(element);
        SharedPreferences.Editor editor = context.getSharedPreferences(elementLayoutName, Activity.MODE_PRIVATE).edit();
        editor.putString(elementName, elementString);
        editor.apply();
        //新增成功

        //wg_debug
        System.out.println("wg_debug elements preference:" + elements);
        return 0;

    }

    public void saveElements(){
        SharedPreferences.Editor editor = context.getSharedPreferences(elementLayoutName, Activity.MODE_PRIVATE).edit();
        for (Map.Entry<String, ElementBean> entry: elements.entrySet()){
            editor.putString(entry.getKey(),JSONToString(entry.getValue()));
        }
        editor.apply();

        //wg_debug
        System.out.println("wg_debug elements preference:" + elements);
    }
    public void deleteElement(String elementId){

        /*
         * 两步操作：
         * 1.从SharedPreference中删除
         * 2.从MAP中删除
         * */
        elements.remove(elementId);
        SharedPreferences.Editor editor = context.getSharedPreferences(elementLayoutName, Activity.MODE_PRIVATE).edit();
        editor.remove(elementId);
        editor.apply();

        //wg_debug
        System.out.println("wg_debug elements preference:" + elements);
    }
    public Collection<ElementBean> getElements() {
        return elements.values();
    }

    public boolean containsElement(String elementName){
        //新增失败，按钮名称已存在
        return elements.containsKey(elementName);
    }

    private ElementBean stringToJSON(String element){
        return gson.fromJson(element, ElementBean.class);
    }

    private String JSONToString(ElementBean element){
        return gson.toJson(element);
    }
}
