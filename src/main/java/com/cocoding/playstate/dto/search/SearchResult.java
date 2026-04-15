package com.cocoding.playstate.dto.search;

import java.util.List;

public final class SearchResult {

  public final List<SearchGameRow> results;
  public final boolean hasMore;
  public final String igdbError;

  public SearchResult(List<SearchGameRow> results, boolean hasMore, String igdbError) {
    this.results = results;
    this.hasMore = hasMore;
    this.igdbError = igdbError;
  }
}
