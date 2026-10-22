package com.diagramio.domain.auth;

import com.diagramio.domain.user.User;

/** Phát hành access token (JWT); cài đặt bằng thư viện jjwt ở tầng ngoài. */
public interface TokenIssuer {

    String issueAccessToken(User user);
}
