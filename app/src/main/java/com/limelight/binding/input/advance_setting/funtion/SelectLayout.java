package com.limelight.binding.input.advance_setting.funtion;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.AdvanceSettingController;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class SelectLayout {

    private AdvanceSettingController advanceSettingController;
    private Context context;

    private KeyboardLayoutItem currentSelectedItem;
    private KeyboardLayoutItem editingLayoutItem;

    private LinearLayout keyboardLayoutContainer;
    private Button addLayoutButton;
    private Button renameConfirmButton;
    private Button renameCancelButton;
    private EditText renameEditText;
    private FrameLayout renameWindow;
    private Button addConfirmButton;
    private Button addCancelButton;
    private EditText addEditText;
    private FrameLayout addWindow;
    private FrameLayout confirmDeleteWindow;
    private Button      deleteConfirmButton;
    private Button      deleteCancelButton;
    private TextView deleteConfirmText;


    public SelectLayout(FrameLayout advanceSettingFatherLayout, AdvanceSettingController advanceSettingController, Context context){
        this.advanceSettingController = advanceSettingController;
        this.context = context;

        keyboardLayoutContainer = advanceSettingFatherLayout.findViewById(R.id.keyboard_layout_container);
        addLayoutButton         = advanceSettingFatherLayout.findViewById(R.id.add_layout_button);
        renameConfirmButton     = advanceSettingFatherLayout.findViewById(R.id.rename_window_confirm_button);
        renameCancelButton      = advanceSettingFatherLayout.findViewById(R.id.rename_window_cancel_button);
        renameEditText          = advanceSettingFatherLayout.findViewById(R.id.rename_window_edittext);
        renameWindow            = advanceSettingFatherLayout.findViewById(R.id.rename_layout_window_background);
        addConfirmButton        = advanceSettingFatherLayout.findViewById(R.id.add_window_confirm_button);
        addCancelButton         = advanceSettingFatherLayout.findViewById(R.id.add_window_cancel_button);
        addEditText             = advanceSettingFatherLayout.findViewById(R.id.add_window_edittext);
        addWindow               = advanceSettingFatherLayout.findViewById(R.id.add_layout_window_background);
        confirmDeleteWindow     = advanceSettingFatherLayout.findViewById(R.id.delete_layout_confirm_windows);
        deleteConfirmButton     = advanceSettingFatherLayout.findViewById(R.id.delete_layout_confirm_windows_confirm_button);
        deleteCancelButton      = advanceSettingFatherLayout.findViewById(R.id.delete_layout_confirm_windows_cancel_button);
        deleteConfirmText       = advanceSettingFatherLayout.findViewById(R.id.delete_layout_confirm_windows_text);

        initAddWindow();
        initDeleteWindow();
        initRenameWindow();
    }

    private void initAddWindow(){
        addConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = addEditText.getText().toString();
                if (name.matches("^[a-zA-Z0-9]{1,10}$")){
//                    if (advanceSettingController.getKeyboardLayoutController().addLayout(name) == 0){
//                        addLayoutItem(name);
//                        addWindow.setVisibility(View.GONE);
//                    } else {
//                        Toast.makeText(context,"名称已存在",Toast.LENGTH_SHORT).show();
//                    }
                } else {
                    Toast.makeText(context,"名称只能由1-10个数字、小写字母组成",Toast.LENGTH_SHORT).show();
                }
            }
        });

        addCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addWindow.setVisibility(View.GONE);
            }
        });

        addLayoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addWindow.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initRenameWindow(){
        renameConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = renameEditText.getText().toString();
                if (name.equals(editingLayoutItem.getText())){
                    renameWindow.setVisibility(View.GONE);
                    return;
                }
                if (name.matches("^[a-zA-Z0-9]{1,10}$")){
//                    switch (advanceSettingController.getKeyboardLayoutController().renameLayout(editingLayoutItem.getText(),name)){
//                        case -1 : {
//
//                            Toast.makeText(context,"不能重命名默认布局",Toast.LENGTH_SHORT).show();
//                            break;
//                        }
//                        case -2 : {
//                            Toast.makeText(context,"布局名已存在",Toast.LENGTH_SHORT).show();
//                            break;
//                        }
//                        case 0 : {
//                            editingLayoutItem.setText(name);
//                            if (currentSelectedItem == editingLayoutItem){
//                                advanceSettingController.getKeyboardLayoutController().setCurrentLayoutName(name);
//                            }
//                            renameWindow.setVisibility(View.GONE);
//                            break;
//                        }
//                    }
                } else {
                    Toast.makeText(context,"名称只能由1-10个数字、小写字母组成",Toast.LENGTH_SHORT).show();
                }
            }
        });

        renameCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                renameWindow.setVisibility(View.GONE);
            }
        });
    }

    private void initDeleteWindow(){
        deleteConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                if (advanceSettingController.getKeyboardLayoutController().deleteLayout(editingLayoutItem.getText()) == 0){
//                    keyboardLayoutContainer.removeView(editingLayoutItem.getView());
//                    confirmDeleteWindow.setVisibility(View.GONE);
//                } else {
//                    Toast.makeText(context,"不能删除默认布局",Toast.LENGTH_SHORT).show();
//                }
            }
        });
        deleteCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteWindow.setVisibility(View.GONE);
            }
        });
    }

    private KeyboardLayoutItem addLayoutItem(String layoutName){
        KeyboardLayoutItem keyboardLayoutItem = new KeyboardLayoutItem(this,layoutName,context);

        keyboardLayoutContainer.addView(keyboardLayoutItem.getView(), (keyboardLayoutContainer.getChildCount() - 1));
        return keyboardLayoutItem;
    }

    public void selectItem(KeyboardLayoutItem item){
//        currentSelectedItem.unselected();
//        currentSelectedItem = item;
//        currentSelectedItem.selected();
//        advanceSettingController.getKeyboardLayoutController().setCurrentLayoutName(currentSelectedItem.getText());
//        advanceSettingController.getKeyboardController().refreshLayout();
    }

    public void jumpRenameWindow(KeyboardLayoutItem item){
        renameEditText.setText(item.getText());
        renameWindow.setVisibility(View.VISIBLE);
        editingLayoutItem = item;
    }

    public void jumpDeleteWindow(KeyboardLayoutItem item){
        deleteConfirmText.setText("是否删除布局:" + item.getText());
        confirmDeleteWindow.setVisibility(View.VISIBLE);
        editingLayoutItem = item;
    }

}
