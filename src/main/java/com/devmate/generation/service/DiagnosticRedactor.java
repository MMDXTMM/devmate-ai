package com.devmate.generation.service;
import java.util.regex.Pattern;
public final class DiagnosticRedactor {
 private DiagnosticRedactor() { }
 public static String redact(String value){
  String s=value
    .replaceAll("(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+)\\S+","$1[REDACTED]")
    .replaceAll("(?i)((?:password|passwd|pwd|api[_-]?key|access[_-]?token|refresh[_-]?token|secret|token)[\"']?\\s*[:=]\\s*[\"']?)[^\\s,;\"'}]+","$1[REDACTED]")
    .replaceAll("sk-(?:proj-)?[A-Za-z0-9_-]{12,}","[REDACTED]")
    .replaceAll("(https?://)[^/@\\s]+:[^/@\\s]+@","$1[REDACTED]@");
  return Pattern.compile("-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----",Pattern.DOTALL).matcher(s).replaceAll("[REDACTED PRIVATE KEY]");
 }
}
