package com.diagramio.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu tham số {@code UUID} của controller nhận id người dùng đã đăng nhập.
 * Endpoint cần tài khoản (thông tin cá nhân, sơ đồ của tôi, tạo sơ đồ, quản lý link) dùng tham số này:
 * khách hoặc người chỉ cầm token chia sẻ sẽ nhận 401. Do {@link CallerArgumentResolver} cung cấp.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
