package com.cocoding.playstate.dto.playthrough;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlaythroughsPayload(List<PlaythroughItem> items) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PlaythroughItem(
      Long id,
      String shortName,
      String difficulty,
      boolean current,
      Integer manualPlayMinutes,
      Integer completionPercent,
      String progressNote,
      String progressStatus,
      String endDate) {}
}
