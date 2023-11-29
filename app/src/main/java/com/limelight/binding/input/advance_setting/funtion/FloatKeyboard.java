package com.limelight.binding.input.advance_setting.funtion;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowId;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.AdvanceSettingController;
import com.limelight.binding.input.advance_setting.KeyboardController;

public class FloatKeyboard {

    private static final String ENABLE_KEYBOARD = "enable_keyboard";

    private AdvanceSettingController advanceSettingController;

    private Button displayKeyboardButton;
    private SeekBar opacitySeekbar;
    private LinearLayout keyboardLayout;
    private LinearLayout keyboard;
    private Switch enableKeyboardSwitch;
    private FrameLayout keyboardFatherLayout;
    public FloatKeyboard(FrameLayout advanceSettingFatherLayout, FrameLayout keyboardFatherLayout, AdvanceSettingController advanceSettingController){
        this.advanceSettingController = advanceSettingController;
        this.keyboardFatherLayout = keyboardFatherLayout;

        displayKeyboardButton   = keyboardFatherLayout.findViewById(R.id.float_keyboard_button);
        opacitySeekbar          = keyboardFatherLayout.findViewById(R.id.float_keyboard_seekbar);
        keyboardLayout          = keyboardFatherLayout.findViewById(R.id.float_keyboard);
        keyboard                = keyboardFatherLayout.findViewById(R.id.keyboard_drawing);
        enableKeyboardSwitch    = advanceSettingFatherLayout.findViewById(R.id.keyboard_enable_switch);

        initPreference();
        loadPreference();
        initButton();
        initSeekbar();
        initKeyboard();
        initEnableFloatKeyboard();
    }

    private void initKeyboard(){
        KeyboardController keyboardController = advanceSettingController.getKeyboardController();
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 处理按下事件
                        keyboardController.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,Integer.parseInt((String) v.getTag())));
                        v.setBackgroundResource(R.drawable.confirm_square_border);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // 处理释放事件
                        keyboardController.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,Integer.parseInt((String) v.getTag())));
                        v.setBackgroundResource(R.drawable.square_border);
                        return true;
                }
                return false;
            }
        };
        for (int i = 0; i < keyboard.getChildCount(); i++){
            LinearLayout keyboardRow = (LinearLayout) keyboard.getChildAt(i);
            for (int j = 0; j < keyboardRow.getChildCount(); j++){
                keyboardRow.getChildAt(j).setOnTouchListener(touchListener);
            }
        }
    }

    private void initButton(){
        displayKeyboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (displayKeyboardButton.getText().equals("↑")){
                    opacitySeekbar.setVisibility(View.VISIBLE);
                    keyboardLayout.setVisibility(View.VISIBLE);
                    displayKeyboardButton.setText("↓");
                } else {
                    opacitySeekbar.setVisibility(View.GONE);
                    keyboardLayout.setVisibility(View.GONE);
                    displayKeyboardButton.setText("↑");
                }

            }
        });
    }

    private void initSeekbar(){
        opacitySeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float alpha = (float) (progress * 0.1);
                keyboardLayout.setAlpha(alpha);
                displayKeyboardButton.setAlpha(alpha);
                opacitySeekbar.setAlpha(alpha);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void initEnableFloatKeyboard(){
        enableKeyboardSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                advanceSettingController.setAdvancePreference(ENABLE_KEYBOARD,String.valueOf(isChecked));
                enableKeyboard(isChecked);
            }
        });

    }

    private void enableKeyboard(boolean enable){
        if (enable) {
            keyboardFatherLayout.setVisibility(View.VISIBLE);
        } else {
            keyboardFatherLayout.setVisibility(View.GONE);
        }
    }

    private void initPreference(){
        //初始化键盘enable设置
        if (advanceSettingController.getAdvancePreference(ENABLE_KEYBOARD) == null){
            advanceSettingController.setAdvancePreference(ENABLE_KEYBOARD,String.valueOf(false));
        }
    }

    private void loadPreference(){
        //加载键盘enable设置
        boolean isEnable = Boolean.parseBoolean(advanceSettingController.getAdvancePreference(ENABLE_KEYBOARD));
        enableKeyboardSwitch.setChecked(isEnable);
        enableKeyboard(isEnable);
    }

}
