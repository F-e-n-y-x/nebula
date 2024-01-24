package com.limelight.binding.input.advance_setting;

import android.content.Context;

import java.util.List;

public class ElementLayoutController {

    public static final String CURRENT_LAYOUT_KEY = "current_layout_key";
    public static final String DEFAULT_LAYOUT_NAME = "default";
    private AdvanceSettingController advanceSettingController;
    private ConfigurationListPreference configurationListPreference;
    private Context context;

    public ElementLayoutController(AdvanceSettingController advanceSettingController, Context context){
        this.advanceSettingController = advanceSettingController;
        this.context = context;

        configurationListPreference = new ConfigurationListPreference(context);

        initCurrentConfiguration();
    }

    private void initCurrentConfiguration(){
        if (advanceSettingController.getAdvancePreference(CURRENT_LAYOUT_KEY) == null){
            advanceSettingController.setAdvancePreference(CURRENT_LAYOUT_KEY, DEFAULT_LAYOUT_NAME);
        }
    }

    public String getCurrentConfigurationName(){
        return advanceSettingController.getAdvancePreference(CURRENT_LAYOUT_KEY);
    }

    public void setCurrentConfigurationName(String layoutName){
        advanceSettingController.setAdvancePreference(CURRENT_LAYOUT_KEY,layoutName);
    }

    public int addConfiguration(String name){
        return configurationListPreference.addConfiguration(name);
    }
    public int deleteConfiguration(String name){
        return configurationListPreference.deleteConfiguration(name);
    }
    public int renameConfiguration(String oldName, String newName){
        return configurationListPreference.renameConfiguration(oldName,newName);
    }
    public List<String> getConfigurationNames(){
        return configurationListPreference.getConfigurationNames();
    }

    public String getConfigurationId(String layoutName){
        return configurationListPreference.getConfigurationId(layoutName);
    }
}
