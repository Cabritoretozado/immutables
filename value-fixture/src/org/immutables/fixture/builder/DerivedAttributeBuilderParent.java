package org.immutables.fixture.builder;

import org.immutables.value.Value;

@Value.Style(attributeBuilderDetection = true)
public interface DerivedAttributeBuilderParent {

  @Value.Immutable
  interface Payload {
    String name();
  }

  // Payload is reached under three attribute names, one of which is derived. Attribute builder
  // helpers are emitted per type, so all three must resolve to the same helper.
  @Value.Immutable
  @Value.Style(attributeBuilderDetection = true)
  interface Holder {
    Payload primary();

    Payload secondary();

    @Value.Derived
    default Payload computed() {
      return ImmutablePayload.builder().name(primary().name() + secondary().name()).build();
    }
  }
}
