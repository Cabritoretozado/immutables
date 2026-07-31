package org.immutables.fixture.builder;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.immutables.check.Checkers.check;

public class DerivedAttributeBuilderTest {

  @Test
  public void derivedAttributeOfABuildableTypeIsUsable() {
    DerivedAttributeBuilderParent.Holder holder = ImmutableHolder.builder()
        .primary(ImmutablePayload.builder().name("a").build())
        .secondary(ImmutablePayload.builder().name("b").build())
        .build();

    check(holder.computed().name()).is("ab");
  }

  @Test
  public void attributeBuilderHelpersAreNamedAfterTheType() {
    Set<String> helpers = Stream.of(ImmutableHolder.class.getDeclaredMethods())
        .map(Method::getName)
        .filter(name -> name.endsWith("ValueOf") || name.endsWith("ToBuilder"))
        .collect(Collectors.toSet());

    check(!helpers.isEmpty());
    for (String helper : helpers) {
      check(helper).startsWith(ImmutablePayload.class.getName().replace('.', '_'));
    }
  }
}
