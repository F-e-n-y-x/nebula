package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class AdvanceSettingPreference {
    public static final String ADVANCE_PREFERENCE = "advance_preference";
    private final Context context;

    public AdvanceSettingPreference(Context context){
        this.context = context;
        //wg_debug
        wg_debugGetAll();
    }

    public String getValue(String key){
        SharedPreferences preferences = context.getSharedPreferences(ADVANCE_PREFERENCE, Activity.MODE_PRIVATE);
        return preferences.getString(key, null);
    }

    public void setValue(String key, String value){
        SharedPreferences.Editor editor = context.getSharedPreferences(ADVANCE_PREFERENCE,Activity.MODE_PRIVATE).edit();
        editor.putString(key,value);
        editor.apply();
        //wg_debug
        wg_debugGetAll();
    }

    private void wg_debugGetAll(){
        SharedPreferences preferences = context.getSharedPreferences(ADVANCE_PREFERENCE, Activity.MODE_PRIVATE);
        System.out.println("wg_debug setting preference:" + preferences.getAll());
    }

}
