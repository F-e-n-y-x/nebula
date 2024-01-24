package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.funtion.AddElement;
import com.limelight.binding.input.advance_setting.funtion.DeleteElement;
import com.limelight.binding.input.advance_setting.funtion.EditElement;
import com.limelight.binding.input.advance_setting.funtion.FloatKeyboard;
import com.limelight.binding.input.advance_setting.funtion.OtherSetting;
import com.limelight.binding.input.advance_setting.funtion.SelectLayout;

public class AdvanceSettingController {
    /**
     * 不同层的layout成员
     */
    private FrameLayout fatherLayout;
    private FrameLayout advanceSettingLayoutFather;
    private FrameLayout elementsLayout;
    private LinearLayout advanceSettingLayout;
    private FrameLayout keyboardFrameLayout;
    private Button buttonConfigure;
    private Context context;
    private ElementController elementController;
    private ElementLayoutController elementLayoutController;

    private AdvanceSettingPreference advanceSettingPreference;
    private ElementPreference elementPreference;

    private AddElement addElement;
    private EditElement editElement;
    private DeleteElement deleteElement;
    private FloatKeyboard floatKeyboard;
    private SelectLayout selectLayout;
    private OtherSetting otherSetting;
    public AdvanceSettingController(FrameLayout layout, final Context context) {
        this.fatherLayout = layout;
        this.context = context;


        advanceSettingPreference = new AdvanceSettingPreference(context);


        advanceSettingLayoutFather = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.advance_setting_view_back,null);
        elementsLayout = new FrameLayout(context);
        keyboardFrameLayout = (FrameLayout) LayoutInflater.from(context).inflate(R.layout.float_keyboard,null);
        advanceSettingLayout = advanceSettingLayoutFather.findViewById(R.id.advance_setting_layout);

        elementLayoutController = new ElementLayoutController(this,context);

        addElement = new AddElement(advanceSettingLayoutFather.findViewById(R.id.advance_setting_1_linear_layout),elementController,elementPreference,context);
        editElement = new EditElement(advanceSettingLayoutFather,this);
        deleteElement = new DeleteElement(advanceSettingLayoutFather,this);
        floatKeyboard = new FloatKeyboard(advanceSettingLayoutFather,keyboardFrameLayout,this);
        selectLayout = new SelectLayout(advanceSettingLayoutFather,this,context);
        otherSetting = new OtherSetting(advanceSettingLayoutFather,this);

        buttonConfigure = new Button(context);
        buttonConfigure.setAlpha(0.25f);
        buttonConfigure.setFocusable(false);
        buttonConfigure.setBackgroundResource(R.drawable.ic_settings);
        buttonConfigure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (advanceSettingLayoutFather.getVisibility() == View.VISIBLE){
                    advanceSettingLayoutFather.setVisibility(View.GONE);
                } else {
                    advanceSettingLayoutFather.setVisibility(View.VISIBLE);
                }
            }
        });

        initSetting();
    }



    public String getAdvancePreference(String key){
        return advanceSettingPreference.getValue(key);
    }

    public void setAdvancePreference(String key, String value){
        advanceSettingPreference.setValue(key,value);
    }
    public ElementController getKeyboardController() {
        return elementController;
    }

    public ElementLayoutController getKeyboardLayoutController() {
        return elementLayoutController;
    }

    public EditElement getEditElement() {
        return editElement;
    }

    public DeleteElement getDeleteElement() {
        return deleteElement;
    }

    public FloatKeyboard getFloatKeyboard() {
        return floatKeyboard;
    }

    public void setAdvanceSettingLayoutVisibility(int visibility){
        advanceSettingLayout.setVisibility(visibility);
    }

    public void setButtonConfigureVisibility(int visibility){
        buttonConfigure.setVisibility(visibility);
    }

    private void initSetting(){

        Button setting1Button = advanceSettingLayoutFather.findViewById(R.id.advance_setting_1_radio_button);
        Button setting2Button = advanceSettingLayoutFather.findViewById(R.id.advance_setting_2_radio_button);
        LinearLayout setting1Layout = advanceSettingLayoutFather.findViewById(R.id.advance_setting_1_linear_layout);
        LinearLayout setting2Layout = advanceSettingLayoutFather.findViewById(R.id.advance_setting_2_linear_layout);
        ((RadioGroup) advanceSettingLayoutFather.findViewById(R.id.advance_setting_radio_group)).setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            /*
             * 设置界面初始化，最开始是"设置1"被选中,初始化的内容有
             * 1. "设置1"的按钮背景需要设置为黑色，这个在layout中实现
             * 2. "设置1"的界面需要显示，这个在layout中实现
             * 3. 把当前的设置项设置为"设置1"，这个在下面实现
             * */
            private LinearLayout currentSettingLayout = setting1Layout;
            private Button currentSettingButton = setting1Button;
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.advance_setting_1_radio_button){
                    switchAdvanceSetting(setting1Button,setting1Layout);
                }else if (checkedId == R.id.advance_setting_2_radio_button){
                    switchAdvanceSetting(setting2Button,setting2Layout);
                }
            }
            private void switchAdvanceSetting(Button settingButton, LinearLayout settingLayout){
                if (currentSettingButton != settingButton){
                    currentSettingButton.setBackgroundColor(context.getResources().getColor(R.color.advance_setting_button_background));
                    currentSettingLayout.setVisibility(View.GONE);
                    currentSettingButton = settingButton;
                    currentSettingLayout = settingLayout;
                    currentSettingButton.setBackgroundColor(context.getResources().getColor(R.color.advance_setting_background));
                    currentSettingLayout.setVisibility(View.VISIBLE);
                }
            }

        });
    }

    public void refreshLayout(){
        fatherLayout.removeView(buttonConfigure);
        fatherLayout.removeView(advanceSettingLayoutFather);
        fatherLayout.removeView(elementsLayout);
        fatherLayout.removeView(keyboardFrameLayout);

        DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int buttonSize = (int)(screen.heightPixels*0.06f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonSize, buttonSize);
        params.rightMargin = 15;
        params.topMargin = 15;

        fatherLayout.addView(elementsLayout);
        fatherLayout.addView(keyboardFrameLayout);
        fatherLayout.addView(advanceSettingLayoutFather);
        fatherLayout.addView(buttonConfigure, params);


        elementController.refreshLayout();
    }
}
