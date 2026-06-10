package com.dejebu.protocol;

import com.fasterxml.jackson.databind.JsonNode;

public class GameMessage {

    private MessageType type;
    private JsonNode payload;

    public GameMessage() {
    }

    public GameMessage(MessageType type, JsonNode payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
