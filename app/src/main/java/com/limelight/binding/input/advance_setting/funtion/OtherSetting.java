package com.limelight.binding.input.advance_setting.funtion;

import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Switch;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.AdvanceSettingController;

public class OtherSetting {

    private AdvanceSettingController advanceSettingController;

    public OtherSetting(FrameLayout advanceSettingFatherLayout, AdvanceSettingController advanceSettingController){
        this.advanceSettingController = advanceSettingController;

        initFirstColumn();
    }

    private void initFirstColumn(){
    }
}
