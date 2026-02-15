package com.eventdelivery.platform.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String httpMethod;

    @Column(columnDefinition = "TEXT")
    private String headers; // stored as JSON

    @Column(name = "signing_secret")
    private String signingSecret;

    @Column(name = "rate_limit_rps")
    private Integer rateLimitRps; // req/sec

    @CreationTimestamp
    private LocalDateTime createdAt;

    public Destination() {
    }

    public Destination(String name, String url, String httpMethod, String headers, String signingSecret,
            Integer rateLimitRps) {
        this.name = name;
        this.url = url;
        this.httpMethod = httpMethod;
        this.headers = headers;
        this.signingSecret = signingSecret;
        this.rateLimitRps = rateLimitRps != null ? rateLimitRps : 10;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
