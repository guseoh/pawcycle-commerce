package com.pawcycle.backend.foundation.persistence;

import java.sql.SQLException;
import org.springframework.dao.DuplicateKeyException;

/** Classifies persistence failures without weakening database constraints. */
public final class PersistenceExceptionClassifier {
  private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;

  private PersistenceExceptionClassifier() {}

  public static boolean isDuplicateKey(Throwable failure) {
    Throwable current = failure;
    while (current != null) {
      if (current instanceof DuplicateKeyException) return true;
      if (current instanceof SQLException sqlException
          && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR) {
        return true;
      }
      if (current.getCause() == current) break;
      current = current.getCause();
    }
    return false;
  }
}
