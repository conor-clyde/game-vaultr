package com.cocoding.playstate.dto.playthrough;

public record PlaythroughPickerItem(
    long id,
    String label,
    boolean active,
    Integer manualPlayMinutes,
    String shortName,
    String progressNote) {}
