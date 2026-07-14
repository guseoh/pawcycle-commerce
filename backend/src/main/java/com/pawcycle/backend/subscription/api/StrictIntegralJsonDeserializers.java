package com.pawcycle.backend.subscription.api;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public final class StrictIntegralJsonDeserializers {

	private StrictIntegralJsonDeserializers() {
	}

	public static final class LongValue extends StdDeserializer<Long> {

		public LongValue() {
			super(Long.class);
		}

		@Override
		public Long deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
			if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(Long.class, "정수 JSON 값이어야 합니다.");
			}
			return parser.getLongValue();
		}
	}

	public static final class IntegerValue extends StdDeserializer<Integer> {

		public IntegerValue() {
			super(Integer.class);
		}

		@Override
		public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
			if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(Integer.class, "정수 JSON 값이어야 합니다.");
			}
			return parser.getIntValue();
		}
	}
}
