package com.imqh.usermanagementapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public class PhoneRequest {

    @NotBlank(message = "El número es obligatorio")
    @Schema(description = "Número obligatorio con texto no blanco; no se exige formato numérico.", example = "1234567")
    private String number;

    @NotBlank(message = "El citycode es obligatorio")
    @Schema(description = "Código de ciudad obligatorio con texto no blanco; no se exige formato numérico.", example = "1")
    private String citycode;

    @NotBlank(message = "El contrycode es obligatorio")
    @Schema(description = "Código de país obligatorio con texto no blanco. Se conserva la grafía contrycode; no se exige formato numérico.", example = "57")
    private String contrycode;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getCitycode() {
        return citycode;
    }

    public void setCitycode(String citycode) {
        this.citycode = citycode;
    }

    public String getContrycode() {
        return contrycode;
    }

    public void setContrycode(String contrycode) {
        this.contrycode = contrycode;
    }
}
