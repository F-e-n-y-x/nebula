package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ElementPreference {

    final private static String ELEMENT_TABLE_PREFIX = "element_";

    private final String elementLayoutName;
    private final Map<String, ElementBean> elements = new HashMap<>();
    private final Context context;
    private final Gson gson = new Gson();

    public ElementPreference(String configId, Context context){
        this.context = context;
        this.elementLayoutName = ELEMENT_TABLE_PREFIX + configId;

        // 对象创建的时候读取按钮信息
        SharedPreferences preferences = context.getSharedPreferences(elementLayoutName,Activity.MODE_PRIVATE);
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
        //这个elementBean的原因是因为Gson会报错，无法转换Map，但是新建一个elementBean就可以了，可能是创建之后，这个对象并没有被回收，于是就被gson使用了
        new ElementBean(
            null,
            0,
            new HashMap<>(),
            0,
            0,
            0,
            0,
            100,
            0xF0888888,
            0xF00000FF,
            0,
            new HashMap<>());
        return gson.fromJson(element, ElementBean.class);
    }

    private String JSONToString(ElementBean element){
        return gson.toJson(element);
    }

    public void clear(){
        SharedPreferences.Editor editor = context.getSharedPreferences(elementLayoutName, Activity.MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }
}
