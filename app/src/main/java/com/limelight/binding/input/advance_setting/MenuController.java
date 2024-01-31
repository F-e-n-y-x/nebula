package com.limelight.binding.input.advance_setting;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;

import com.limelight.R;

public class MenuController extends Controller{

    private final ControllerManager controllerManager;

    private final FrameLayout layerMenu;
    private String lastMode;
    private Button lastButton;

    private Button openMenuButton;
    private Button closeMenuButton;
    private Button configModeButton;
    private Button settingModeButton;
    private Button editModeButton;

    public MenuController(ControllerManager controllerManager, FrameLayout layerMenu) {
        this.controllerManager = controllerManager;
        this.layerMenu = layerMenu;

        openMenuButton = layerMenu.findViewById(R.id.button_open_menu);
        openMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMenu();
            }
        });
        closeMenuButton = layerMenu.findViewById(R.id.button_close_menu);
        closeMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenu();
            }
        });
        configModeButton = layerMenu.findViewById(R.id.button_config_mode);
        configModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode("config_mode");
            }
        });
        settingModeButton = layerMenu.findViewById(R.id.button_setting_mode);
        settingModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode("setting_mode");
            }
        });
        editModeButton = layerMenu.findViewById(R.id.button_edit_mode);
        editModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode("edit_mode");
            }
        });
        lastButton = settingModeButton;
        lastMode = "setting_mode";
    }



    @Override
    public void receiveMessage(Message message) {
    }


    private void openMenu(){
        openMenuButton.setVisibility(View.GONE);
        closeMenuButton.setVisibility(View.VISIBLE);
        configModeButton.setVisibility(View.VISIBLE);
        settingModeButton.setVisibility(View.VISIBLE);
        editModeButton.setVisibility(View.VISIBLE);
        lastButton.setAlpha(1);
        controllerManager.sendMessage(new Message(
                lastMode,
                0,
                MenuController.class,
                new Class[]{
                        ConfigController.class,
                        SettingController.class,
                        EditController.class
                },
                null
        ));
    }

    private void closeMenu(){
        openMenuButton.setVisibility(View.VISIBLE);
        closeMenuButton.setVisibility(View.GONE);
        configModeButton.setVisibility(View.GONE);
        settingModeButton.setVisibility(View.GONE);
        editModeButton.setVisibility(View.GONE);
        controllerManager.sendMessage(new Message(
                "exit_" + lastMode,
                0,
                MenuController.class,
                new Class[]{
                        ConfigController.class,
                        SettingController.class,
                        EditController.class
                },
                null
        ));
    }

    private void switchMode(String mode){
        Button modeButton = null;

        switch (mode){
            case "config_mode":
                modeButton = configModeButton;
                break;
            case "setting_mode":
                modeButton = settingModeButton;
                break;
            case "edit_mode":
                modeButton = editModeButton;
                break;
            default:
                return;
        }

        lastButton.setAlpha(0.5f);
        modeButton.setAlpha(1);
        modeButton.setClickable(false);
        lastButton.setClickable(true);
        lastButton = modeButton;

        controllerManager.sendMessage(new Message(
                "exit_" + lastMode,
                0,
                MenuController.class,
                new Class[]{
                        ConfigController.class,
                        SettingController.class,
                        EditController.class
                },
                null
        ));
        controllerManager.sendMessage(new Message(
                mode,
                0,
                MenuController.class,
                new Class[]{
                        ConfigController.class,
                        SettingController.class,
                        EditController.class
                },
                null
        ));
        lastMode = mode;
    }


}
