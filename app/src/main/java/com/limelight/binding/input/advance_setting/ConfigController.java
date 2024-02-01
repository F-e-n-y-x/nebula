package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.limelight.R;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ConfigController extends Controller{

    private FrameLayout layerConfig;
    private ConfigListPreference configListPreference;
    private Context context;
    private ConfigItem currentSelectItem;

    private ControllerManager controllerManager;

    private LinearLayout configItemContainer;


    public ConfigController(ControllerManager controllerManager,FrameLayout layout, Context context){
        this.context = context;
        this.layerConfig = layout;
        this.controllerManager = controllerManager;
        configListPreference = new ConfigListPreference(context);
        configItemContainer = layout.findViewById(R.id.config_item_container);
        layout.findViewById(R.id.add_config_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpAddWindow();
            }
        });
        loadConfigs();
    }

    private void loadConfigs(){
        String currentConfigName = configListPreference.getCurrentConfigName();

        Map<Long, String> idNameMap = new HashMap<>();
        for (String name : configListPreference.getConfigurationNames()){
            idNameMap.put(Long.parseLong(configListPreference.getConfigId(name)),name);
        }
        Map<Long, String> sortedMap = new TreeMap<>(idNameMap);
        for (Map.Entry<Long, String> entry : sortedMap.entrySet()) {
            String name = entry.getValue();
            ConfigItem item = addConfigItem(name);
            if (name.equals(currentConfigName)){
                currentSelectItem = item;
                currentSelectItem.selected();
            }
        }

    }

    private ConfigItem addConfigItem(String layoutName){
        ConfigItem configItem = new ConfigItem(this,layoutName,context);

        configItemContainer.addView(configItem.getView(), (configItemContainer.getChildCount() - 1));
        return configItem;
    }


    @Override
    public void receiveMessage(Message message) {
        switch (message.getMessageTitle()){
            case "ready_init":
                loadElements(currentSelectItem.getText());
                break;
            case "config_mode":
                layerConfig.setVisibility(View.VISIBLE);
                break;
            case "exit_config_mode":
                layerConfig.setVisibility(View.GONE);
                break;

        }
    }

    private void loadElements(String configName){
        Map<String,Object> messageContent = new HashMap<>();
        messageContent.put("config_id",configListPreference.getConfigId(configName));
        controllerManager.sendMessage(new Message(
                "edit_load_elements",
                0,
                ConfigController.class,
                new Class[]{
                        EditController.class
                },
                messageContent
        ));
    }

    private void loadSettings(String configName){

    }

    public void selectItem(ConfigItem configItem){
        currentSelectItem.unselected();
        currentSelectItem = configItem;
        currentSelectItem.selected();
        configListPreference.setCurrentConfigName(configItem.getText());
        loadElements(configItem.getText());
        loadSettings(configItem.getText());

    }

    public void jumpAddWindow(){
        Map<String,Object> messageContent = new HashMap<>();
        WindowsController.EditTextWindowListener addListener = new WindowsController.EditTextWindowListener() {
            @Override
            public void onConfirmClick(String text) {
                if (configListPreference.isContainedName(text)){
                    Toast.makeText(context,"配置名字已存在",Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!text.matches("^[a-zA-Z0-9]{1,10}$")){
                    Toast.makeText(context,"名称只能由1-10个数字、小写字母组成",Toast.LENGTH_SHORT).show();
                    return;
                }
                configListPreference.addConfiguration(text);
                addConfigItem(text);
                controllerManager.sendMessage(new Message(
                        "edit_text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
            }

            @Override
            public void onCancelClick(String text) {
                controllerManager.sendMessage(new Message(
                        "edit_text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
            }

            @Override
            public String getText() {
                return "";
            }
        };

        messageContent.put("edit_text_window_listener",addListener);
        controllerManager.sendMessage(new Message(
                "edit_text_window_open",
                0,
                ConfigController.class,
                new Class[]{
                        WindowsController.class
                },
                messageContent
        ));
    }

    public void jumpRenameWindow(ConfigItem configItem){
        Map<String,Object> messageContent = new HashMap<>();
        WindowsController.EditTextWindowListener renameListener = new WindowsController.EditTextWindowListener() {
            @Override
            public void onConfirmClick(String text) {
                String preName = configItem.getText();
                String nowName = text;
                //如果名字没有改变，点击确认键也可以返回，如果没有这个判断，点击确认键会显示名称已存在
                if (preName.equals(nowName)){
                    controllerManager.sendMessage(new Message(
                            "edit_text_window_close",
                            0,
                            ConfigController.class,
                            new Class[]{
                                    WindowsController.class
                            },
                            null
                    ));
                    return;
                }
                if (configListPreference.isContainedName(nowName)){
                    Toast.makeText(context,"配置名字已存在",Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!nowName.matches("^[a-zA-Z0-9]{1,10}$")){
                    Toast.makeText(context,"名称只能由1-10个数字、小写字母组成",Toast.LENGTH_SHORT).show();
                    return;
                }
                //如果编辑的这个配置先前的名字和当前选择的配置的名字一样，则说明在修改选中的配置，需要修改存储中目前的配置名称
                if (preName.equals(currentSelectItem.getText())){
                    configListPreference.setCurrentConfigName(nowName);
                }
                configListPreference.renameConfiguration(preName,nowName);
                configItem.setText(nowName);
                controllerManager.sendMessage(new Message(
                        "edit_text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
            }

            @Override
            public void onCancelClick(String text) {
                controllerManager.sendMessage(new Message(
                        "edit_text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
            }

            @Override
            public String getText() {
                return configItem.getText();
            }
        };
        messageContent.put("edit_text_window_listener",renameListener);
        controllerManager.sendMessage(new Message(
                "edit_text_window_open",
                0,
                ConfigController.class,
                new Class[]{
                        WindowsController.class
                },
                messageContent
        ));
    }

    public void jumpDeleteWindow(ConfigItem configItem){
        Map<String,Object> messageContent = new HashMap<>();
        WindowsController.TextWindowListener deleteListener = new WindowsController.TextWindowListener() {
            @Override
            public void onConfirmCLick() {
                configListPreference.deleteConfig(configItem.getText());
                configItemContainer.removeView(configItem.getView());
                controllerManager.sendMessage(new Message(
                        "text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
            }

            @Override
            public void onCancelClick() {
                controllerManager.sendMessage(new Message(
                        "text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
            }

            @Override
            public String getText() {
                return "是否删除:" + configItem.getText();
            }
        };

        messageContent.put("text_window_listener",deleteListener);
        controllerManager.sendMessage(new Message(
                "text_window_open",
                0,
                ConfigController.class,
                new Class[]{
                        WindowsController.class
                },
                messageContent
        ));
    }


}
