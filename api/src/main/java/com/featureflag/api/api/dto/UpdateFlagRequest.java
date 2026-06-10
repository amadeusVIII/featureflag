package com.featureflag.api.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class UpdateFlagRequest {


    @Size(max = 200)
    private String name;

    private String description;

    // null means "don't change this field"
    private Boolean enabled;

    @Min(0) @Max(100)
    private Integer rolloutPercentage;

    private String stringValue;
    
}
