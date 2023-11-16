package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* layout使用Map结构：
* key是用户自己命名的，用来唯一标识
* value是毫秒时间戳，作为layout的id，用这个值来索引layout表。
* */
public class KeyboardLayoutPreference {

    private final static String LAYOUT_LIST_PREFERENCE = "layout_list_preference";
    private final Map<String, String> layoutsMap;
    private final Context context;

    public KeyboardLayoutPreference( Context context){
        this.context = context;
        SharedPreferences preferences = context.getSharedPreferences(LAYOUT_LIST_PREFERENCE, Activity.MODE_PRIVATE);
        layoutsMap = (Map<String, String>) preferences.getAll();

        //初始化默认布局：每次创建该对象的时候，都会检查有没有默认布局
        addLayout(AdvanceSettingPreference.CURRENT_LAYOUT_VALUE);
    }

    public int addLayout(String layoutName){
        if (layoutsMap.containsKey(layoutName)){
            //新增布局失败，布局已存在
            return -1;
        }

        /*
        * 两步：
        * 1.先加到list中
        * 2.再加到SharedPreference中
        * */
        String layoutId = String.valueOf(System.currentTimeMillis());
        layoutsMap.put(layoutName, layoutId);
        SharedPreferences.Editor editor = context.getSharedPreferences(LAYOUT_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.putString(layoutName, layoutId);
        editor.apply();
        //新增布局成功
        return 0;
    }

    public int deleteLayout(String layoutName){
        if (layoutName.equals(AdvanceSettingPreference.CURRENT_LAYOUT_VALUE)){
            //删除失败，不能删除默认布局
            return -1;
        }
        //1.先把element的preference删除
        SharedPreferences.Editor layoutEditor = context.getSharedPreferences(layoutName,Context.MODE_PRIVATE).edit();
        layoutEditor.clear();
        layoutEditor.apply();
        //2.再从SharedPreferenceList中删除Layout名称
        SharedPreferences.Editor editor = context.getSharedPreferences(LAYOUT_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.remove(layoutName);
        editor.apply();
        //3.再从list中删除
        layoutsMap.remove(layoutName);
        //删除成功
        return 0;
    }

    public int renameLayout(String layoutOldName, String layoutNewName){
        if (layoutOldName.equals(AdvanceSettingPreference.CURRENT_LAYOUT_VALUE)){
            //重命名失败，不能重命名默认布局
            return -1;
        }
        if (layoutsMap.containsKey(layoutNewName)){
            //重命名布局失败，布局已存在
            return -2;
        }

        String layoutId = layoutsMap.get(layoutOldName);
        layoutsMap.remove(layoutOldName);
        layoutsMap.put(layoutNewName,layoutId);
        SharedPreferences.Editor layoutEditor = context.getSharedPreferences(LAYOUT_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        layoutEditor.remove(layoutOldName);
        layoutEditor.putString(layoutNewName,layoutId);
        layoutEditor.apply();
        //重命名成功
        return 0;
    }

    public List<String> getLayoutNames(){
        Set<String> layoutNamesSet = layoutsMap.keySet();
        return new ArrayList<>(layoutNamesSet);
    }

    public String getLayoutId(String layoutName){
        return layoutsMap.get(layoutName);
    }


}
