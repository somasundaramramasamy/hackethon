package com.cognizant.backend.config;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import com.cognizant.backend.settings.AppProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** In read-only mode (the public demo), refuses every request that would change data or call a model. */
class ReadOnlyInterceptor implements HandlerInterceptor {

    private final AppProperties app;

    ReadOnlyInterceptor(AppProperties app) {
        this.app = app;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod();
        if (!app.isReadOnly() || HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Read-Only Demo\",\"status\":403,\"detail\":"
                + "\"This is a read-only demo with recorded results. Run Kavasam locally to start runs, "
                + "review answers or approve fixes.\"}");
        return false;
    }
}
