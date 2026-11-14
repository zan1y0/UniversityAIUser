package com.kaoyan.ai.api.security;
import static com.kaoyan.ai.common.Jsons.id;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.service.auth.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
/** 仅在SecurityFilterChain中运行，将已校验的JWT/旧会话转为Spring Security身份。 */
public class SessionFilter extends OncePerRequestFilter {
  static final Set<String> PUBLIC = Set.of("/api/v1/session/csrf", "/api/v1/session/login", "/api/v1/auth/login",
      "/api/v1/auth/register", "/api/v1/auth/email/code", "/api/v1/auth/password/reset",
      "/api/v1/auth/invitation/check", "/api/v1/auth/invitation/activate");
  public static String path(HttpServletRequest request) {
    return request.getRequestURI().substring(request.getContextPath().length());
  }
  private final JwtService jwt;
  private final PermissionService permissions;
  public SessionFilter(JwtService jwt,PermissionService permissions) { this.jwt=jwt;this.permissions=permissions; }
  protected void doFilterInternal(HttpServletRequest r,HttpServletResponse w,FilterChain chain) throws ServletException,IOException {
    r.setAttribute("traceId",id());
    boolean publicRoute = PUBLIC.contains(path(r));
    try {
      if(path(r).startsWith("/api/v1/") && !publicRoute && !r.getMethod().equals("OPTIONS")) {
        String user = null, role = null;
        Object version = null;
        var token=jwt.token(r);
        if(token.isPresent()) {
          var verified=jwt.verify(token.get()); user=verified.getSubject();
          role = verified.getClaimAsString("activeRole");
          version = verified.getClaims().get("authVersion");
        } else {
          var session=r.getSession(false);
          if(session!=null) { user=(String)session.getAttribute("userId");
            if(session.getAttribute("activeRole") instanceof String selected)role=selected;
            version = session.getAttribute("authVersion"); }
        }
        if(user==null)throw new ApiException(401,"UNAUTHENTICATED","请先登录");
        var granted = permissions.validate(user, role, version);
        var authorities=new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_"+role));
        granted.forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new RequestIdentity(user,role),null,authorities));
        SecurityContextHolder.setContext(context);
      }
      chain.doFilter(r,w);
    } catch(ApiException e) { SecurityErrors.write(r,w,e.status(),e.code(),e.getMessage()); }
  }
}
