package com.binitech.imports.domain.exception;

public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String resource, String id) {
    super(resource + " não encontrado(a): " + id);
  }
}
