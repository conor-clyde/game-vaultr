package com.cocoding.playstate.dto;

import java.util.List;

public record PlayLogPageJson(List<PlayLogJsonRow> items, boolean hasMore) {
}
