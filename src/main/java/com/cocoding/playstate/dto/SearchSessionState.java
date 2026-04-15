package com.cocoding.playstate.dto;

import com.cocoding.playstate.igdb.IgdbConstants;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

public class SearchSessionState implements Serializable {

    private static final long serialVersionUID = 1L;

    public final Set<String> platforms = new LinkedHashSet<>();
    public int yearMin = IgdbConstants.RELEASE_YEAR_MIN;
    public int yearMax = IgdbConstants.RELEASE_YEAR_MAX;

    public void clearFiltersOnly() {
        platforms.clear();
        yearMin = IgdbConstants.RELEASE_YEAR_MIN;
        yearMax = IgdbConstants.RELEASE_YEAR_MAX;
    }
}
