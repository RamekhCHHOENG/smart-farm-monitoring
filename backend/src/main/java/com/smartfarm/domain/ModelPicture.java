package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A model's uploaded photo, in its own table so listing the catalog never loads the image bytes.
 * Stored as VARBINARY (bytea on PostgreSQL), at most 2 MB.
 */
@Entity
@Table(name = "device_model_picture")
public class ModelPicture {

    public static final int MAX_BYTES = 2_000_000;

    @Id
    private Long modelId;

    @Column(length = 60)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(length = MAX_BYTES)
    private byte[] bytes;

    protected ModelPicture() {
    }

    public ModelPicture(Long modelId, String contentType, byte[] bytes) {
        this.modelId = modelId;
        replace(contentType, bytes);
    }

    public void replace(String contentType, byte[] bytes) {
        if (contentType == null || !contentType.matches("image/(png|jpeg|webp|gif)")) {
            throw new IllegalArgumentException("The picture must be a PNG, JPEG, WebP or GIF image");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("The picture must be at most 2 MB");
        }
        this.contentType = contentType;
        this.bytes = bytes;
    }

    public Long getModelId() { return modelId; }
    public String getContentType() { return contentType; }
    public byte[] getBytes() { return bytes; }
}
