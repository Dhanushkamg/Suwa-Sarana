package com.suwasarana.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates {@code @PreAuthorize} BEFORE Spring MVC reads and validates the request body.
 *
 * Method security only runs once the controller method is about to be invoked, i.e. after the JSON has been parsed and
 * Bean Validation has run. A caller with the wrong role would therefore get "400 validation failed" (and could probe the
 * shape of the DTO) instead of "403". This interceptor applies the very same annotation first, so a forbidden caller is
 * refused with 403 without any of its input being processed. Method security still runs afterwards, unchanged
 * (belt and braces). Expressions that need method arguments (e.g. {@code #id}) cannot be evaluated this early and are left
 * to method security.
 */
@Component
public class AuthorizeBeforeBindingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeBeforeBindingInterceptor.class);

    private final DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final Map<Method, Expression> cache = new ConcurrentHashMap<>();
    private static final Expression NONE = new SpelExpressionParser().parseExpression("true");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Expression expression = cache.computeIfAbsent(handlerMethod.getMethod(), m -> expressionFor(handlerMethod));
        if (expression == NONE) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return true; // URL rules answer 401 for anonymous callers; nothing to add here
        }

        boolean allowed;
        try {
            MethodInvocation invocation = new SimpleMethodInvocation(handlerMethod.getBean(), handlerMethod.getMethod());
            EvaluationContext context = expressionHandler.createEvaluationContext(authentication, invocation);
            allowed = ExpressionUtils.evaluateAsBoolean(expression, context);
        } catch (RuntimeException e) {
            log.debug("@PreAuthorize on {} needs the method arguments; leaving it to method security", handlerMethod.getMethod().getName());
            return true;
        }
        if (!allowed) {
            throw new AccessDeniedException("Access Denied");
        }
        return true;
    }

    private Expression expressionFor(HandlerMethod handlerMethod) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), PreAuthorize.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
        }
        if (annotation == null) {
            return NONE;
        }
        try {
            return parser.parseExpression(annotation.value());
        } catch (RuntimeException e) {
            return NONE;
        }
    }
}
