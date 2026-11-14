package com.kaoyan.ai.service.auth;

import static com.kaoyan.ai.common.Jsons.*;
import static com.kaoyan.ai.service.auth.AccountInputs.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.entity.EmailAccount;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import com.kaoyan.ai.persistence.mapper.RbacMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccounts {
  public record Action(String actor, String trace, String source) {}
  private static final Set<String> ROLES = Set.of("STUDENT", "TEACHER", "ADMIN");
  private static final String SETTING = "platform.announcement";
  private final IdentityMapper accounts;
  private final RbacMapper roles;
  private final AccountLifecycle lifecycle;

  AdminAccounts(IdentityMapper accounts, RbacMapper roles, AccountLifecycle lifecycle) {
    this.accounts = accounts;
    this.roles = roles;
    this.lifecycle = lifecycle;
  }

  public void bootstrap(String rawEmail, String rawPassword, String displayName) {
    lifecycle.bootstrap(rawEmail, rawPassword, displayName);
  }

  @PreAuthorize("hasAuthority('user:list')")
  public Object users(String email, String role, int page) {
    String query = email.strip().toLowerCase(java.util.Locale.ROOT);
    require(query.length() <= 254 && (role.isEmpty() || ROLES.contains(role)), "筛选条件无效");
    List<Map<String,Object>> list = accounts.users(query, role, offset(page));
    return obj("items", list.stream().map(this::withRoles).toList(), "page", page);
  }

  @PreAuthorize("hasAuthority('user:list')")
  public Object user(String id) {
    EmailAccount account = accounts.byId(id);
    if (account == null) throw ApiException.missing();
    var invitation = accounts.invitationByUser(id);
    return obj("id", id, "email", account.email(), "displayName", account.displayName(), "status", account.status(),
        "verified", account.verifiedAt() != null, "roles", roles.roles(id),
        "deliveryStatus", invitation == null ? null : invitation.get("status"));
  }

  @PreAuthorize("hasAuthority('user:update')")
  public Object legacy() {
    return obj("items", accounts.legacyUsers().stream().map(this::withRoles).toList());
  }

  private Object withRoles(Map<String,Object> row) {
    var result = MAPPER.valueToTree(row);
    ((com.fasterxml.jackson.databind.node.ObjectNode) result).set("roles", MAPPER.valueToTree(roles.roles((String)row.get("id"))));
    return result;
  }

  @PreAuthorize("hasAuthority('teacher:create')")
  public Object createTeacher(JsonNode body, Action action) {
    fields(body,"email","displayName","reason");
    String name = name(string(body,"displayName"));
    return lifecycle.issueInvitation(null,string(body,"email"),name,"TEACHER",
        action.actor,reason(body),action.trace,action.source);
  }

  @PreAuthorize("hasAuthority('user:update')")
  public Object bindLegacy(String user, JsonNode body, Action action) {
    fields(body,"email","reason");
    return lifecycle.issueInvitation(user,string(body,"email"),null,"BIND",
        action.actor,reason(body),action.trace,action.source);
  }

  @PreAuthorize("hasAuthority('teacher:invite') and hasAuthority('user:update')")
  public Object resend(String user, JsonNode body, Action action) {
    fields(body,"reason");
    return lifecycle.issueInvitation(user,null,null,null,action.actor,reason(body),action.trace,action.source);
  }

  @PreAuthorize("hasAuthority('teacher:manage') and hasAuthority('user:update')")
  @Transactional
  public void cancel(String user, JsonNode body, Action action) {
    fields(body,"reason");
    var account = lock(user);
    var invitation = accounts.invitationByUser(user);
    if (!"PENDING_ACTIVATION".equals(account.status()) || invitation == null)
      throw ApiException.state("账号没有待处理邀请");
    accounts.removeInvitation(user);
    if ("TEACHER".equals(invitation.get("purpose"))) {
      roles.revoke(user,"TEACHER");
      accounts.changeStatus(user,"DISABLED");
    }
    lifecycle.audit(action.actor,"INVITATION_CANCELLED",user,"PENDING","CANCELLED",reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('user:update')")
  @Transactional
  public void renameUser(String user, JsonNode body, Action action) {
    fields(body,"displayName","reason");
    var account = lock(user);
    String name = name(string(body,"displayName"));
    accounts.renameUser(user,name);
    lifecycle.audit(action.actor,"USER_RENAMED",user,account.displayName(),name,reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('user:disable')")
  @Transactional
  public void disable(String user, JsonNode body, Action action) {
    fields(body,"reason");
    var account = lock(user);
    if (!account.enabled()) throw ApiException.state("仅可停用已启用账号");
    accounts.changeStatus(user,"DISABLED");
    accounts.revokeTokens(user);
    guardAdmin();
    lifecycle.audit(action.actor,"USER_DISABLED",user,account.status(),"DISABLED",reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('user:enable')")
  @Transactional
  public void enable(String user, JsonNode body, Action action) {
    fields(body,"reason");
    var account = lock(user);
    if (!"DISABLED".equals(account.status()) || account.verifiedAt() == null || roles.roles(user).isEmpty())
      throw ApiException.state("仅可恢复已验证且拥有角色的账号");
    accounts.changeStatus(user,"ENABLED");
    accounts.revokeTokens(user);
    lifecycle.audit(action.actor,"USER_ENABLED",user,account.status(),"ENABLED",reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('role:assign')")
  @Transactional
  public void assign(String user, JsonNode body, Action action) {
    fields(body,"roles","reason");
    var account = lock(user);
    if (account.verifiedAt() == null) throw ApiException.state("请先完成账号验证");
    Set<String> requested = values(body,"roles");
    require(!requested.isEmpty() && ROLES.containsAll(requested),"角色无效");
    var existing = roles.roles(user);
    if (action.actor.equals(user) && !existing.containsAll(requested)) throw escalation();
    for (String role : requested) {
      if (!existing.contains(role)) {
        if ("ADMIN".equals(role)) requireDelegable(roles.permissions(role));
        roles.grant(user,role);
      }
    }
    for (String role : existing) if (!requested.contains(role)) roles.revoke(user,role);
    guardAdmin();
    accounts.bumpVersion(user);
    accounts.revokeTokens(user);
    lifecycle.audit(action.actor,"USER_ROLES_CHANGED",user,existing.toString(),requested.toString(),reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('role:list')")
  public Object roles() {
    return obj("items", accounts.roleCatalog().stream().map(role -> obj("code",role.get("code"),
        "name",role.get("name"),"permissions",roles.permissions((String) role.get("code")))).toList());
  }

  @PreAuthorize("hasAuthority('permission:list')")
  public Object permissions() {
    return obj("items", accounts.permissionCatalog().stream().map(permission -> {
      String code = (String) permission.get("code");
      return obj("code",code,"name",permission.get("name"),"category",code.split(":",2)[0],"description",permission.get("name"));
    }).toList());
  }

  @PreAuthorize("hasAuthority('role:update')")
  @Transactional
  public void renameRole(String role, JsonNode body, Action action) {
    fields(body,"name","reason");
    accounts.lock(1);
    var previous = accounts.roleCatalog().stream().filter(record -> role.equals(record.get("code")))
        .findFirst().orElseThrow(ApiException::missing);
    String name = name(string(body,"name"));
    accounts.renameRole(role,name);
    lifecycle.audit(action.actor,"ROLE_RENAMED",role,(String)previous.get("name"),name,reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('permission:assign')")
  @Transactional
  public void assignPermissions(String role, JsonNode body, Action action) {
    fields(body,"permissions","reason");
    accounts.lock(1);
    require(ROLES.contains(role),"角色无效");
    var requested = values(body,"permissions");
    var known = accounts.permissionCatalog().stream().map(record -> record.get("code")).toList();
    require(known.containsAll(requested),"存在未知权限");
    var previous = roles.permissions(role);
    var additions = requested.stream().filter(permission -> !previous.contains(permission)).toList();
    if ("ADMIN".equals(role)) requireDelegable(additions);
    else if (additions.stream().anyMatch(permission -> !permission.startsWith(role.toLowerCase(java.util.Locale.ROOT) + ":")
        || Set.of("teacher:create","teacher:invite","teacher:manage").contains(permission)))
      throw escalation();
    accounts.clearPermissions(role);
    requested.forEach(permission -> accounts.addPermission(role,permission));
    guardAdmin();
    lifecycle.audit(action.actor,"ROLE_PERMISSIONS_CHANGED",role,previous.toString(),requested.toString(),reason(body),action.trace);
  }

  @PreAuthorize("hasAuthority('system:log:read')")
  public Object logs(int page) { return obj("items",accounts.auditList(offset(page)),"page",page); }

  @PreAuthorize("hasAuthority('system:config:manage')")
  public Object settings() { return obj("announcement",accounts.setting(SETTING)); }

  @PreAuthorize("hasAuthority('system:config:manage')")
  @Transactional
  public void settings(JsonNode body, Action action) {
    fields(body,"announcement","reason");
    String announcement = string(body,"announcement").strip();
    require(announcement.length() <= 500,"公告不能超过500个字符");
    accounts.lock(1);
    String old = accounts.setting(SETTING);
    accounts.updateSetting(SETTING,announcement);
    lifecycle.audit(action.actor,"SETTINGS_CHANGED",SETTING,old,announcement,reason(body),action.trace);
  }

  private EmailAccount lock(String user) {
    accounts.lock(1);
    var account = accounts.lockUser(user);
    if (account == null) throw ApiException.missing();
    return account;
  }

  private void guardAdmin() {
    if (accounts.effectiveAdmins() == 0) throw ApiException.state("必须保留至少一名有效管理员和必要的管理权限");
  }

  private void requireDelegable(List<String> permissions) {
    var current = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    var effective = current.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();
    if (!effective.containsAll(permissions)) throw escalation();
  }

  private Set<String> values(JsonNode body, String key) {
    require(body.path(key).isArray() && body.path(key).size() <= 128, "请求集合无效");
    Set<String> values = new LinkedHashSet<>();
    body.path(key).forEach(value -> {
      require(value.isTextual() && value.asText().length() <= 80, "请求集合无效");
      values.add(value.asText());
    });
    return values;
  }

  private int offset(int page) {
    require(page >= 0 && page <= 10000,"页码无效");
    return page * 50;
  }

  private String reason(JsonNode body) {
    String reason = string(body,"reason").strip();
    require(!reason.isEmpty() && reason.length() <= 250,"填写1～250字符的操作原因");
    return reason;
  }

  private ApiException escalation() {
    return new ApiException(403,"FORBIDDEN","不能授予超出当前授权范围的角色或权限");
  }
}
