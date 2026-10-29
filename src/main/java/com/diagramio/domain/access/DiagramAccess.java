package com.diagramio.domain.access;

import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.UnauthorizedException;
import java.util.UUID;

/**
 * Một chỗ duy nhất trả lời "sơ đồ có tồn tại không và người gọi có quyền không" cho mọi module,
 * để sơ đồ, phiên bản và chia sẻ trả cùng mã lỗi:
 * <ul>
 *   <li>không có sơ đồ: 404;</li>
 *   <li>bị từ chối và người gọi không có tài khoản lẫn link của sơ đồ này: 401 (đăng nhập có thể đổi kết quả);</li>
 *   <li>bị từ chối khi đã biết người gọi (có tài khoản, hoặc link view mà đòi sửa): 403.</li>
 * </ul>
 */
public class DiagramAccess {

    private final DiagramRepository diagramRepository;

    public DiagramAccess(DiagramRepository diagramRepository) {
        this.diagramRepository = diagramRepository;
    }

    /** Đọc sơ đồ và kiểm tra quyền; trả về sơ đồ nếu được phép. */
    public Diagram require(UUID diagramId, Caller caller, Permission permission) {
        if (diagramId == null) {
            throw new DiagramNotFoundException("Diagram not found");
        }
        Diagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + diagramId));
        check(diagram, caller, permission);
        return diagram;
    }

    /** Kiểm tra quyền trên sơ đồ đã đọc sẵn. */
    public void check(Diagram diagram, Caller caller, Permission permission) {
        if (permission.isGrantedTo(caller, diagram)) {
            return;
        }
        if (!caller.hasIdentityFor(diagram)) {
            throw new UnauthorizedException("Authentication required");
        }
        throw new ForbiddenException(permission.deniedMessage());
    }
}
