package com.binitech.imports.application.service;

import com.binitech.imports.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class BrazilianNumberParser {
  public BigDecimal money(String raw) {
    BigDecimal value = decimal(raw);
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  public BigDecimal decimal(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String value = raw.trim().replace("R$", "").replace("\u00a0", "").replace(" ", "");
    int comma = value.lastIndexOf(',');
    int dot = value.lastIndexOf('.');
    if (comma >= 0 && dot >= 0) {
      if (comma > dot) value = value.replace(".", "").replace(',', '.');
      else value = value.replace(",", "");
    } else if (comma >= 0) {
      value = value.replace('.', '\0').replace(",", ".").replace("\0", "");
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw new BusinessException("Número inválido: " + raw);
    }
  }

  public Integer integer(String raw) {
    BigDecimal value = decimal(raw);
    if (value == null) return null;
    try {
      return value.intValueExact();
    } catch (ArithmeticException exception) {
      throw new BusinessException("Quantidade deve ser um número inteiro: " + raw);
    }
  }
}
