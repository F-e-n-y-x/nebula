package com.limelight.binding.input.advance_setting;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
* layout使用Map结构：
* key是用户自己命名的，用来唯一标识
* value是毫秒时间戳，作为layout的id，用这个值来索引layout表。
* */
public class ConfigListPreference {

    final private static String CONFIG_LIST_PREFERENCE = "configuration_list_preference";
    final public static String DEFAULT_CONFIG_NAME = "default";
    final public static String CURRENT_CONFIG_NAME_KEY = "current_config_name";
    final private Map<String, String> configMap;
    final private Context context;
    private String currentConfigName = DEFAULT_CONFIG_NAME;

    public ConfigListPreference(Context context){
        this.context = context;
        SharedPreferences preferences = context.getSharedPreferences(CONFIG_LIST_PREFERENCE, Activity.MODE_PRIVATE);
        configMap = (Map<String, String>) preferences.getAll();

        SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        currentConfigName = defaultPreferences.getString(CURRENT_CONFIG_NAME_KEY,DEFAULT_CONFIG_NAME);

        //初始化默认布局：每次创建该对象的时候，都会检查有没有默认布局
        addConfiguration(DEFAULT_CONFIG_NAME);

    }

    public int addConfiguration(String configurationName){
        if (configMap.containsKey(configurationName)){
            //新增布局失败，布局已存在
            return -1;
        }

        /*
        * 两步：
        * 1.先加到list中
        * 2.再加到SharedPreference中
        * */
        String configurationId = String.valueOf(System.currentTimeMillis());
        configMap.put(configurationName, configurationId);
        SharedPreferences.Editor editor = context.getSharedPreferences(CONFIG_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.putString(configurationName, configurationId);
        editor.apply();

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configMap);
        //新增布局成功
        return 0;
    }

    public int deleteConfig(String configurationName){
        if (configurationName.equals(DEFAULT_CONFIG_NAME)){
            //删除失败，不能删除默认布局
            return -1;
        }
        //1.先把element的preference删除
        SharedPreferences.Editor configurationEditor = context.getSharedPreferences(configurationName,Context.MODE_PRIVATE).edit();
        configurationEditor.clear();
        configurationEditor.apply();
        //2.再从SharedPreferenceList中删除Configuration名称
        SharedPreferences.Editor editor = context.getSharedPreferences(CONFIG_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.remove(configurationName);
        editor.apply();
        //3.再从list中删除
        configMap.remove(configurationName);
        //删除成功

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configMap);
        return 0;
    }

    public int renameConfiguration(String configurationOldName, String configurationNewName){
        if (configurationOldName.equals(DEFAULT_CONFIG_NAME)){
            //重命名失败，不能重命名默认布局
            return -1;
        }
        if (configMap.containsKey(configurationNewName)){
            //重命名布局失败，布局已存在
            return -2;
        }

        String configurationId = configMap.get(configurationOldName);
        configMap.remove(configurationOldName);
        configMap.put(configurationNewName,configurationId);
        SharedPreferences.Editor configurationEditor = context.getSharedPreferences(CONFIG_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        configurationEditor.remove(configurationOldName);
        configurationEditor.putString(configurationNewName,configurationId);
        configurationEditor.apply();
        //重命名成功

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configMap);
        return 0;
    }

    public List<String> getConfigurationNames(){
        Set<String> configurationNamesSet = configMap.keySet();
        return new ArrayList<>(configurationNamesSet);
    }

    public String getConfigId(String configurationName){
        return configMap.get(configurationName);
    }

    public String getCurrentConfigName() {
        return currentConfigName;
    }

    public void setCurrentConfigName(String currentConfigName) {
        this.currentConfigName = currentConfigName;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(CURRENT_CONFIG_NAME_KEY,currentConfigName);
        editor.apply();
    }

    public boolean isContainedName(String Name){
        return configMap.containsKey(Name);
    }

}
