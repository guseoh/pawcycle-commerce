package com.pawcycle.backend.common.external;

import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;

/**
 * Maps a provider-call failure to a {@link FailureClass} by walking its cause chain. Anything not
 * recognised as a timeout, network, provider or parse failure is {@link FailureClass#INTERNAL}, so a
 * defect in our code is never reported as a provider outage.
 */
public final class ExternalFailureClassifier {
  private ExternalFailureClassifier() {}

  public static FailureClass classify(Throwable failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = failure;
        current != null && seen.add(current);
        current = current.getCause()) {
      FailureClass known = direct(current);
      if (known != null) return known;
    }
    return FailureClass.INTERNAL;
  }

  private static FailureClass direct(Throwable failure) {
    if (failure instanceof ExternalCallException classified) return classified.failureClass();
    // HttpConnectTimeoutException is an HttpTimeoutException, but the provider was never reached.
    if (failure instanceof HttpConnectTimeoutException) return FailureClass.CONNECT;
    if (failure instanceof HttpTimeoutException
        || failure instanceof InterruptedIOException
        || failure instanceof TimeoutException
        || failure instanceof InterruptedException) return FailureClass.TIMEOUT;
    if (failure instanceof OpenAIServiceException service)
      return service.statusCode() >= 500 ? FailureClass.PROVIDER_5XX : FailureClass.PROVIDER_REJECTED;
    if (failure instanceof OpenAIInvalidDataException || failure instanceof JacksonException)
      return FailureClass.PARSE;
    // Checked after the parse types: a JSON read failure may also be an IOException.
    if (failure instanceof IOException) return FailureClass.CONNECT;
    return null;
  }
}
