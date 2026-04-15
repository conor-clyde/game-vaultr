package com.cocoding.playstate.model;

public enum OwnershipType {
    PHYSICAL("Physical"),
    DIGITAL("Digital"),
    SUBSCRIPTION("Subscription"),
    FREE("Free"),
    UNOWNED("Unowned");

    private final String displayName;

    OwnershipType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

