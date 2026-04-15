package com.cocoding.playstate.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class SearchGameRow {

    public long id;
    public String name;
    
    public Long firstReleaseDateEpoch;

    public final List<Platform> platforms = new ArrayList<>();

    public Double popScore;
    public Boolean exactMatch;
    public Boolean prefixMatch;
    public List<String> discoverySignals;
    public Boolean inCollection;

    @JsonIgnore
    public transient Double totalRating;
    @JsonIgnore
    public transient String coverImageId;

    @JsonProperty("releaseYear")
    public Integer getReleaseYear() {
        if (firstReleaseDateEpoch == null) {
            return null;
        }
        return Instant.ofEpochSecond(firstReleaseDateEpoch).atZone(ZoneOffset.UTC).getYear();
    }

    @JsonGetter("reviewScore")
    public Integer getReviewScore() {
        if (totalRating == null) {
            return null;
        }
        int score = (int) Math.round(totalRating);
        return score > 0 ? score : null;
    }

    @JsonGetter("coverImageUrl")
    public String getCoverImageUrl() {
        if (coverImageId == null || coverImageId.isBlank()) {
            return null;
        }
        return "https://images.igdb.com/igdb/image/upload/t_cover_big/" + coverImageId + ".jpg";
    }

    @JsonGetter("platformNamesJson")
    public String getPlatformNamesJson() {
        if (platforms.isEmpty()) {
            return null;
        }
        List<String> displayNames = new ArrayList<>(platforms.size());
        for (Platform p : platforms) {
            String d = p.getDisplayName();
            if (d != null && !d.isBlank()) {
                displayNames.add(d);
            }
        }
        if (displayNames.isEmpty()) {
            return null;
        }
        return jsonArrayOfStrings(displayNames);
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class Platform {
        public String name;

        public Platform() {
        }

        public Platform(String name) {
            this.name = name;
        }

        
        @JsonGetter("displayName")
        public String getDisplayName() {
            return formatPlatformDisplayName(name);
        }
    }

    private static String formatPlatformDisplayName(String apiName) {
        if (apiName == null) {
            return null;
        }
        return "PC (Microsoft Windows)".equalsIgnoreCase(apiName.trim()) ? "Windows PC" : apiName;
    }

    private static String jsonArrayOfStrings(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            String s = list.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(s).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    public static SearchGameRow fromIgdbMap(Map<String, Object> map) {
        SearchGameRow g = new SearchGameRow();
        if (map == null) {
            return g;
        }
        Object idObj = map.get("id");
        if (idObj instanceof Number) {
            g.id = ((Number) idObj).longValue();
        }
        Object nameObj = map.get("name");
        g.name = nameObj != null ? nameObj.toString() : null;

        Object dateObj = map.get("first_release_date");
        if (dateObj instanceof Number) {
            g.firstReleaseDateEpoch = ((Number) dateObj).longValue();
        }

        Object ratingObj = map.get("total_rating");
        if (ratingObj instanceof Number) {
            g.totalRating = ((Number) ratingObj).doubleValue();
        }

        Object coverObj = map.get("cover");
        if (coverObj instanceof Map<?, ?> coverMap) {
            Object imageId = coverMap.get("image_id");
            if (imageId != null) {
                g.coverImageId = imageId.toString();
            }
        }

        Object platformsObj = map.get("platforms");
        if (platformsObj instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof Map<?, ?> pm) {
                    Object n = pm.get("name");
                    g.platforms.add(new Platform(n != null ? n.toString() : null));
                }
            }
        }

        return g;
    }

    public static List<SearchGameRow> fromIgdbMaps(List<Map<String, Object>> maps) {
        if (maps == null || maps.isEmpty()) {
            return new ArrayList<>();
        }
        List<SearchGameRow> out = new ArrayList<>(maps.size());
        for (Map<String, Object> m : maps) {
            out.add(fromIgdbMap(m));
        }
        return out;
    }

}
