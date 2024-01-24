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
public class ConfigurationListPreference {

    final private static String CONFIGURATION_LIST_PREFERENCE = "setting_table_list_preference";
    final public static String DEFAULT_CONFIGURATION_NAME = "default";
    final private Map<String, String> configurationMap;
    final private Context context;

    public ConfigurationListPreference(Context context){
        this.context = context;
        SharedPreferences preferences = context.getSharedPreferences(CONFIGURATION_LIST_PREFERENCE, Activity.MODE_PRIVATE);
        configurationMap = (Map<String, String>) preferences.getAll();

        //初始化默认布局：每次创建该对象的时候，都会检查有没有默认布局
        addConfiguration(DEFAULT_CONFIGURATION_NAME);

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configurationMap);
    }

    public int addConfiguration(String configurationName){
        if (configurationMap.containsKey(configurationName)){
            //新增布局失败，布局已存在
            return -1;
        }

        /*
        * 两步：
        * 1.先加到list中
        * 2.再加到SharedPreference中
        * */
        String configurationId = String.valueOf(System.currentTimeMillis());
        configurationMap.put(configurationName, configurationId);
        SharedPreferences.Editor editor = context.getSharedPreferences(CONFIGURATION_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.putString(configurationName, configurationId);
        editor.apply();

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configurationMap);
        //新增布局成功
        return 0;
    }

    public int deleteConfiguration(String configurationName){
        if (configurationName.equals(DEFAULT_CONFIGURATION_NAME)){
            //删除失败，不能删除默认布局
            return -1;
        }
        //1.先把element的preference删除
        SharedPreferences.Editor configurationEditor = context.getSharedPreferences(configurationName,Context.MODE_PRIVATE).edit();
        configurationEditor.clear();
        configurationEditor.apply();
        //2.再从SharedPreferenceList中删除Configuration名称
        SharedPreferences.Editor editor = context.getSharedPreferences(CONFIGURATION_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        editor.remove(configurationName);
        editor.apply();
        //3.再从list中删除
        configurationMap.remove(configurationName);
        //删除成功

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configurationMap);
        return 0;
    }

    public int renameConfiguration(String configurationOldName, String configurationNewName){
        if (configurationOldName.equals(DEFAULT_CONFIGURATION_NAME)){
            //重命名失败，不能重命名默认布局
            return -1;
        }
        if (configurationMap.containsKey(configurationNewName)){
            //重命名布局失败，布局已存在
            return -2;
        }

        String configurationId = configurationMap.get(configurationOldName);
        configurationMap.remove(configurationOldName);
        configurationMap.put(configurationNewName,configurationId);
        SharedPreferences.Editor configurationEditor = context.getSharedPreferences(CONFIGURATION_LIST_PREFERENCE,Context.MODE_PRIVATE).edit();
        configurationEditor.remove(configurationOldName);
        configurationEditor.putString(configurationNewName,configurationId);
        configurationEditor.apply();
        //重命名成功

        //wg_debug
        System.out.println("wg_debug configurationsMap:" + configurationMap);
        return 0;
    }

    public List<String> getConfigurationNames(){
        Set<String> configurationNamesSet = configurationMap.keySet();
        return new ArrayList<>(configurationNamesSet);
    }

    public String getConfigurationId(String configurationName){
        return configurationMap.get(configurationName);
    }


}
