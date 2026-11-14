package com.kaoyan.ai.api.security;
import static com.kaoyan.ai.common.Jsons.*;
import jakarta.servlet.http.*;
import java.io.IOException;
public final class SecurityErrors {
  private SecurityErrors() {}
  public static void write(HttpServletRequest r,HttpServletResponse w,int status,String code,String message) throws IOException {
    if(r.getAttribute("traceId")==null)r.setAttribute("traceId",id());
    w.setStatus(status);w.setContentType("application/json;charset=UTF-8");
    w.getWriter().write(obj("code",code,"message",message,"data",null,"traceId",r.getAttribute("traceId"),"details",arr()).toString());
  }
}
