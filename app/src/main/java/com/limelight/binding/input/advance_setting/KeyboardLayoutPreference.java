package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;


public class KeyboardLayoutPreference {

    private final String keyboardLayoutListPreference;
    private final List<String> layouts;
    private final Context context;

    public KeyboardLayoutPreference(String keyboardLayoutListPreference, Context context){
        this.keyboardLayoutListPreference = keyboardLayoutListPreference;
        this.context = context;
        layouts = new ArrayList<>();


    }

    public void addLayout(String layoutName){
        /*
        * 两步：
        * 1.先加到list中
        * 2.再加到SharedPreference中
        * */
        layouts.add(layoutName);
        SharedPreferences.Editor editor = context.getSharedPreferences(keyboardLayoutListPreference,Context.MODE_PRIVATE).edit();
        editor.putString(layoutName,"");
        editor.apply();
    }

    public void deleteLayout(String layoutName){
        /*
         * 两步：
         * 1.先从SharedPreference中删除
         * 2.再从list中删除
         * */
        SharedPreferences.Editor editor = context.getSharedPreferences(keyboardLayoutListPreference,Context.MODE_PRIVATE).edit();
        editor.remove(layoutName);
        editor.apply();
        layouts.remove(layoutName);
    }

    public void renameLayout(String layoutOldName, String layoutNewName){

    }

    public List<String> getLayouts(){
        return layouts;
    }



}
