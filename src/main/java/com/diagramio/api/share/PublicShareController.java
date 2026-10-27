package com.diagramio.api.share;

import com.diagramio.domain.share.SharedDiagramView;
import com.diagramio.domain.share.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shares")
@Tag(name = "Public Sharing", description = "Truy xuất sơ đồ công khai thông qua mã chia sẻ (Không yêu cầu đăng nhập)")
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Truy xuất thông tin và cấu trúc sơ đồ qua token chia sẻ", description = "Ai có token hợp lệ đều có thể xem cấu trúc sơ đồ và quyền tương ứng (view/edit).")
    public ResponseEntity<SharedDiagramResponse> getSharedDiagram(@PathVariable("token") String token) {
        SharedDiagramView diagramView = shareService.getDiagramByShareToken(token);
        return ResponseEntity.ok(SharedDiagramResponse.fromDomain(diagramView));
    }
}
