package com.limelight.binding.input.advance_setting.funtion;

import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.AdvanceSettingController;
import com.limelight.binding.input.advance_setting.KeyboardController;

public class DeleteElement {
    private Button      deleteButton;
    private Button      exitDeleteButton;
    private FrameLayout confirmDeleteWindow;
    private Button      deleteConfirmButton;
    private Button      deleteCancelButton;
    private TextView    deleteConfirmText;
    private AdvanceSettingController advanceSettingController;

    public DeleteElement(FrameLayout advanceSettingFatherLayout, AdvanceSettingController advanceSettingController){
        this.advanceSettingController = advanceSettingController;

        deleteButton            = advanceSettingFatherLayout.findViewById(R.id.delete_element);
        exitDeleteButton        = advanceSettingFatherLayout.findViewById(R.id.delete_element_exit_button);
        confirmDeleteWindow     = advanceSettingFatherLayout.findViewById(R.id.delete_element_confirm_windows);
        deleteConfirmButton     = advanceSettingFatherLayout.findViewById(R.id.delete_element_confirm_windows_confirm_button);
        deleteCancelButton      = advanceSettingFatherLayout.findViewById(R.id.delete_element_confirm_windows_cancel_button);
        deleteConfirmText       = advanceSettingFatherLayout.findViewById(R.id.delete_element_confirm_windows_text);

        initDeleteButton();
        initConfirmDeleteWindow();
        initExitButton();
    }

    private void initDeleteButton(){
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advanceSettingController.getKeyboardController().setControllerMode(KeyboardController.ControllerMode.DeleteButtons);
                advanceSettingController.setAdvanceSettingLayoutVisibility(View.GONE);
                advanceSettingController.setButtonConfigureVisibility(View.GONE);
                exitDeleteButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initConfirmDeleteWindow(){
        //confirm_button
        deleteConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advanceSettingController.getKeyboardController().removeSelectedElement();
                exitDeleteButton.setVisibility(View.VISIBLE);
                confirmDeleteWindow.setVisibility(View.GONE);
            }
        });
        //cancel_button
        deleteCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteWindow.setVisibility(View.GONE);
                advanceSettingController.getKeyboardController().setCurrentSelectedElement(null);
                //取消后需要重绘选择的按钮，但是只有重绘所有
                advanceSettingController.getKeyboardController().elementsInvalidate();
                confirmDeleteWindow.setVisibility(View.GONE);
            }
        });
    }

    private void initExitButton(){
        exitDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                advanceSettingController.getKeyboardController().setControllerMode(KeyboardController.ControllerMode.Active);
                exitDeleteButton.setVisibility(View.GONE);
                advanceSettingController.setAdvanceSettingLayoutVisibility(View.VISIBLE);
                advanceSettingController.setButtonConfigureVisibility(View.VISIBLE);
            }
        });
    }

    public void confirmDeleteElement(String deleteElementName){
        deleteConfirmText.setText(deleteElementName);
        confirmDeleteWindow.setVisibility(View.VISIBLE);
        exitDeleteButton.setVisibility(View.GONE);
    }

}
