package com.motus.motusbot.model;

public enum ButtonCallBack {
    REPAIR_BUTTON("REPAIR_BUTTON", "1.запись на ремонт"),
    PARTS_BUTTON("PARTS_BUTTON", "2.подбор запчастей"),
    OPERATOR_BUTTON("OPERATOR_BUTTON", "3.позвать оператора"),
    BACK_BUTTON("BACK_BUTTON", "⬅️Назад");

    private final String id;
    private final String label;

    ButtonCallBack(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
