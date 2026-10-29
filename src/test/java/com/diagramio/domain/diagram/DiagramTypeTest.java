package com.diagramio.domain.diagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diagramio.domain.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

class DiagramTypeTest {

    @Test
    void fromAcceptsExactNames() {
        assertThat(DiagramType.from("UML")).isEqualTo(DiagramType.UML);
        assertThat(DiagramType.from("ERD")).isEqualTo(DiagramType.ERD);
    }

    @Test
    void fromRejectsUnknownBlankNullAndWrongCaseWith400() {
        for (String value : new String[] {"BPMN", "", "uml", " ERD", null}) {
            assertThatThrownBy(() -> DiagramType.from(value))
                    .as(String.valueOf(value))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessage("diagramType must be UML or ERD");
        }
    }
}
