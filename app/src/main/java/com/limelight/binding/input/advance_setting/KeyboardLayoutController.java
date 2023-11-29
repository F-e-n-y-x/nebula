package com.limelight.binding.input.advance_setting;

import android.content.Context;

import java.util.List;

public class KeyboardLayoutController {

    public static final String CURRENT_LAYOUT_KEY = "current_layout_key";
    public static final String DEFAULT_LAYOUT_NAME = "default";
    private AdvanceSettingController advanceSettingController;
    private KeyboardLayoutPreference keyboardLayoutPreference;
    private Context context;

    public KeyboardLayoutController(AdvanceSettingController advanceSettingController, Context context){
        this.advanceSettingController = advanceSettingController;
        this.context = context;

        keyboardLayoutPreference = new KeyboardLayoutPreference(context);

        initCurrentLayout();
    }

    private void initCurrentLayout(){
        if (advanceSettingController.getAdvancePreference(CURRENT_LAYOUT_KEY) == null){
            advanceSettingController.setAdvancePreference(CURRENT_LAYOUT_KEY, DEFAULT_LAYOUT_NAME);
        }
    }

    public String getCurrentLayoutName(){
        return advanceSettingController.getAdvancePreference(CURRENT_LAYOUT_KEY);
    }

    public void setCurrentLayoutName(String layoutName){
        advanceSettingController.setAdvancePreference(CURRENT_LAYOUT_KEY,layoutName);
    }

    public int addLayout(String name){
        return keyboardLayoutPreference.addLayout(name);
    }
    public int deleteLayout(String name){
        return keyboardLayoutPreference.deleteLayout(name);
    }
    public int renameLayout(String oldName, String newName){
        return keyboardLayoutPreference.renameLayout(oldName,newName);
    }
    public List<String> getLayoutNames(){
        return keyboardLayoutPreference.getLayoutNames();
    }

    public String getLayoutId(String layoutName){
        return keyboardLayoutPreference.getLayoutId(layoutName);
    }
}
