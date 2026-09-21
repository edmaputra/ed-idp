package io.github.edmaputra.edidp.claims;

/**
 * Enumeration of token/endpoint destinations where dynamic claims can be projected.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public enum ClaimTarget {
  USERINFO,
  ID_TOKEN,
  ACCESS_TOKEN
}