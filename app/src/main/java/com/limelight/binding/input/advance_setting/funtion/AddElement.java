package com.limelight.binding.input.advance_setting.funtion;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.binding.input.advance_setting.ElementBean;
import com.limelight.binding.input.advance_setting.ElementController;
import com.limelight.binding.input.advance_setting.ElementPreference;
import com.limelight.binding.input.advance_setting.funtion.add_element.ButtonCard;
import com.limelight.binding.input.advance_setting.funtion.add_element.ElementCard;

public class AddElement {

    final private LinearLayout advanceSettingLayout1;
    final private LinearLayout elementCardBox;
    private ElementCard elementCard;
    final private Context context;
    final private ElementController elementController;
    final private ElementPreference elementPreference;
    final private EditText elementName;
    final private EditText elementLength;
    final private EditText elementHigh;
    final private EditText elementX;
    final private EditText elementY;


    public AddElement(LinearLayout advanceSettingLayout1, ElementController elementController, ElementPreference elementPreference, Context context){
        this.context = context;
        this.advanceSettingLayout1 = advanceSettingLayout1;
        this.elementController = elementController;
        this.elementPreference = elementPreference;
        elementCardBox  = advanceSettingLayout1.findViewById(R.id.element_card_box);
        elementName     = advanceSettingLayout1.findViewById(R.id.element_name_edittext);
        elementLength   = advanceSettingLayout1.findViewById(R.id.element_width_edittext);
        elementHigh     = advanceSettingLayout1.findViewById(R.id.element_height_edittext);
        elementX        = advanceSettingLayout1.findViewById(R.id.element_x_edittext);
        elementY        = advanceSettingLayout1.findViewById(R.id.element_y_edittext);
        ((Spinner) advanceSettingLayout1.findViewById(R.id.element_type_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 1:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 2:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 3:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 4:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 5:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 6:{
                        elementCard = new ButtonCard();
                        break;
                    }
                    case 7:{
                        elementCard = new ButtonCard();
                        break;
                    }

                }
                turnCard();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        advanceSettingLayout1.findViewById(R.id.element_add_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ElementBean elementBean = new ElementBean();

                String name = elementName.getText().toString();
                int positionX = Integer.parseInt(elementX.getText().toString());
                int positionY = Integer.parseInt(elementY.getText().toString());
                int width = Integer.parseInt(elementLength.getText().toString());
                int height = Integer.parseInt(elementHigh.getText().toString());

                if (elementPreference.containsElement(name)){
                    Toast.makeText(context,"名称已存在",Toast.LENGTH_SHORT).show();
                    return;
                }

                if (elementController.positionIsInvalid(positionX,positionY)){
                    Toast.makeText(context,
                            "X或Y超出范围，X:" + elementController.getPositionScale()[0] +
                                    "~" + elementController.getPositionScale()[1] +
                                    "Y:" + elementController.getPositionScale()[2] +
                                    "~" + elementController.getPositionScale()[3]
                            ,Toast.LENGTH_SHORT).show();
                    return;
                }

                if (elementController.sizeIsInvalid(width,height)){
                    Toast.makeText(context,
                            "Width或Height超出范围，Width:" + elementController.getSizeScale()[0] +
                                    "~" + elementController.getSizeScale()[1] +
                                    "High:" + elementController.getSizeScale()[2] +
                                    "~" + elementController.getSizeScale()[3]
                            ,Toast.LENGTH_SHORT).show();
                    return;
                }


                elementBean.setName(name);
                elementBean.setType(elementCard.getType());
                elementBean.setTypeAttributes(elementCard.getTypeAttribute());
                elementBean.setPositionX(positionX);
                elementBean.setPositionY(positionY);
                elementBean.setWidth(width);
                elementBean.setHeight(height);
                elementBean.setOpacity(100);
                elementBean.setColor(0);
                elementBean.setLayer(1);
                elementBean.setOtherAttributes(null);

                elementPreference.addElement(elementBean);
                elementController.addElement(elementBean);


            }
        });
    }

    private void turnCard(){
        elementCardBox.addView(elementCard.getLayout());
    }

}
