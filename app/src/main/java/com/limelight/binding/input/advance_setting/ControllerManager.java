package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.limelight.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerManager {

    private FrameLayout advanceSettingView;
    private FrameLayout fatherLayout;
    private Context context;
    private final Map<Class<?>,Controller> controllerMap = new HashMap<>();
    private final List<Message> messagesList = new ArrayList<>();
    private boolean massageIsSending = false;

    public ControllerManager(FrameLayout layout, Context context){
        advanceSettingView = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.advance_setting_view,null);
        this.fatherLayout = layout;
        //menuController 创建并注册
        FrameLayout layerMenu = advanceSettingView.findViewById(R.id.layer_menu);
        MenuController menuController = new MenuController(this,layerMenu);
        controllerMap.put(MenuController.class,menuController);

        //configController
        FrameLayout layerConfig = advanceSettingView.findViewById(R.id.layer_config);
        ConfigController configController = new ConfigController(this,layerConfig,context);
        controllerMap.put(ConfigController.class,configController);

        //setting controller
        SettingController settingController = new SettingController();
        controllerMap.put(SettingController.class,settingController);

        //Edit controller
        FrameLayout layerEdit = advanceSettingView.findViewById(R.id.layer_edit);
        EditController editController = new EditController(this,layerEdit,context);
        controllerMap.put(EditController.class,editController);

        FrameLayout layerElement = advanceSettingView.findViewById(R.id.layer_element);
        ElementController elementController = new ElementController(this,layerElement,context);
        controllerMap.put(ElementController.class,elementController);

        //window controller
        FrameLayout layerWindows = advanceSettingView.findViewById(R.id.layer_windows);
        WindowsController windowsController = new WindowsController(this,layerWindows,context);
        controllerMap.put(WindowsController.class,windowsController);

        sendMessage(new Message(
                "ready_init",
                0,
                null,
                new Class[]{
                        ConfigController.class
                },
                null
        ));
    }

    public void sendMessage(Message message){
        messagesList.add(message);
        if (massageIsSending){
            return;
        }

        send();
    }

    private void send(){
        massageIsSending = true;

        //获取消息队列的第一个元素
        Message message = messagesList.get(0);
        System.out.println(message);
        for (Class<?> controllerClass : message.getReceiver()){
            controllerMap.get(controllerClass).receiveMessage(message);
        }
        //消息发送完后，从消息队列中删除这个元素
        messagesList.remove(0);

        //检查消息队列中是否还有消息，如果没有消息，则退出
        if (messagesList.isEmpty()){
            massageIsSending = false;
            return;
        }

        //如果消息队列中还有其他的消息，继续发送
        send();
    }

    public void refreshLayout(){
        fatherLayout.removeView(advanceSettingView);
        fatherLayout.addView(advanceSettingView);
    }

}
