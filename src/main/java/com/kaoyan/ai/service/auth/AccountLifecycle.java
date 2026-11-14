package com.kaoyan.ai.service.auth;

import static com.kaoyan.ai.common.Jsons.*;
import static com.kaoyan.ai.service.auth.AccountInputs.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaoyan.ai.common.ApiException;
import com.kaoyan.ai.persistence.entity.EmailAccount;
import com.kaoyan.ai.persistence.mapper.IdentityMapper;
import com.kaoyan.ai.persistence.mapper.RbacMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountLifecycle {
  private static final String PENDING = "PENDING_ACTIVATION";
  private final IdentityMapper accounts;
  private final RbacMapper roles;
  private final EmailAuthService auth;
  private final EmailVerificationService codes;
  private final AccountMail mail;
  private final TransactionTemplate tx;
  private final SecureRandom random = new SecureRandom();
  private final String publicUrl;
  private final org.springframework.core.task.TaskExecutor resetExecutor;

  public AccountLifecycle(IdentityMapper accounts, RbacMapper roles, EmailAuthService auth,
      EmailVerificationService codes, AccountMail mail, PlatformTransactionManager manager,
      @Value("${app.account-mail.public-url:http://localhost:5173}") String publicUrl,
      @org.springframework.beans.factory.annotation.Qualifier("resetMailExecutor")
      org.springframework.core.task.TaskExecutor resetExecutor,
      org.springframework.core.env.Environment environment) {
    this.accounts = accounts;
    this.roles = roles;
    this.auth = auth;
    this.codes = codes;
    this.mail = mail;
    this.tx = new TransactionTemplate(manager);
    this.resetExecutor = resetExecutor;
    var url = java.net.URI.create(publicUrl);
    if (url.getHost() == null || url.getUserInfo() != null || url.getRawQuery() != null || url.getRawFragment() != null
        || !("https".equals(url.getScheme()) || (!environment.matchesProfiles("prod") && "http".equals(url.getScheme())
        && java.util.Set.of("localhost","127.0.0.1").contains(url.getHost()))))
      throw new IllegalArgumentException("账号邮件地址必须使用可信 HTTPS URL");
    this.publicUrl = publicUrl.replaceAll("/+$", "");
  }

  public void requestCode(JsonNode body, String ip) {
    fields(body, "email", "purpose", "token");
    String purpose = string(body, "purpose");
    if ("ACTIVATE".equals(purpose)) {
      var invite = validInvitation(string(body, "token"));
      String mailbox = (String) invite.get("email");
      codes.reserve(mailbox, ip);
      codes.send(EmailVerificationService.Purpose.ACTIVATE, mailbox, (String) invite.get("digest"));
      return;
    }
    String mailbox = email(string(body, "email"));
    require("REGISTER".equals(purpose) || "RESET".equals(purpose), "验证码用途无效");
    codes.reserve(mailbox, ip);
    if ("RESET".equals(purpose)) {
      try {
        resetExecutor.execute(() -> deliverReset(mailbox, accounts.byEmail(mailbox)));
      } catch (org.springframework.core.task.TaskRejectedException e) {
        throw new ApiException(503, "MAIL_UNAVAILABLE", "邮件服务繁忙，请稍后重试");
      }
    } else if (accounts.byEmail(mailbox) == null) {
      codes.send(EmailVerificationService.Purpose.REGISTER, mailbox, "");
    }
  }

  private void deliverReset(String mailbox, EmailAccount existing) {
    if (existing == null || !existing.enabled()) return;
    try {
      codes.send(EmailVerificationService.Purpose.RESET, mailbox, resetScope(existing));
    } catch (ApiException e) {
      org.slf4j.LoggerFactory.getLogger(AccountLifecycle.class).warn("密码找回邮件投递失败，状态码 {}", e.code());
    }
  }

  @Transactional(noRollbackFor = EmailVerificationService.InvalidCode.class)
  public void reset(JsonNode body) {
    fields(body, "email", "code", "password", "confirmPassword");
    String mailbox = email(string(body, "email"));
    String password = password(body);
    EmailAccount existing = accounts.byEmail(mailbox);
    if (existing == null) throw new EmailVerificationService.InvalidCode();
    existing = accounts.lockUser(existing.id());
    if (!existing.enabled() || !mailbox.equals(existing.email())) throw new EmailVerificationService.InvalidCode();
    codes.consume(EmailVerificationService.Purpose.RESET, mailbox, resetScope(existing), string(body, "code"));
    accounts.changePassword(existing.id(), auth.hash(password));
    accounts.revokeTokens(existing.id());
  }

  private String resetScope(EmailAccount account) { return account.id() + ":" + account.authVersion(); }

  public JsonNode invitation(String token) {
    var invite = validInvitation(token);
    return obj("email", invite.get("email"), "legacy", "BIND".equals(invite.get("purpose")),
        "bootstrap", "BOOTSTRAP".equals(invite.get("purpose")));
  }

  @Transactional(noRollbackFor = EmailVerificationService.InvalidCode.class)
  public void activate(JsonNode body) {
    fields(body, "token", "code", "password", "confirmPassword", "legacyPassword");
    String password = password(body);
    String token = string(body, "token");
    var first = validInvitation(token);
    if ("BOOTSTRAP".equals(first.get("purpose"))) accounts.lock(1);
    var user = accounts.lockUser((String) first.get("userId"));
    var invite = checkedInvitation(accounts.lockInvitationByToken(digest(token)));
    String mailbox = (String) invite.get("email");
    boolean binding = "BIND".equals(invite.get("purpose"));
    boolean bootstrap = "BOOTSTRAP".equals(invite.get("purpose"));
    if (user == null || !PENDING.equals(user.status())
        || (binding ? user.email() != null : !mailbox.equals(user.email()))) throw invalidInvite();
    if ((binding && !auth.matchesLegacy(user, credential(body,"legacyPassword")))
        || (bootstrap && !auth.matchesStored(user, credential(body,"legacyPassword"))))
      codes.reject(EmailVerificationService.Purpose.ACTIVATE, mailbox, (String) invite.get("digest"));
    codes.consume(EmailVerificationService.Purpose.ACTIVATE, mailbox, (String) invite.get("digest"), string(body, "code"));
    try {
      if (accounts.activate(user.id(), mailbox, auth.hash(password)) != 1) throw invalidInvite();
    } catch (DuplicateKeyException e) {
      throw ApiException.state("邮箱已被占用，请联系管理员处理");
    }
    accounts.removeInvitation(user.id());
    accounts.revokeTokens(user.id());
    if (bootstrap) {
      roles.grant(user.id(), "ADMIN");
      accounts.completeBootstrap(user.id());
    } else if (!binding) auth.createProfile(user.id(), mailbox, user.displayName());
    audit(user.id(), bootstrap ? "ADMIN_BOOTSTRAPPED" : "ACCOUNT_ACTIVATED",
        user.id(), PENDING, "ENABLED", "邮箱验证", id());
  }

  void bootstrap(String rawEmail, String rawPassword, String displayName) {
    if (accounts.bootstrapCompleted()) return;
    String mailbox = email(rawEmail);
    String password = password(obj("password", rawPassword, "confirmPassword", rawPassword));
    String token = newToken();
    String hash = digest(token);
    String trace = id();
    String user = tx.execute(status -> {
      accounts.lock(1);
      if (accounts.bootstrapCompleted()) return null;
      String pending = accounts.bootstrap();
      if (pending == null) {
        if (accounts.invitationOwner(mailbox) != null)
          throw new IllegalStateException("首位管理员邮箱已被其他邀请占用");
        pending = id();
        auth.insert(pending, mailbox, name(displayName), auth.hash(password), PENDING, false, false);
        accounts.reserveBootstrap(pending);
      } else {
        var account = accounts.lockUser(pending);
        if (accounts.bootstrapCompleted()) return null;
        if (!mailbox.equals(account.email()) || !auth.matchesStored(account, password))
          throw new IllegalStateException("首位管理员的待验证初始化配置不匹配");
        var invite = accounts.invitationByUser(pending);
        if (invite != null && !"FAILED".equals(invite.get("status")) && expires(invite).isAfter(Instant.now()))
          return null;
      }
      codes.reserve(mailbox, "admin-bootstrap");
      accounts.removeInvitation(pending);
      accounts.addInvitation(pending, mailbox, hash, "BOOTSTRAP", Timestamp.from(Instant.now().plusSeconds(86400)));
      return pending;
    });
    if (user != null && !deliverInvitation(mailbox, user, token, hash, null, "受控运维初始化", trace))
      throw new IllegalStateException("管理员初始化邮件投递失败；检查邮件配置后重试");
  }

  public Object issueInvitation(String userId, String newEmail, String name, String purpose,
      String actor, String reason, String trace, String source) {
    String mailbox;
    if (newEmail == null) {
      var previous = accounts.invitationByUser(userId);
      if (previous == null) throw ApiException.missing();
      mailbox = (String) previous.get("email");
      purpose = (String) previous.get("purpose");
    } else mailbox = email(newEmail);
    if (userId == null && accounts.byEmail(mailbox) != null)
      throw new ApiException(409, "ACCOUNT_EXISTS", "该邮箱已被占用");
    final String kind = purpose;
    String reserved = accounts.invitationOwner(mailbox);
    if (reserved != null && !reserved.equals(userId))
      throw ApiException.state("该候选邮箱已被另一条邀请占用，请管理员核对");
    codes.reserve(mailbox, source);
    String token = newToken();
    String digest = digest(token);
    String user = userId == null ? id() : userId;
    tx.executeWithoutResult(status -> {
      accounts.lock(1);
      String owner = accounts.invitationOwner(mailbox);
      if (owner != null && !owner.equals(user))
        throw ApiException.state("该候选邮箱已被另一条邀请占用，请管理员核对");
      if (userId == null) {
        auth.insert(user, mailbox, name(name), "!PENDING", PENDING, false, false);
        roles.grant(user, "TEACHER");
      } else {
        var account = accounts.lockUser(user);
        if (account == null || !PENDING.equals(account.status())) throw invalidInvite();
        if (newEmail == null) {
          var current = accounts.invitationByUser(user);
          if (current == null || !mailbox.equals(current.get("email")) || !kind.equals(current.get("purpose")))
            throw invalidInvite();
        } else if (account.email() != null || accounts.byEmail(mailbox) != null
            || roles.roles(user).contains("ADMIN")) {
          throw ApiException.state("历史账号或候选邮箱不可绑定");
        }
      }
      accounts.removeInvitation(user);
      accounts.addInvitation(user, mailbox, digest, kind, Timestamp.from(Instant.now().plusSeconds(86400)));
      audit(actor, userId == null ? "TEACHER_CREATED" : "INVITATION_ISSUED", user,
          null, "PENDING", reason, trace);
    });
    boolean sent = deliverInvitation(mailbox, user, token, digest, actor, reason, trace);
    return obj("id", user, "deliveryStatus", sent ? "SENT" : "FAILED");
  }

  private String newToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private boolean deliverInvitation(String mailbox, String user, String token, String digest,
      String actor, String reason, String trace) {
    boolean sent;
    try {
      mail.send(mailbox, "研途 AI 账号激活邀请", "请在24小时内打开链接并验证邮箱："
          + publicUrl + "/activate#token=" + token);
      sent = true;
    } catch (ApiException e) {
      sent = false;
    }
    final String delivery = sent ? "SENT" : "FAILED";
    tx.executeWithoutResult(status -> {
      if (accounts.delivery(digest, delivery) == 1)
        audit(actor, "INVITATION_DELIVERY", user, "PENDING", delivery, reason, trace);
    });
    return sent;
  }

  public void sendChange(String user, JsonNode body, String ip) {
    fields(body, "email", "currentPassword");
    var account = accounts.byId(user);
    auth.verifyPassword(account, credential(body, "currentPassword"));
    String mailbox = email(string(body, "email"));
    if (accounts.byEmail(mailbox) != null) throw ApiException.state("该邮箱已被占用");
    codes.reserve(mailbox, ip);
    codes.send(EmailVerificationService.Purpose.CHANGE, mailbox, resetScope(account));
  }

  @Transactional(noRollbackFor = EmailVerificationService.InvalidCode.class)
  public void changeEmail(String user, JsonNode body, String trace) {
    fields(body, "email", "currentPassword", "code");
    var account = accounts.lockUser(user);
    auth.verifyPassword(account, credential(body, "currentPassword"));
    String mailbox = email(string(body, "email"));
    codes.consume(EmailVerificationService.Purpose.CHANGE, mailbox, resetScope(account), string(body, "code"));
    try {
      accounts.changeEmail(user, mailbox);
    } catch (DuplicateKeyException e) {
      throw ApiException.state("该邮箱已被占用");
    }
    accounts.revokeTokens(user);
    audit(user, "EMAIL_CHANGED", user, null, null, "用户验证新邮箱", trace);
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
        new org.springframework.transaction.support.TransactionSynchronization() {
          @Override public void afterCommit() {
            try { mail.send(account.email(), "研途 AI 邮箱变更通知", "账号绑定邮箱已更换。"); }
            catch (ApiException e) {
              org.slf4j.LoggerFactory.getLogger(AccountLifecycle.class).warn("原邮箱变更通知发送失败");
            }
          }
        });
  }

  private Map<String,Object> validInvitation(String token) {
    if (!token.matches("[A-Za-z0-9_-]{43}")) throw invalidInvite();
    return checkedInvitation(accounts.invitationByToken(digest(token)));
  }

  private Map<String,Object> checkedInvitation(Map<String,Object> invite) {
    if (invite == null || !"SENT".equals(invite.get("status"))
        || !expires(invite).isAfter(Instant.now())) throw invalidInvite();
    return invite;
  }

  private Instant expires(Map<String,Object> invite) {
    return ((Timestamp) invite.get("expiresAt")).toInstant();
  }

  void audit(String actor, String action, String target, String before, String after, String reason, String trace) {
    accounts.audit(id(), actor, action, target, before, after, reason, trace);
  }

  private static ApiException invalidInvite() {
    return new ApiException(422, "INVALID_INVITATION", "邀请无效、已过期或已使用");
  }

  private static String digest(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
