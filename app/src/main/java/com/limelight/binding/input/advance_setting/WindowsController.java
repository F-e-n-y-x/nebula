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

    private ControllerManager controllerManager;
    private Context context;

    private FrameLayout textWindow;
    private TextView textWindowText;
    private Button textWindowConfirmButton;
    private Button textWindowCancelButton;
    private FrameLayout editTextWindow;
    private EditText editTextWindowText;
    private Button editTextWindowConfirmButton;
    private Button editTextWindowCancelButton;

    private Message incomeMessage;


    public WindowsController(ControllerManager controllerManager, FrameLayout layout, Context context){

        textWindow = layout.findViewById(R.id.text_window);
        textWindowText = layout.findViewById(R.id.text_window_text);
        textWindowConfirmButton = layout.findViewById(R.id.text_window_confirm);
        textWindowCancelButton = layout.findViewById(R.id.text_window_canel);
        editTextWindow = layout.findViewById(R.id.edittext_window);
        editTextWindowText = layout.findViewById(R.id.edittext_window_text);
        editTextWindowConfirmButton = layout.findViewById(R.id.edittext_window_confirm);
        editTextWindowCancelButton = layout.findViewById(R.id.edittext_window_canel);

        textWindowConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controllerManager.sendMessage(new Message(
                        "text_window_confirm",
                        0,
                        WindowsController.class,
                        new Class[]{
                            incomeMessage.getSender()
                        },
                        incomeMessage.getMessageContent()
                ));
            }
        });
        textWindowCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controllerManager.sendMessage(new Message(
                        "text_window_cancel",
                        0,
                        WindowsController.class,
                        new Class[]{
                                incomeMessage.getSender()
                        },
                        incomeMessage.getMessageContent()
                ));
            }
        });
        editTextWindowConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                incomeMessage.getMessageContent().put("return_edittext_text", editTextWindowText.getText().toString());
                controllerManager.sendMessage(new Message(
                        "edittext_window_confirm",
                        0,
                        WindowsController.class,
                        new Class[]{
                                incomeMessage.getSender()
                        },
                        incomeMessage.getMessageContent()
                ));
            }
        });
        editTextWindowCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                incomeMessage.getMessageContent().put("return_edittext_text", editTextWindowText.getText().toString());
                controllerManager.sendMessage(new Message(
                        "edittext_window_cancel",
                        0,
                        WindowsController.class,
                        new Class[]{
                                incomeMessage.getSender()
                        },
                        incomeMessage.getMessageContent()
                ));
            }
        });

    }


    @Override
    public void receiveMessage(Message message) {
        String messageTitle = message.getMessageTitle();
        switch (messageTitle){
            case "text_window_open":
                //此项消息必须有purpose和text，放在messageContent
                incomeMessage = message;
                textWindow.setVisibility(View.VISIBLE);
                textWindowText.setText((String)incomeMessage.getMessageContent().get("text"));
                break;
            case "text_window_close":
                incomeMessage = null;
                textWindow.setVisibility(View.GONE);
                break;
            case "edittext_window_open":
                //此项消息必须有purpose，放在messageContent
                incomeMessage = message;
                editTextWindow.setVisibility(View.VISIBLE);
                Map<String, Object> messageContent =  incomeMessage.getMessageContent();
                String text = (String) messageContent.get("text");
                if (text == null){
                    break;
                }
                editTextWindowText.setText(text);
                break;
            case "edittext_window_close":
                incomeMessage = null;
                editTextWindow.setVisibility(View.GONE);
                break;

        }

    }
}
