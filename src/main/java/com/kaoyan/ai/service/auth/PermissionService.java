package com.kaoyan.ai.service.auth;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.mapper.RbacMapper;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class PermissionService {
  private final RbacMapper mapper;
  private final IdentityMapper identities;
  public PermissionService(RbacMapper mapper, IdentityMapper identities) {
    this.mapper = mapper;
    this.identities = identities;
  }
  public List<String> roles(String user) { return mapper.roles(user); }
  public List<String> permissions(String role) { return mapper.permissions(role); }
  public void requireRole(String user,String role) {
    if (role == null || !roles(user).contains(role))
      throw new ApiException(403,"ROLE_NOT_ASSIGNED","该账号未分配所选角色");
  }

  public List<String> validate(String user, String role, Object version) {
    var account = user == null ? null : identities.byId(user);
    if (account == null || !account.enabled() || !(version instanceof Number number)
        || account.authVersion() != number.longValue())
      throw new ApiException(401,"UNAUTHENTICATED","登录已失效，请重新登录");
    requireRole(user, role);
    return permissions(role);
  }
}
