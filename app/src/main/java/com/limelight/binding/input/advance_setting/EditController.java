package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.element_card.ButtonCard;
import com.limelight.binding.input.advance_setting.element_card.ElementCard;
import com.limelight.binding.input.advance_setting.element_card.PadCard;
import com.limelight.binding.input.advance_setting.element_card.SwitchCard;

import java.util.HashMap;
import java.util.Map;

public class EditController extends Controller{

    private ElementPreference elementPreference;
    private Context context;
    private ControllerManager controllerManager;
    private FrameLayout layerEdit;
    private Element editElement;
    private ElementCard elementCard;
    private EditController myself;
    private TextView currentSetValuePlace;


    //edit views
    private EditText elementWidth;
    private EditText elementHeight;
    private EditText elementX;
    private EditText elementY;
    private Button addButton;
    private Button deleteButton;
    private LinearLayout addElementView;


    //add views
    private EditText elementName;
    private Spinner elementType;
    private Button addConfirm;
    private FrameLayout elementCardSlot;





    public EditController( ControllerManager controllerManager, FrameLayout layout,Context context) {
        this.context = context;
        this.controllerManager = controllerManager;
        this.layerEdit = layout;
        this.myself = this;

        addElementView = layout.findViewById(R.id.add_element_view);
        deleteButton = layout.findViewById(R.id.delete_element_button);
        layout.findViewById(R.id.add_element_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (addElementView.getVisibility() == View.VISIBLE){
                    addElementView.setVisibility(View.GONE);
                    deleteButton.setVisibility(View.VISIBLE);
                    ((Button) v).setText("新增");
                } else {
                    addElementView.setVisibility(View.VISIBLE);
                    deleteButton.setVisibility(View.GONE);
                    ((Button) v).setText("退出");
                }
            }
        });

        //add view
        layout.findViewById(R.id.add_element_confirm_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        //初始化elementCardSlot
        elementCardSlot = layout.findViewById(R.id.element_slot);
        elementCard = new ButtonCard(this,context);
        elementCardSlot.addView(elementCard.getView());
        elementType = layout.findViewById(R.id.element_type_spinner);
        elementType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:
                        insertCard(new ButtonCard(myself,context));
                        break;
                    case 1:
                        insertCard(new SwitchCard(myself,context));
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        

    }


    private void insertCard(ElementCard elementCard){
        elementCardSlot.removeView(this.elementCard.getView());
        this.elementCard = elementCard;
        elementCardSlot.addView(this.elementCard.getView());
    }


    @Override
    public void receiveMessage(Message message) {
        switch (message.getMessageTitle()){
            case "edit_load_elements":
                loadElements((String) message.getMessageContent().get("config_id"));
                break;
            case "edit_mode":
                layerEdit.setVisibility(View.VISIBLE);
                break;
            case "exit_edit_mode":
                layerEdit.setVisibility(View.GONE);
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

    public void jumpDeviceLayout(TextView valueTextView){
        currentSetValuePlace = valueTextView;
        Map<String,Object> messageContent = new HashMap<>();
        WindowsController.DeviceWindowListener keySelectListener = new WindowsController.DeviceWindowListener() {
            @Override
            public void onElementClick(String text, String tag) {
                valueTextView.setTag(tag);
                valueTextView.setText(text);
            }
        };
        messageContent.put("text_window_listener",keySelectListener);
        controllerManager.sendMessage(new Message(
                "device_window_open",
                0,
                ConfigController.class,
                new Class[]{
                        WindowsController.class
                },
                messageContent
        ));
    }
}
