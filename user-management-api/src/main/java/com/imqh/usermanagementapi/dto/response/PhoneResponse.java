package com.imqh.usermanagementapi.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record PhoneResponse(
        @Schema(example = "1234567", requiredMode = Schema.RequiredMode.REQUIRED) String number,
        @JsonProperty("citycode") @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) String cityCode,
        @JsonProperty("contrycode") @Schema(example = "57", requiredMode = Schema.RequiredMode.REQUIRED) String countryCode) {
}
