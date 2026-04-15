package com.cocoding.playstate.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PlaythroughProgressStatusConverter implements AttributeConverter<PlaythroughProgressStatus, String> {

    @Override
    public String convertToDatabaseColumn(PlaythroughProgressStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public PlaythroughProgressStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String s = dbData.trim().toUpperCase();
        if ("IN_PROGRESS".equals(s)) {
            return PlaythroughProgressStatus.PLAYING;
        }
        if ("DROPPED".equals(s)) {
            return PlaythroughProgressStatus.STOPPED;
        }
        try {
            return PlaythroughProgressStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return PlaythroughProgressStatus.PLAYING;
        }
    }
}
