package com.kaoyan.ai.api.security;
import static com.kaoyan.ai.common.Jsons.id;
import jakarta.servlet.http.*;
import org.springframework.security.web.csrf.*;
/** 复用原SESSION内csrf属性及X-CSRF-Token契约，由Spring Security执行校验。 */
public class SessionCsrfTokenRepository implements CsrfTokenRepository {
  public CsrfToken generateToken(HttpServletRequest r) { return new DefaultCsrfToken("X-CSRF-Token","_csrf",id()+id()); }
  public void saveToken(CsrfToken token,HttpServletRequest r,HttpServletResponse w) {
    if(token==null) { var s=r.getSession(false); if(s!=null)s.removeAttribute("csrf"); }
    else r.getSession(true).setAttribute("csrf",token.getToken());
  }
  public CsrfToken loadToken(HttpServletRequest r) {
    var s=r.getSession(false);String value=s==null?null:(String)s.getAttribute("csrf");
    return value==null?null:new DefaultCsrfToken("X-CSRF-Token","_csrf",value);
  }
}
