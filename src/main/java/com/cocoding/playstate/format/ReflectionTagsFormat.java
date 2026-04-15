package com.cocoding.playstate.format;

import com.cocoding.playstate.util.ReflectionTagsJson;
import org.springframework.stereotype.Component;

@Component("reflectionTags")
public class ReflectionTagsFormat {

  public String toDisplayLabel(String normalized) {
    return ReflectionTagsJson.toDisplayLabel(normalized);
  }
}
