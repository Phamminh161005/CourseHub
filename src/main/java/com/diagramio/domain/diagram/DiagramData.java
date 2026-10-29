package com.diagramio.domain.diagram;

import com.diagramio.domain.exception.InvalidInputException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Nội dung JSON của sơ đồ (nodes, edges). Luật "hợp lệ" chỉ nằm ở đây:
 * <ul>
 *   <li>{@link #of(String)} cho dữ liệu người dùng gửi lên: phải là object có mảng "nodes" và "edges",
 *       tối đa 2 MB, sai thì 400;</li>
 *   <li>{@link #fromStorage(String)} cho dữ liệu đọc từ CSDL: đã kiểm tra lúc ghi nên không parse lại
 *       (tránh parse tới 2 MB mỗi lần đọc sơ đồ hay phiên bản).</li>
 * </ul>
 * Snapshot phiên bản và sơ đồ cùng dùng kiểu này nên không thể chứa dữ liệu sai cấu trúc.
 */
public final class DiagramData {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final DiagramData EMPTY = new DiagramData("{\"nodes\": [], \"edges\": []}");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String json;

    private DiagramData(String json) {
        this.json = json;
    }

    /** Dữ liệu từ người dùng: kiểm tra cấu trúc và dung lượng, sai thì InvalidInputException (400). */
    public static DiagramData of(String json) {
        if (json == null || json.isBlank()) {
            throw new InvalidInputException("data must be valid JSON");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidInputException("data must not exceed 2 MB");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidInputException("data must be valid JSON");
        }
        if (root == null || !root.isObject() || !root.path("nodes").isArray() || !root.path("edges").isArray()) {
            throw new InvalidInputException("data must be a JSON object with \"nodes\" and \"edges\" arrays");
        }
        return new DiagramData(json);
    }

    /** Dữ liệu đã lưu trong CSDL (đã được kiểm tra bằng {@link #of} lúc ghi). */
    public static DiagramData fromStorage(String json) {
        return new DiagramData(Objects.requireNonNull(json, "stored diagram data must not be null"));
    }

    public String json() {
        return json;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DiagramData data && json.equals(data.json);
    }

    @Override
    public int hashCode() {
        return json.hashCode();
    }

    @Override
    public String toString() {
        return json;
    }
}
