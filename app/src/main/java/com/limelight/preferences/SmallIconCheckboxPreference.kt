package com.limelight.preferences

import android.content.Context
import android.content.res.TypedArray
import androidx.preference.CheckBoxPreference
import android.util.AttributeSet
import androidx.preference.R as PreferenceR

class SmallIconCheckboxPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.core.content.res.TypedArrayUtils.getAttr(
        context,
        PreferenceR.attr.checkBoxPreferenceStyle,
        android.R.attr.checkBoxPreferenceStyle
    )
) : CheckBoxPreference(context, attrs, defStyleAttr) {

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return PreferenceConfiguration.getDefaultSmallMode(context)
    }
}
