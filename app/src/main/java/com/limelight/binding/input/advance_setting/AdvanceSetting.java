package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.widget.FrameLayout;

import java.util.Map;

public class AdvanceSetting {

    final private static String CURRENT_CONFIGURATION_KEY = "current_configuration_key";

    //全局
    private Context context;

    //存储
    private AdvanceSettingPreference advanceSettingPreference;
    private ConfigurationListPreference configurationListPreference;
    private SettingPreference settingPreference;
    private ElementPreference elementPreference;
    private String currentConfigurationName = "default";

    //中间件
    private ElementController elementController;

    //操作界面

    public AdvanceSetting(FrameLayout layout, final Context context){
        this.context = context;

        advanceSettingPreference = new AdvanceSettingPreference(context);
        configurationListPreference = new ConfigurationListPreference(context);

        elementController = new ElementController(layout, context);


    }


    public void loaderConfiguration(String currentConfigurationName){
        //切换当前布局
        this.currentConfigurationName = currentConfigurationName;
        //保存当前布局
        advanceSettingPreference.setValue(CURRENT_CONFIGURATION_KEY,currentConfigurationName);

        //获取但当前布局的ID
        String currentConfigurationId = configurationListPreference.getConfigurationId(currentConfigurationName);

        //加载Element到屏幕
        elementPreference = new ElementPreference(currentConfigurationId, context);
        elementController.loadElements(elementPreference.getElements());

        //加载setting
        settingPreference = new SettingPreference(currentConfigurationId, context);
    }





}
