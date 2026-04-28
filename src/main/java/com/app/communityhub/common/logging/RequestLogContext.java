package com.app.communityhub.common.logging;

public final class RequestLogContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String REQUEST_ID_ATTRIBUTE = RequestLogContext.class.getName() + ".requestId";
    public static final String AUTHENTICATED_USER_ID_ATTRIBUTE = RequestLogContext.class.getName() + ".userId";
    public static final String AUTHENTICATED_USERNAME_ATTRIBUTE = RequestLogContext.class.getName() + ".username";

    private RequestLogContext() {
    }
}
