package com.limelight.binding.input.advance_setting.funtion;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.KeyboardLayoutController;

public class KeyboardLayoutItem{
    private LinearLayout keyboardLayoutItemLayout;
    private CheckBox layoutItemCheckBox;
    private Button layoutItemRenameButton;
    private Button layoutItemDeleteButton;
    private SelectLayout selectLayout;
    private KeyboardLayoutItem myself;


    public KeyboardLayoutItem(SelectLayout selectLayout, String layoutName, Context context){
        this.selectLayout = selectLayout;
        this.myself = this;

        keyboardLayoutItemLayout = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.keyboard_layout_item,null);
        layoutItemCheckBox         = (CheckBox) keyboardLayoutItemLayout.getChildAt(0);
        layoutItemRenameButton       = (Button) ((LinearLayout) keyboardLayoutItemLayout.getChildAt(1)).getChildAt(0);
        layoutItemDeleteButton       = (Button) ((LinearLayout) keyboardLayoutItemLayout.getChildAt(1)).getChildAt(1);
        setText(layoutName);
        if (layoutName.equals(KeyboardLayoutController.DEFAULT_LAYOUT_NAME)){
            ((LinearLayout) keyboardLayoutItemLayout.getChildAt(1)).setVisibility(View.GONE);
        }
        layoutItemCheckBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectLayout.selectItem(myself);
            }
        });
        layoutItemRenameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectLayout.jumpRenameWindow(myself);
            }
        });

        layoutItemDeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectLayout.jumpDeleteWindow(myself);
            }
        });
    }

    public void setText(String text){
        layoutItemCheckBox.setText(text);
    }

    public String getText(){
        return layoutItemCheckBox.getText().toString();
    }

    public void selected(){
        layoutItemCheckBox.setClickable(false);
        displayDeleteButton(false);
        layoutItemCheckBox.setChecked(true);
    }

    public void unselected(){
        layoutItemCheckBox.setClickable(true);
        displayDeleteButton(true);
        layoutItemCheckBox.setChecked(false);
    }

    public View getView(){
        return keyboardLayoutItemLayout;
    }

    private void displayDeleteButton(boolean display){
        if (layoutItemCheckBox.getText().toString().equals(KeyboardLayoutController.DEFAULT_LAYOUT_NAME)){
            return;
        }

        if (display){
            layoutItemDeleteButton.setVisibility(View.VISIBLE);
        } else {
            layoutItemDeleteButton.setVisibility(View.GONE);
        }
    }
}
