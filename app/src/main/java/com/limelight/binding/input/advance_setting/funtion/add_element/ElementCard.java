package com.limelight.binding.input.advance_setting.funtion.add_element;

import android.widget.LinearLayout;

import java.util.Map;

public abstract class ElementCard {
    abstract public Map<String,String> getTypeAttribute();

    abstract public LinearLayout getLayout();
    abstract public int getType();
}
