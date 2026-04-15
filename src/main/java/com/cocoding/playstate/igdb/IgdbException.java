package com.cocoding.playstate.igdb;

public class IgdbException extends RuntimeException {

    
    public static final String MESSAGE = "Game search is temporarily unavailable. Please try again in a moment.";

    public IgdbException(Throwable cause) {
        super(cause);
    }
}
