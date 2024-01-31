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
            case "text_window_confirm":
                String textWindowPurpose = (String) message.getMessageContent().get("purpose");
                if (textWindowPurpose.equals("delete")){
                    purposeDelete(message);
                }
                break;
            case "text_window_cancel":
                controllerManager.sendMessage(new Message(
                        "text_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
                break;
            case "edittext_window_confirm":
                String EdittextWindowPurpose = (String) message.getMessageContent().get("purpose");
                if (EdittextWindowPurpose.equals("rename")){
                    purposeRename(message);
                } else if (EdittextWindowPurpose.equals("add")) {
                    purposeAdd(message);
                }
                break;
            case "edittext_window_cancel":
                controllerManager.sendMessage(new Message(
                        "edittext_window_close",
                        0,
                        ConfigController.class,
                        new Class[]{
                                WindowsController.class
                        },
                        null
                ));
                break;
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

    private void purposeRename(Message message){
        String nowName = (String) message.getMessageContent().get("return_edittext_text");
        String preName = (String) message.getMessageContent().get("text");
        //如果名字没有改变，点击确认键也可以返回，如果没有这个判断，点击确认键会显示名称已存在
        if (preName.equals(nowName)){
            controllerManager.sendMessage(new Message(
                    "edittext_window_close",
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
            configListPreference.renameConfiguration(preName,nowName);
            configListPreference.setCurrentConfigName(nowName);
        }
        ((ConfigItem)message.getMessageContent().get("config_item")).setText(nowName);
        controllerManager.sendMessage(new Message(
                "edittext_window_close",
                0,
                ConfigController.class,
                new Class[]{
                        WindowsController.class
                },
                null
        ));
    }

    private void purposeAdd(Message message){
        String name = (String) message.getMessageContent().get("return_edittext_text");
        if (configListPreference.isContainedName(name)){
            Toast.makeText(context,"配置名字已存在",Toast.LENGTH_SHORT).show();
            return;
        }
        if (!name.matches("^[a-zA-Z0-9]{1,10}$")){
            Toast.makeText(context,"名称只能由1-10个数字、小写字母组成",Toast.LENGTH_SHORT).show();
            return;
        }
        configListPreference.addConfiguration(name);
        addConfigItem(name);
        controllerManager.sendMessage(new Message(
                "edittext_window_close",
                0,
                ConfigController.class,
                new Class[]{
                        WindowsController.class
                },
                null
        ));
    }

    public void purposeDelete(Message message){
        ConfigItem configItem = (ConfigItem) message.getMessageContent().get("config_item");
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
        messageContent.put("purpose","add");
        controllerManager.sendMessage(new Message(
                "edittext_window_open",
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
        messageContent.put("text",configItem.getText());
        messageContent.put("config_item",configItem);
        messageContent.put("purpose","rename");
        controllerManager.sendMessage(new Message(
                "edittext_window_open",
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
        messageContent.put("text","是否删除:" + configItem.getText());
        messageContent.put("purpose","delete");
        messageContent.put("config_item",configItem);
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
