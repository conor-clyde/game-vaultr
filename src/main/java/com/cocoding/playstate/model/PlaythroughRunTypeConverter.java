package com.cocoding.playstate.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PlaythroughRunTypeConverter implements AttributeConverter<PlaythroughRunType, String> {

  @Override
  public String convertToDatabaseColumn(PlaythroughRunType attribute) {
    return attribute == null ? null : attribute.name();
  }

  @Override
  public PlaythroughRunType convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return null;
    }
    try {
      return PlaythroughRunType.valueOf(dbData.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return PlaythroughRunType.FIRST_TIME;
    }
  }
}
