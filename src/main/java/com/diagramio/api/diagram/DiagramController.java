package com.diagramio.api.diagram;

import com.diagramio.api.CallerResolver;
import com.diagramio.api.diagram.dto.CreateDiagramRequest;
import com.diagramio.api.diagram.dto.DiagramResponse;
import com.diagramio.api.diagram.dto.DiagramSummaryResponse;
import com.diagramio.api.diagram.dto.UpdateDiagramRequest;
import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.api.dto.PageResponse;
import com.diagramio.config.OpenApiConfig;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/diagrams")
@Tag(name = "Diagrams", description = "Quản lý sơ đồ UML/ERD: tạo, xem, sửa (optimistic locking), xóa")
@ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class DiagramController {

    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    @GetMapping
    @Operation(summary = "Danh sách sơ đồ của tôi",
            description = "Lọc theo loại (UML/ERD), tìm theo tên, phân trang (size tối đa 100).")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "200", description = "Một trang danh sách sơ đồ (không kèm data)")
    public PageResponse<DiagramSummaryResponse> getMyDiagrams(
            @Parameter(description = "Tìm theo tên (không phân biệt hoa thường)") @RequestParam(required = false) String title,
            @Parameter(description = "UML hoặc ERD") @RequestParam(required = false) String diagramType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID ownerId = CallerResolver.requireUserId(authentication);
        List<Diagram> diagrams = diagramService.getDiagramsByOwner(ownerId, diagramType, title, page, size);
        long total = diagramService.countDiagramsByOwner(ownerId, diagramType, title);
        return pageResponse(diagrams, page, size, total);
    }

    @PostMapping
    @Operation(summary = "Tạo sơ đồ mới",
            description = "Sơ đồ mới luôn riêng tư, data mặc định {\"nodes\": [], \"edges\": []}. Người tạo là owner.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "201", description = "Đã tạo sơ đồ")
    public ResponseEntity<DiagramResponse> create(
            @Valid @RequestBody CreateDiagramRequest request,
            Authentication authentication) {
        Diagram diagram = new Diagram();
        diagram.setTitle(request.title());
        diagram.setDiagramType(request.diagramType());
        diagram.setOwnerId(CallerResolver.requireUserId(authentication));
        Diagram created = diagramService.createDiagram(diagram);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(DiagramResponse.from(created));
    }

    @GetMapping("/public")
    @Operation(summary = "Danh sách sơ đồ công khai", description = "Không cần đăng nhập.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Một trang danh sách sơ đồ công khai (không kèm data)")
    public PageResponse<DiagramSummaryResponse> getPublicDiagrams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Diagram> diagrams = diagramService.getPublicDiagrams(page, size);
        return pageResponse(diagrams, page, size, diagramService.countPublicDiagrams());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết sơ đồ kèm data",
            description = "Sơ đồ công khai: ai cũng xem được. Riêng tư: owner hoặc người có link chia sẻ (view/edit).")
    @ApiResponse(responseCode = "200", description = "Chi tiết sơ đồ")
    @ApiResponse(responseCode = "403", description = "Không có quyền xem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Không tìm thấy sơ đồ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public DiagramResponse getById(@PathVariable UUID id, Authentication authentication) {
        return DiagramResponse.from(diagramService.getDiagramById(id, CallerResolver.resolveOrAnonymous(authentication)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật sơ đồ",
            description = "Owner hoặc người có link edit. Bắt buộc gửi version đang thấy; lệch thì 409. "
                    + "data phải là object có mảng nodes và edges, tối đa 2 MB. Chỉ owner đổi được isPublic.")
    @ApiResponse(responseCode = "200", description = "Đã cập nhật, version tăng 1")
    @ApiResponse(responseCode = "403", description = "Không có quyền sửa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Không tìm thấy sơ đồ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Sơ đồ đã bị người khác sửa (version lệch)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public DiagramResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiagramRequest request,
            Authentication authentication) {
        return DiagramResponse.from(diagramService.updateDiagram(
                id, request.title(), request.diagramType(), request.data().toString(), request.isPublic(),
                CallerResolver.resolve(authentication), request.version()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa sơ đồ", description = "Chỉ owner.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @ApiResponse(responseCode = "204", description = "Đã xóa")
    @ApiResponse(responseCode = "403", description = "Không phải owner",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Không tìm thấy sơ đồ",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        diagramService.deleteDiagram(id, CallerResolver.resolve(authentication));
        return ResponseEntity.noContent().build();
    }

    private PageResponse<DiagramSummaryResponse> pageResponse(
            List<Diagram> diagrams, int page, int size, long total) {
        List<DiagramSummaryResponse> content = diagrams.stream()
                .map(DiagramSummaryResponse::from)
                .toList();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
