package com.diagramio.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diagramio.domain.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

class ShareRoleTest {

    @Test
    void parsesCaseInsensitivelyAndTrims() {
        assertThat(ShareRole.from("view")).isEqualTo(ShareRole.VIEW);
        assertThat(ShareRole.from(" Edit ")).isEqualTo(ShareRole.EDIT);
    }

    @Test
    void rejectsUnknownOrMissingRole() {
        assertThatThrownBy(() -> ShareRole.from("admin"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("'view'")
                .hasMessageContaining("'edit'");
        assertThatThrownBy(() -> ShareRole.from(null)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void valueIsWhatApiAndDatabaseStore() {
        assertThat(ShareRole.VIEW.value()).isEqualTo("view");
        assertThat(ShareRole.EDIT.value()).isEqualTo("edit");
    }
}
