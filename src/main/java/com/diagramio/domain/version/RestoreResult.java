package com.diagramio.domain.version;

import com.diagramio.domain.diagram.Diagram;

/** Kết quả khôi phục: sơ đồ sau khi khôi phục và snapshot sao lưu trạng thái ngay trước đó. */
public record RestoreResult(Diagram diagram, DiagramVersion backup) {
}
