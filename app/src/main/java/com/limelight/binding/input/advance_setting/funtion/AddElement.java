package com.limelight.binding.input.advance_setting.funtion;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.AdvanceSettingController;
import com.limelight.binding.input.advance_setting.KeyboardBean;
import com.limelight.binding.input.advance_setting.KeyboardController;

public class AddElement {

    private FrameLayout buttonDrawing;
    private FrameLayout switchDrawing;
    private LinearLayout padDrawing;
    private Spinner elementType;
    private TextView padUp;
    private TextView padDown;
    private TextView padLeft;
    private TextView padRight;
    private LinearLayout settingInnerKeyboard;
    private TextView buttonDrawingText;
    private TextView switchDrawingText;
    private EditText addElementName;
    private Button addElementButton;

    private TextView currentPadView;
    private Context context;
    private AdvanceSettingController advanceSettingController;


    public AddElement(FrameLayout advanceSettingLayout, AdvanceSettingController advanceSettingController, Context context){
        this.context = context;
        this.advanceSettingController = advanceSettingController;
        buttonDrawing           = advanceSettingLayout.findViewById(R.id.button_drawing);
        switchDrawing           = advanceSettingLayout.findViewById(R.id.switch_drawing);
        padDrawing              = advanceSettingLayout.findViewById(R.id.pad_drawing);
        elementType             = advanceSettingLayout.findViewById(R.id.select_element_type);
        padUp                   = advanceSettingLayout.findViewById(R.id.pad_drawing_up);
        padDown                 = advanceSettingLayout.findViewById(R.id.pad_drawing_down);
        padLeft                 = advanceSettingLayout.findViewById(R.id.pad_drawing_left);
        padRight                = advanceSettingLayout.findViewById(R.id.pad_drawing_right);
        settingInnerKeyboard    = advanceSettingLayout.findViewById(R.id.keyboard_drawing);
        buttonDrawingText       = advanceSettingLayout.findViewById(R.id.button_drawing_text);
        switchDrawingText       = advanceSettingLayout.findViewById(R.id.switch_drawing_text);
        addElementName          = advanceSettingLayout.findViewById(R.id.add_element_name);
        addElementButton        = advanceSettingLayout.findViewById(R.id.add_element_button);

        initSpinner();
        initPad();
        initKeyboard();
        initAddElementButton();
    }
    /**
     * 初始化Element类型选择
     */
    private void initSpinner(){
        elementType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0: {
                        buttonDrawing.setVisibility(View.VISIBLE);
                        switchDrawing.setVisibility(View.INVISIBLE);
                        padDrawing.setVisibility(View.INVISIBLE);
                        break;
                    }
                    case 1: {
                        buttonDrawing.setVisibility(View.INVISIBLE);
                        switchDrawing.setVisibility(View.VISIBLE);
                        padDrawing.setVisibility(View.INVISIBLE);
                        break;
                    }
                    case 2:{
                        buttonDrawing.setVisibility(View.INVISIBLE);
                        switchDrawing.setVisibility(View.INVISIBLE);
                        padDrawing.setVisibility(View.VISIBLE);
                        break;
                    }

                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }
    /**
     * 设置十字键的选中逻辑
     */
    private void initPad(){
        //默认选中padUp
        currentPadView = padUp;
        //给四个键设置onClick方法，点击时聚焦到那个View中，并将currentPadView设置为选中的View。
        View.OnClickListener padListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                padUp.setBackgroundResource(R.drawable.square_border);
                padDown.setBackgroundResource(R.drawable.square_border);
                padLeft.setBackgroundResource(R.drawable.square_border);
                padRight.setBackgroundResource(R.drawable.square_border);
                currentPadView = (TextView) v;
                v.setBackgroundResource(R.drawable.confirm_square_border);
            }
        };
        padUp.setOnClickListener(padListener);
        padDown.setOnClickListener(padListener);
        padLeft.setOnClickListener(padListener);
        padRight.setOnClickListener(padListener);
    }
    /**
     * 设置keyBoard的按键逻辑
     */
    private void initKeyboard(){
        View.OnClickListener keyboardListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView keyButton = (TextView) v;
                switch (elementType.getSelectedItemPosition()){
                    case 0: {
                        //设置Button的内容选择
                        buttonDrawingText.setText(keyButton.getText());
                        //将键盘的keyEvent值传递给drawing中
                        buttonDrawingText.setTag(keyButton.getTag());
                        break;
                    }
                    case 1: {
                        switchDrawingText.setText(keyButton.getText());
                        switchDrawingText.setTag(keyButton.getTag());
                        break;
                    }
                    case 2:{
                        //设置Pad其中一个按键的内容选中
                        currentPadView.setText(keyButton.getText());
                        currentPadView.setTag(keyButton.getTag());
                        break;
                    }
                }
            }
        };
        for (int i = 0; i < settingInnerKeyboard.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) settingInnerKeyboard.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnClickListener(keyboardListener);
            }
        }
    }
    /**
     * 设置确定键的逻辑
     */
    private void initAddElementButton(){
        addElementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String elementId = addElementName.getText().toString();
                if (elementId.matches("^[a-zA-Z0-9]{1,6}$")){
                    int type = elementType.getSelectedItemPosition();
                    KeyboardBean keyboardBean = new KeyboardBean();
                    keyboardBean.setLayer(1);
                    keyboardBean.setPositionX(0);
                    keyboardBean.setPositionY(100);
                    keyboardBean.setOpacity(100);
                    keyboardBean.setType(type);
                    switch (type){
                        case 0: {
                            keyboardBean.setSize(200);
                            keyboardBean.setValue(Integer.parseInt((String) buttonDrawingText.getTag()));
                            break;
                        }
                        case 1: {
                            keyboardBean.setSize(200);
                            keyboardBean.setValue(Integer.parseInt((String) switchDrawingText.getTag()));
                        }
                        case 2:{
                            keyboardBean.setSize(400);
                            keyboardBean.setValueUp(Integer.parseInt((String) padUp.getTag()));
                            keyboardBean.setValueDown(Integer.parseInt((String) padDown.getTag()));
                            keyboardBean.setValueLeft(Integer.parseInt((String) padLeft.getTag()));
                            keyboardBean.setValueRight(Integer.parseInt((String) padRight.getTag()));
                            break;
                        }
                    }
                    System.out.println("wg_debug:" + keyboardBean);
                    if (advanceSettingController.getKeyboardController().addElement(elementId, keyboardBean)){
                        Toast.makeText(context,"创建成功",Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context,"名称已存在",Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context,"名称只能由1-6个数字、小写字母组成",Toast.LENGTH_SHORT).show();
                }

            }
        });
    }

}
