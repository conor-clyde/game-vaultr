package com.cocoding.playstate.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReflectionTagsJson {

  private ReflectionTagsJson() {}

  public static List<String> deserialize(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    String s = json.trim();
    if (!s.startsWith("[") || !s.endsWith("]")) {
      return List.of();
    }
    String inner = s.substring(1, s.length() - 1).trim();
    if (inner.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    int i = 0;
    while (i < inner.length()) {
      while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) {
        i++;
      }
      if (i >= inner.length()) {
        break;
      }
      if (inner.charAt(i) != '"') {
        return List.of();
      }
      i++;
      StringBuilder sb = new StringBuilder();
      while (i < inner.length()) {
        char c = inner.charAt(i);
        if (c == '\\' && i + 1 < inner.length()) {
          char n = inner.charAt(i + 1);
          if (n == '"' || n == '\\' || n == '/') {
            sb.append(n);
            i += 2;
          } else if (n == 'n') {
            sb.append('\n');
            i += 2;
          } else {
            sb.append(n);
            i += 2;
          }
        } else if (c == '"') {
          i++;
          break;
        } else {
          sb.append(c);
          i++;
        }
      }
      String t = sb.toString().trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
      while (i < inner.length() && Character.isWhitespace(inner.charAt(i))) {
        i++;
      }
      if (i < inner.length() && inner.charAt(i) == ',') {
        i++;
      }
    }
    return List.copyOf(out);
  }

  private static String escapeJsonString(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  public static String normalizeTag(String raw, int maxLen) {
    if (raw == null) {
      return "";
    }
    String s = raw.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) {
      return "";
    }
    s = s.replaceAll("[\\s_]+", "-");
    s = s.replaceAll("[^a-z0-9-]+", "");
    s = s.replaceAll("-+", "-");
    s = s.replaceAll("^-+", "");
    s = s.replaceAll("-+$", "");
    if (s.length() > maxLen) {
      s = s.substring(0, maxLen);
      s = s.replaceAll("-+$", "");
    }
    return s;
  }

  public static String toDisplayLabel(String normalized) {
    if (normalized == null || normalized.isBlank()) {
      return "";
    }
    String s = normalized.trim().toLowerCase(Locale.ROOT);
    s = s.replaceAll("[\s_]+", "-");
    s = s.replaceAll("-+", "-");
    s = s.replaceAll("^-+", "");
    s = s.replaceAll("-+$", "");
    return s;
  }

  public static String serializeList(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    final int maxTags = 6;
    final int maxLen = 30;
    List<String> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String t : tags) {
      if (out.size() >= maxTags) {
        break;
      }
      String n = normalizeTag(t, maxLen);
      if (n.isEmpty() || seen.contains(n)) {
        continue;
      }
      seen.add(n);
      out.add(n);
    }
    if (out.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < out.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append('"').append(escapeJsonString(out.get(i))).append('"');
    }
    return sb.append(']').toString();
  }

  public static String serializeValidated(String jsonFromClient, int maxTags, int maxLenPerTag) {
    List<String> raw = deserialize(jsonFromClient);
    List<String> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String t : raw) {
      if (out.size() >= maxTags) {
        break;
      }
      String n = normalizeTag(t, maxLenPerTag);
      if (n.isEmpty() || seen.contains(n)) {
        continue;
      }
      seen.add(n);
      out.add(n);
    }
    return serializeList(out);
  }
}
