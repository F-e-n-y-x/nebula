package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class AdvanceSettingPreference {
    public static final String ADVANCE_PREFERENCE = "advance_preference";
    public static final String CURRENT_LAYOUT_KEY = "current_layout_key";
    public static final String CURRENT_LAYOUT_VALUE = "default";

    private final Context context;

    public AdvanceSettingPreference(Context context){
        this.context = context;
        SharedPreferences preferences = context.getSharedPreferences(ADVANCE_PREFERENCE, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        //初始化advance_preference表
        if (!preferences.contains(CURRENT_LAYOUT_KEY)){
            editor.putString(CURRENT_LAYOUT_KEY,CURRENT_LAYOUT_VALUE);
        }
        editor.apply();
    }

    public String getCurrentLayoutName(){
        SharedPreferences preferences = context.getSharedPreferences(ADVANCE_PREFERENCE, Activity.MODE_PRIVATE);
        return preferences.getString(CURRENT_LAYOUT_KEY, CURRENT_LAYOUT_VALUE);
    }

    public void setCurrentLayoutName(String layoutName){
        SharedPreferences.Editor editor = context.getSharedPreferences(ADVANCE_PREFERENCE,Activity.MODE_PRIVATE).edit();
        editor.putString(CURRENT_LAYOUT_KEY,layoutName);
        editor.apply();
    }



}
