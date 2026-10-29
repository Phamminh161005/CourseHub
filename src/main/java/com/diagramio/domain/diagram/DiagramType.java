package com.diagramio.domain.diagram;

import com.diagramio.domain.exception.InvalidInputException;

/**
 * Loại sơ đồ. API và DB dùng đúng tên hằng ("UML", "ERD"); mọi chỗ khác trong domain dùng enum.
 * Thêm loại mới: thêm hằng ở đây và sửa CHECK của cột diagrams.diagram_type bằng migration.
 */
public enum DiagramType {
    UML,
    ERD;

    /** Đổi chuỗi từ request thành enum (phân biệt hoa thường như đặc tả); sai thì 400. */
    public static DiagramType from(String value) {
        for (DiagramType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        throw new InvalidInputException("diagramType must be UML or ERD");
    }
}
