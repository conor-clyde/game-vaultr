package com.cocoding.playstate.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaythroughsPayloadDeserTest {

    @Test
    void deserializesShortName() throws Exception {
        String json =
                "{\"items\":[{\"id\":1,\"shortName\":\"Blind\",\"difficulty\":\"Normal\",\"current\":true,\"manualPlayMinutes\":null,\"progressNote\":null,\"progressStatus\":\"PLAYING\",\"endDate\":null,\"runType\":\"REPLAY\"}]}";
        ObjectMapper om = new ObjectMapper();
        PlaythroughsPayload p = om.readValue(json, PlaythroughsPayload.class);
        assertEquals("Blind", p.items().get(0).shortName());
        assertEquals("PLAYING", p.items().get(0).progressStatus());
        assertEquals("REPLAY", p.items().get(0).runType());
        assertNull(p.items().get(0).completionPercent());
    }

    @Test
    void deserializesWhenProgressFieldsOmitted() throws Exception {
        String json =
                "{\"items\":[{\"id\":1,\"shortName\":null,\"difficulty\":\"\",\"current\":true,\"manualPlayMinutes\":null}]}";
        ObjectMapper om = new ObjectMapper();
        PlaythroughsPayload p = om.readValue(json, PlaythroughsPayload.class);
        assertNull(p.items().get(0).progressNote());
        assertNull(p.items().get(0).progressStatus());
        assertNull(p.items().get(0).completionPercent());
    }
}
