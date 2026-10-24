package com.lectus.api;
public final class Digests {
 private Digests() {}
 public static String sha256(String value) {
  try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
}