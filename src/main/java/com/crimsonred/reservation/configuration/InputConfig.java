package com.crimsonred.reservation.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.type.LogicalType;

@Configuration
public class InputConfig {
  @Bean
  JsonMapperBuilderCustomizer strictInputTypes() {
    return builder -> builder
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .withCoercionConfig(LogicalType.Textual, config -> {
          config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
          config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
          config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
  }
}
