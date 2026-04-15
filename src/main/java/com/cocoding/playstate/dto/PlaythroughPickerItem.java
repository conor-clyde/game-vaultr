package com.cocoding.playstate.dto;

public record PlaythroughPickerItem(
        long id,
        String label,
        boolean active,
        Integer manualPlayMinutes,
        String shortName,
        String progressNote) {}
