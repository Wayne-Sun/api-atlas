package com.api.atlas.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class StatusUpdateDTO {
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "Status must be ENABLED or DISABLED")
    private String status;

    public StatusUpdateDTO() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
