package com.imqh.usermanagementapi.dto.request;

import jakarta.validation.constraints.NotBlank;

public class PhoneRequest {

    @NotBlank(message = "El número es obligatorio")
    private String number;

    @NotBlank(message = "El citycode es obligatorio")
    private String citycode;

    @NotBlank(message = "El contrycode es obligatorio")
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
