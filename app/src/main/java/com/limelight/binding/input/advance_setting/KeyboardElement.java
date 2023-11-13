package com.limelight.binding.input.advance_setting;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

public abstract class KeyboardElement extends View {

    public KeyboardElement(Context context){
        super(context);

    }

    public void moveElement(){

    }

    public void resizeElement(){

    }

    public void setOpacity(){

    }

    public void setColors(){

    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

}
