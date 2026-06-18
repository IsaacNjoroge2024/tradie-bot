package com.tradie.common.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Converter
public class TagsConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return String.join(",", tags);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return Collections.emptyList();
        return Arrays.asList(dbData.split(","));
    }
}
