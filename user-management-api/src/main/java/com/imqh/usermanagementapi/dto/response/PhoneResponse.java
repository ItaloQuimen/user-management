package com.imqh.usermanagementapi.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PhoneResponse(
        String number,
        @JsonProperty("citycode") String cityCode,
        @JsonProperty("contrycode") String countryCode) {
}
