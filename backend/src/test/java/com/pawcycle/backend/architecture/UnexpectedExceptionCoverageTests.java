package com.pawcycle.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;

/**
 * Every REST controller must have an advice that turns an unexpected exception into the common
 * {@code ApiErrorResponse} (with {@code requestId}) instead of Spring's default error body
 * (stage 2, O11).
 */
class UnexpectedExceptionCoverageTests {
  private static final String ROOT = "com.pawcycle.backend";

  @Test
  void everyControllerHasAnUnexpectedExceptionHandler() throws Exception {
    List<Class<?>> controllers = scan(RestController.class);
    List<Class<?>> catchAllAdvices = scan(ControllerAdvice.class).stream()
        .filter(UnexpectedExceptionCoverageTests::handlesException)
        .toList();
    assertThat(controllers).isNotEmpty();
    assertThat(catchAllAdvices).isNotEmpty();

    Set<String> uncovered = new TreeSet<>();
    for (Class<?> controller : controllers) {
      if (catchAllAdvices.stream().noneMatch(advice -> predicate(advice).test(controller))) {
        uncovered.add(controller.getName().substring(ROOT.length() + 1));
      }
    }
    assertThat(uncovered).as("Controllers without a catch-all exception handler").isEmpty();
  }

  private static boolean handlesException(Class<?> advice) {
    for (Method method : advice.getDeclaredMethods()) {
      ExceptionHandler handler = method.getAnnotation(ExceptionHandler.class);
      if (handler == null) continue;
      List<Class<? extends Throwable>> types = handler.value().length > 0
          ? Arrays.asList(handler.value())
          : Arrays.stream(method.getParameterTypes())
              .filter(Throwable.class::isAssignableFrom)
              .<Class<? extends Throwable>>map(type -> type.asSubclass(Throwable.class))
              .toList();
      if (types.contains(Exception.class) || types.contains(Throwable.class)) return true;
    }
    return false;
  }

  private static HandlerTypePredicate predicate(Class<?> advice) {
    ControllerAdvice annotation = AnnotatedElementUtils.findMergedAnnotation(advice, ControllerAdvice.class);
    return HandlerTypePredicate.builder()
        .basePackage(annotation.basePackages())
        .basePackageClass(annotation.basePackageClasses())
        .assignableType(annotation.assignableTypes())
        .annotation(annotation.annotations())
        .build();
  }

  private static List<Class<?>> scan(Class<? extends java.lang.annotation.Annotation> type) throws Exception {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(type));
    List<Class<?>> result = new java.util.ArrayList<>();
    for (var candidate : scanner.findCandidateComponents(ROOT)) {
      result.add(Class.forName(candidate.getBeanClassName()));
    }
    return result;
  }
}
