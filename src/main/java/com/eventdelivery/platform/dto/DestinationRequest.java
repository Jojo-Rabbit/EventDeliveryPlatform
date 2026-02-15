package com.eventdelivery.platform.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public class DestinationRequest {
    @NotBlank
    private String name;

    @NotBlank
    @URL
    private String url;

    @NotBlank
    private String httpMethod;

    private String headers;

    private String signingSecret;
    private Integer rateLimitRps;

    public DestinationRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public Integer getRateLimitRps() {
        return rateLimitRps;
    }

    public void setRateLimitRps(Integer rateLimitRps) {
        this.rateLimitRps = rateLimitRps;
    }
}
