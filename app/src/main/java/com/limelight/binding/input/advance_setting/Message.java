package com.limelight.binding.input.advance_setting;

import java.util.Arrays;
import java.util.Map;

public class Message {

    private String messageTitle;
    private int messageLevel;
    private Class<?> sender;
    private Class<?>[] receivers;
    private Map<String,Object> messageContent;


    public Message(String messageTitle, int messageLevel, Class<?> sender, Class<?>[] receivers, Map<String,Object> messageContent) {
        this.messageTitle = messageTitle;
        this.messageLevel = messageLevel;
        this.sender = sender;
        this.receivers = receivers;
        this.messageContent = messageContent;
    }

    public String getMessageTitle() {
        return messageTitle;
    }

    public int getMessageLevel() {
        return messageLevel;
    }

    public Class<?> getSender() {
        return sender;
    }

    public Class<?>[] getReceiver() {
        return receivers;
    }

    public Map<String,Object> getMessageContent() {
        return messageContent;
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageTitle='" + messageTitle + '\'' +
                ", messageLevel=" + messageLevel +
                ", sender=" + sender +
                ", receivers=" + Arrays.toString(receivers) +
                ", messageContent=" + messageContent +
                '}';
    }
}
