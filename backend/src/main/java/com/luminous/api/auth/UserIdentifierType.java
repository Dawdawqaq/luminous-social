package com.luminous.api.auth;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** 显式区分用户标识命名空间；兼容模式遇到不同用户冲突时拒绝猜测目标。 */
public enum UserIdentifierType {
    AUTO, USERNAME, ID;

    public static UserIdentifierType parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_USER_IDENTIFIER_TYPE",
                    "identifierType 必须为 auto、username 或 id");
        }
    }
}
