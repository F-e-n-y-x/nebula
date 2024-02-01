package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.R;

import java.util.Map;
import java.util.Objects;

public class WindowsController extends Controller{

    public interface TextWindowListener{
        void onConfirmCLick();

        void onCancelClick();
        String getText();
    }

    public interface EditTextWindowListener{
        void onConfirmClick(String text);

        void onCancelClick(String text);

        String getText();
    }

    public interface DeviceWindowListener{
        void onElementClick(String text, String tag);
    }

    private ControllerManager controllerManager;
    private Context context;

    //text window
    private FrameLayout textWindow;
    private TextView textWindowText;
    private Button textWindowConfirmButton;
    private Button textWindowCancelButton;
    private TextWindowListener textWindowListener;

    //edittext window
    private FrameLayout editTextWindow;
    private EditText editTextWindowText;
    private Button editTextWindowConfirmButton;
    private Button editTextWindowCancelButton;
    private EditTextWindowListener editTextWindowListener;

    //device window
    private FrameLayout deviceLayout;
    private Button keyboardButton;
    private Button mouseButton;
    private Button gamepadButton;
    private FrameLayout keyboardLayout;
    private FrameLayout mouseLayout;
    private FrameLayout gamepadLayout;
    private Message incomeMessage;
    private DeviceWindowListener deviceWindowListener;




    public WindowsController(ControllerManager controllerManager, FrameLayout layout, Context context){

        textWindow = layout.findViewById(R.id.text_window);
        textWindowText = layout.findViewById(R.id.text_window_text);
        textWindowConfirmButton = layout.findViewById(R.id.text_window_confirm);
        textWindowCancelButton = layout.findViewById(R.id.text_window_canel);

        textWindowConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textWindowListener.onConfirmCLick();
            }
        });
        textWindowCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textWindowListener.onCancelClick();
            }
        });


        editTextWindow = layout.findViewById(R.id.edittext_window);
        editTextWindowText = layout.findViewById(R.id.edittext_window_text);
        editTextWindowConfirmButton = layout.findViewById(R.id.edittext_window_confirm);
        editTextWindowCancelButton = layout.findViewById(R.id.edittext_window_canel);

        editTextWindowConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = editTextWindowText.getText().toString();
                editTextWindowListener.onConfirmClick(text);
            }
        });
        editTextWindowCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = editTextWindowText.getText().toString();
                editTextWindowListener.onCancelClick(text);
            }
        });

        //device views
        deviceLayout = layout.findViewById(R.id.device_layout_container);
        keyboardButton = layout.findViewById(R.id.keyboard_open_button);
        mouseButton = layout.findViewById(R.id.mouse_open_button);
        gamepadButton = layout.findViewById(R.id.gamepad_open_button);
        keyboardLayout = layout.findViewById(R.id.edit_keyboard_layout);
        mouseLayout = layout.findViewById(R.id.edit_mouse_layout);
        gamepadLayout = layout.findViewById(R.id.edit_gamepad_layout);

        keyboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchDeviceLayout(keyboardButton,keyboardLayout);
            }
        });
        mouseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchDeviceLayout(mouseButton,mouseLayout);
            }
        });
        gamepadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchDeviceLayout(gamepadButton,gamepadLayout);
            }
        });
        View.OnClickListener keyboardListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = ((TextView)v).getText().toString();
                String tag = ((TextView)v).getTag().toString();
                deviceWindowListener.onElementClick(text,tag);
                deviceLayout.setVisibility(View.GONE);
            }
        };
        LinearLayout keyboardDrawing = keyboardLayout.findViewById(R.id.keyboard_drawing);
        for (int i = 0; i < keyboardDrawing.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) keyboardDrawing.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnClickListener(keyboardListener);
            }
        }





    }

    private void switchDeviceLayout(Button button,FrameLayout frameLayout){
        keyboardButton.setAlpha(0.4f);
        mouseButton.setAlpha(0.4f);
        gamepadButton.setAlpha(0.4f);
        keyboardLayout.setVisibility(View.GONE);
        mouseLayout.setVisibility(View.GONE);
        gamepadLayout.setVisibility(View.GONE);

        button.setAlpha(1);
        frameLayout.setVisibility(View.VISIBLE);
    }


    @Override
    public void receiveMessage(Message message) {
        String messageTitle = message.getMessageTitle();
        switch (messageTitle){
            case "text_window_open":
                incomeMessage = message;
                textWindow.setVisibility(View.VISIBLE);
                textWindowListener = (TextWindowListener) incomeMessage.getMessageContent().get("text_window_listener");
                textWindowText.setText(textWindowListener.getText());
                break;
            case "text_window_close":
                incomeMessage = null;
                textWindow.setVisibility(View.GONE);
                break;
            case "edit_text_window_open":
                incomeMessage = message;
                editTextWindow.setVisibility(View.VISIBLE);
                editTextWindowListener = (EditTextWindowListener) incomeMessage.getMessageContent().get("edit_text_window_listener");
                editTextWindowText.setText(editTextWindowListener.getText());
                break;
            case "edit_text_window_close":
                incomeMessage = null;
                editTextWindow.setVisibility(View.GONE);
                break;
            case "device_window_open":
                incomeMessage = message;
                deviceLayout.setVisibility(View.VISIBLE);
                deviceWindowListener = (DeviceWindowListener) incomeMessage.getMessageContent().get("device_window_listener");
                break;
        }

    }
}
