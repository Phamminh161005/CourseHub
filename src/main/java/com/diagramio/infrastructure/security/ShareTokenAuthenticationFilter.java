package com.diagramio.infrastructure.security;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.share.ShareService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Đọc "X-Share-Token: <token>". Token hợp lệ thì đặt principal là Caller có quyền view/edit trên đúng
 * sơ đồ của link. Chạy sau filter JWT: nếu đã đăng nhập thì gộp userId vào Caller, nhờ vậy owner mở link
 * của chính mình vẫn giữ quyền owner và người sửa đăng nhập được ghi vào updated_by.
 *
 * <p>Giống filter JWT, filter này không tự từ chối request: thiếu, sai hoặc token đã thu hồi thì cho đi
 * tiếp, endpoint tự trả 401/403. Không có header thì giữ nguyên principal UUID của filter JWT.
 */
public class ShareTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Share-Token";

    private final ShareService shareService;

    public ShareTokenAuthenticationFilter(ShareService shareService) {
        this.shareService = shareService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null && !token.isBlank()) {
            shareService.resolveShareToken(token).ifPresent(share -> {
                Caller caller = new Caller(currentUserId(), share.sharedDiagramId(), share.shareRole());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(caller, null, List.of()));
            });
        }
        chain.doFilter(request, response);
    }

    /** userId do filter JWT đặt vào trước đó, null nếu chưa đăng nhập. */
    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UUID userId ? userId : null;
    }
}
