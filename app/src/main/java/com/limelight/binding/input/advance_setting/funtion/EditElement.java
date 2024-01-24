package com.limelight.binding.input.advance_setting.funtion;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.AdvanceSettingController;
import com.limelight.binding.input.advance_setting.ElementController;

public class EditElement {
    private Button editButton;
    private AdvanceSettingController advanceSettingController;
    private FrameLayout resizeElementWindows;
    private SeekBar resizeElementWindowsSeekbar;
    private Button resizeElementWindowsButton;

    public EditElement(FrameLayout advanceSettingFatherLayout, AdvanceSettingController advanceSettingController){
        this.advanceSettingController = advanceSettingController;

        editButton                  = advanceSettingFatherLayout.findViewById(R.id.edit_element);
        resizeElementWindows        = advanceSettingFatherLayout.findViewById(R.id.resize_element_windows);
        resizeElementWindowsSeekbar = advanceSettingFatherLayout.findViewById(R.id.resize_element_windows_seekbar);
        resizeElementWindowsButton  = advanceSettingFatherLayout.findViewById(R.id.resize_element_windows_button);

        initEditButton();
        initEditWindows();
    }

    private void initEditButton(){
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advanceSettingController.getKeyboardController().setControllerMode(ElementController.ControllerMode.EditButtons);
                advanceSettingController.setAdvanceSettingLayoutVisibility(View.GONE);
                advanceSettingController.setButtonConfigureVisibility(View.GONE);
                resizeElementWindows.setVisibility(View.VISIBLE);

            }
        });
    }

    private void initEditWindows(){

        resizeElementWindowsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advanceSettingController.getKeyboardController().setControllerMode(ElementController.ControllerMode.Active);
                advanceSettingController.getKeyboardController().setCurrentSelectedElement(null);
                advanceSettingController.getKeyboardController().saveLayout();
                advanceSettingController.setAdvanceSettingLayoutVisibility(View.VISIBLE);
                advanceSettingController.setButtonConfigureVisibility(View.VISIBLE);
                resizeElementWindows.setVisibility(View.GONE);
            }
        });
    }

    public void setSeekbarValue(int progress){
        resizeElementWindowsSeekbar.setProgress(progress);
    }
}
