package com.binitech.imports.domain.exception;

public class TenantRequiredException extends RuntimeException {
  public TenantRequiredException() {
    super(
        "Esta conta não está vinculada a uma loja. Para importar produtos, entre com um usuário da loja de destino.");
  }
}
