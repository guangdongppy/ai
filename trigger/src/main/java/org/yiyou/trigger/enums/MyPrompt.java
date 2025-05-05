package org.yiyou.trigger.enums;


public enum MyPrompt {
    SYSTEM_PROMPT("系统提示词", """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """);
    private final String shorthand;
    private final String value;
    MyPrompt(String shorthand, String value) {
        this.shorthand = shorthand;
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
