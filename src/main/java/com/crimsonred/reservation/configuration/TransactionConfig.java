package com.crimsonred.reservation.configuration;

import com.crimsonred.reservation.shared.application.StorageConflict;
import com.crimsonred.reservation.shared.application.port.TransactionRunner;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionConfig {
  @Bean
  TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
    return new TransactionTemplate(manager);
  }

  @Bean
  TransactionRunner transactions(TransactionTemplate template) {
    return new TransactionRunner() {
      public <T> T required(java.util.function.Supplier<T> action) {
        try {
          return template.execute(status -> action.get());
        } catch (DataIntegrityViolationException error) {
          throw new StorageConflict(error);
        }
      }
    };
  }
}
