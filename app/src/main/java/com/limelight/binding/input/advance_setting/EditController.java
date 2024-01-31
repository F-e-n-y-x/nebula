package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class EditController extends Controller{

    private ElementPreference elementPreference;
    private Context context;
    private ControllerManager controllerManager;
    private FrameLayout layerEdit;
    private Element editElement;

    public EditController( ControllerManager controllerManager, FrameLayout layout,Context context) {
        this.context = context;
        this.controllerManager = controllerManager;
        this.layerEdit = layout;


    }

    @Override
    public void receiveMessage(Message message) {
        switch (message.getMessageTitle()){
            case "edit_load_elements":
                loadElements((String) message.getMessageContent().get("config_id"));
                break;
        }
    }

    public void loadElements(String configId){
        elementPreference = new ElementPreference(configId,context);
        Map<String,Object> messageContent = new HashMap<>();
        messageContent.put("elements",elementPreference.getElements());
        controllerManager.sendMessage(new Message(
                "load_elements",
                0,
                EditController.class,
                new Class[]{
                      ElementController.class
                },
                messageContent
        ));
    }

    public void jumpSelectElementValue(TextView valueTextView){

    }
}
