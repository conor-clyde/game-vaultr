package com.cocoding.playstate.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PlayLogSessionExperienceAttributeConverter
    implements AttributeConverter<PlayLogSessionExperience, String> {

  @Override
  public String convertToDatabaseColumn(PlayLogSessionExperience attribute) {
    return attribute == null ? null : attribute.name();
  }

  @Override
  public PlayLogSessionExperience convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return PlayLogSessionExperience.fromParam(dbData);
  }
}
