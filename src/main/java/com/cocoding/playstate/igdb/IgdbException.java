package com.cocoding.playstate.igdb;

public class IgdbException extends RuntimeException {

  public static final String MESSAGE =
      "Game search is temporarily unavailable. Please try again in a moment.";
  public static final String MISSING_CREDENTIALS_MESSAGE =
      "Search is unavailable locally: IGDB credentials are missing. Set IGDB_CLIENT_ID and"
          + " IGDB_CLIENT_SECRET.";
  public static final String INVALID_CREDENTIALS_MESSAGE =
      "Search is unavailable locally: IGDB credentials are invalid. Verify igdb.client.id and"
          + " igdb.client.secret.";

  public IgdbException(Throwable cause) {
    super(cause);
  }

  public static String userMessage(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      String message = t.getMessage();
      if (message == null) {
        continue;
      }
      String normalized = message.toLowerCase();
      if (normalized.contains("credentials are missing")
          || normalized.contains("missing client id")) {
        return MISSING_CREDENTIALS_MESSAGE;
      }
      if (normalized.contains("invalid client")) {
        return INVALID_CREDENTIALS_MESSAGE;
      }
    }
    return MESSAGE;
  }
}
