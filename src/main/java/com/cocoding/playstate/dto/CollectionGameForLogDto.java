package com.cocoding.playstate.dto;

public record CollectionGameForLogDto(
        String apiId,
        String title,
        String imageUrl,
        String platform,
        int displayPlayMinutes,
        boolean hasPlayLogs,
        
        String lastPlayedRelative,
        long playSessionCount,
        
        String lastPlayedCalendarLine,
        
        String totalPlayTimeLabel,
        
        int playthroughCount) {}
