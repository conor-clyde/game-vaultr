package com.cocoding.playstate.dto.playlog;

import java.util.List;

public record PlayLogPageJson(List<PlayLogJsonRow> items, boolean hasMore) {}
