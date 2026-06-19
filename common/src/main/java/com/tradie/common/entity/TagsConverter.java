package com.tradie.common.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;

@Converter
public class TagsConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(tags);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize tags", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return Collections.emptyList();
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize tags: " + dbData, e);
        }
    }
}
