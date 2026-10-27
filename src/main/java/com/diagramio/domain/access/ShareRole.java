package com.diagramio.domain.access;

import com.diagramio.domain.exception.InvalidInputException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Mức quyền của một liên kết chia sẻ. Đây là nơi duy nhất kiểm tra giá trị role:
 * API và DB dùng chuỗi thường ("view", "edit"), mọi nơi khác dùng enum.
 * Thêm role mới: thêm hằng ở đây và sửa CHECK của cột diagram_shares.role bằng migration.
 */
public enum ShareRole {
    VIEW("view"),
    EDIT("edit");

    private final String value;

    ShareRole(String value) {
        this.value = value;
    }

    /** Giá trị dùng trong JSON và DB. */
    public String value() {
        return value;
    }

    /** Đổi chuỗi (không phân biệt hoa thường) thành enum; sai thì 400. */
    public static ShareRole from(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (ShareRole role : values()) {
                if (role.value.equals(normalized)) {
                    return role;
                }
            }
        }
        throw new InvalidInputException("role must be one of "
                + Arrays.stream(values()).map(r -> "'" + r.value + "'").toList());
    }
}
