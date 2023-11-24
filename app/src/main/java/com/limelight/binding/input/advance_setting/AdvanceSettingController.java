package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerConfigurationLoader;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;

import java.nio.Buffer;

public class AdvanceSettingController {

    private FrameLayout fatherLayout;
    private FrameLayout advanceSettingLayout;
    private FrameLayout elementsLayout;
    private Button setting1Button;
    private Button setting2Button;
    private Button setting3Button;
    //辅助切换设置按钮
    private Button currentSettingButton;
    private LinearLayout setting1Layout;
    private LinearLayout setting2Layout;
    private LinearLayout setting3Layout;
    //辅助切换设置界面
    private LinearLayout currentSettingLayout;
    private RadioGroup advanceSettingRadioGroup;
    private Context context;
    private Button buttonConfigure;
    private KeyboardController keyboardController;
    /**
     * setting2Layout内的成员变量
     */
    private TextView currentPadView;
    public AdvanceSettingController(FrameLayout layout, final Context context) {
        this.fatherLayout = layout;
        this.context = context;
        elementsLayout = new FrameLayout(context);
        keyboardController = new KeyboardController(elementsLayout,context);
        advanceSettingLayout = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.advance_setting_view,null);
        advanceSettingRadioGroup = advanceSettingLayout.findViewById(R.id.advance_setting_radio_group);
        setting1Button = advanceSettingLayout.findViewById(R.id.advance_setting_1_button);
        setting2Button = advanceSettingLayout.findViewById(R.id.advance_setting_2_button);
        setting3Button = advanceSettingLayout.findViewById(R.id.advance_setting_3_button);
        setting1Layout = advanceSettingLayout.findViewById(R.id.advance_setting_1_layout);
        setting2Layout = advanceSettingLayout.findViewById(R.id.advance_setting_2_layout);
        setting3Layout = advanceSettingLayout.findViewById(R.id.advance_setting_3_layout);
        /*
        * 设置界面初始化，最开始是"设置1"被选中,初始化的内容有
        * 1. "设置1"的按钮背景需要设置为黑色，这个在layout中实现
        * 2. "设置1"的界面需要显示，这个在layout中实现
        * 3. 把当前的设置项设置为"设置1"，这个在下面实现
        * */
        currentSettingButton = setting1Button;
        currentSettingLayout = setting1Layout;
        advanceSettingRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.advance_setting_1_button){
                    switchAdvanceSetting(setting1Button,setting1Layout);
                }else if (checkedId == R.id.advance_setting_2_button){
                    switchAdvanceSetting(setting2Button,setting2Layout);
                }else if (checkedId == R.id.advance_setting_3_button){
                    switchAdvanceSetting(setting3Button,setting3Layout);
                }
            }
        });


        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (advanceSettingLayout.getVisibility() == View.VISIBLE){
                    advanceSettingLayout.setVisibility(View.GONE);
                } else {
                    advanceSettingLayout.setVisibility(View.VISIBLE);
                }
            }
        });
        initSetting2Layout();
    }

    private void switchAdvanceSetting(Button settingButton,LinearLayout settingLayout){
        if (currentSettingButton != settingButton){
            currentSettingButton.setBackgroundColor(context.getResources().getColor(R.color.advance_setting_button_background));
            currentSettingLayout.setVisibility(View.GONE);
            currentSettingButton = settingButton;
            currentSettingLayout = settingLayout;
            currentSettingButton.setBackgroundColor(context.getResources().getColor(R.color.advance_setting_background));
            currentSettingLayout.setVisibility(View.VISIBLE);
        }
    }

    private void initSetting2Layout(){
        /**
         * 初始化spinner和drawing
         */
        FrameLayout buttonDrawing = setting2Layout.findViewById(R.id.button_drawing);
        LinearLayout padDrawing = setting2Layout.findViewById(R.id.pad_drawing);
        Spinner elementType = setting2Layout.findViewById(R.id.select_element_type);
        elementType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 1:
                    case 0: {
                        buttonDrawing.setVisibility(View.VISIBLE);
                        padDrawing.setVisibility(View.INVISIBLE);
                        break;
                    }
                    case 2:{
                        buttonDrawing.setVisibility(View.INVISIBLE);
                        padDrawing.setVisibility(View.VISIBLE);
                        break;
                    }

                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        /**
         * 设置十字键的选中逻辑
         */
        TextView padUp = setting2Layout.findViewById(R.id.pad_drawing_up);
        TextView padDown = setting2Layout.findViewById(R.id.pad_drawing_down);
        TextView padLeft = setting2Layout.findViewById(R.id.pad_drawing_left);
        TextView padRight = setting2Layout.findViewById(R.id.pad_drawing_right);
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
        /**
         * 设置keyBoard的按键逻辑
         */
        LinearLayout settingInnerKeyboard = setting2Layout.findViewById(R.id.keyboard_drawing);
        TextView buttonDrawingText = setting2Layout.findViewById(R.id.button_drawing_text);
        View.OnClickListener keyboardListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView keyButton = (TextView) v;
                System.out.println("wg_debug:" + keyButton.getText());
                switch (elementType.getSelectedItemPosition()){
                    case 1:
                    case 0: {
                        //设置Button的内容选择
                        buttonDrawingText.setText(keyButton.getText());
                        //将键盘的keyEvent值传递给drawing中
                        buttonDrawingText.setTag(keyButton.getTag());
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
        /**
         * 设置确定键的逻辑
         */
        EditText addElementName = setting2Layout.findViewById(R.id.add_element_name);
        Button addElementButton = setting2Layout.findViewById(R.id.add_element_button);
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
                        case 1:
                        case 0: {
                            keyboardBean.setSize(200);
                            keyboardBean.setValue(Integer.parseInt((String) buttonDrawingText.getTag()));
                            break;
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
                    if (keyboardController.saveElement(elementId,keyboardBean) == 0){
                        keyboardController.removeElements();
                        keyboardController.loadCurrentLayout();
                        advanceSettingLayout.setVisibility(View.GONE);
                    } else {
                        Toast.makeText(context,"名称已存在",Toast.LENGTH_SHORT).show();
                    }

                } else {
                    Toast.makeText(context,"名称只能由1-10个数字、小写字母组成",Toast.LENGTH_SHORT).show();
                }

            }
        });
    }

    public void refreshLayout(){
        fatherLayout.removeView(buttonConfigure);
        fatherLayout.removeView(advanceSettingLayout);
        fatherLayout.removeView(elementsLayout);

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.rightMargin = 15;
        params.topMargin = 15;
        fatherLayout.addView(elementsLayout);
        fatherLayout.addView(advanceSettingLayout);
        fatherLayout.addView(buttonConfigure, params);

        keyboardController.refreshLayout();
    }
}
