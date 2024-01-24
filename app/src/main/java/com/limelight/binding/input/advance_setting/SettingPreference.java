package com.limelight.binding.input.advance_setting;


import android.content.Context;

public class SettingPreference {

    final private static String SETTING_TABLE_PREFIX = "setting_";

    private Context context;
    private String settingTableName;

    public SettingPreference(String configurationId, Context context){
        this.context = context;
        this.settingTableName = SETTING_TABLE_PREFIX + configurationId;
    }
}
