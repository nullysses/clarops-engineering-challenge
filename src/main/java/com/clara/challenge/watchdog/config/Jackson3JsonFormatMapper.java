package com.clara.challenge.watchdog.config;

import java.io.IOException;
import java.lang.reflect.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

  private final JsonMapper objectMapper;

  public Jackson3JsonFormatMapper() {
    this(JsonMapper.builder().build());
  }

  Jackson3JsonFormatMapper(JsonMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected <T> T fromString(CharSequence charSequence, Type type) {
    try {
      return objectMapper.readValue(charSequence.toString(), objectMapper.constructType(type));
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Could not deserialize JSON for Java type " + type, ex);
    }
  }

  @Override
  protected <T> String toString(T value, Type type) {
    try {
      return objectMapper.writerFor(objectMapper.constructType(type)).writeValueAsString(value);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException("Could not serialize JSON for Java type " + type, ex);
    }
  }

  @Override
  public <T> void writeToTarget(
      T value, JavaType<T> javaType, Object target, WrapperOptions wrapperOptions)
      throws IOException {
    objectMapper
        .writerFor(objectMapper.constructType(javaType.getJavaType()))
        .writeValue((JsonGenerator) target, value);
  }

  @Override
  public <T> T readFromSource(JavaType<T> javaType, Object source, WrapperOptions wrapperOptions)
      throws IOException {
    return objectMapper.readValue(
        (JsonParser) source, objectMapper.constructType(javaType.getJavaType()));
  }

  @Override
  public boolean supportsSourceType(Class<?> sourceType) {
    return JsonParser.class.isAssignableFrom(sourceType);
  }

  @Override
  public boolean supportsTargetType(Class<?> targetType) {
    return JsonGenerator.class.isAssignableFrom(targetType);
  }
}
