package com.kaoyan.ai.persistence.mapper;

import com.kaoyan.ai.persistence.entity.EmailAccount;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface IdentityMapper {
  EmailAccount byEmail(String email);
  EmailAccount byId(String id);
  EmailAccount lockUser(String id);
  String legacyTeacherHash(String id);
  int markDevelopmentAccount(@Param("id") String id, @Param("email") String email);
  int lock(int id);
  void insertAccount(@Param("id") String id, @Param("email") String email, @Param("name") String name,
      @Param("hash") String hash, @Param("status") String status, @Param("verified") boolean verified,
      @Param("terms") boolean terms);
  int activate(@Param("id") String id, @Param("email") String email, @Param("hash") String hash);
  int changeEmail(@Param("id") String id, @Param("email") String email);
  int changePassword(@Param("id") String id, @Param("hash") String hash);
  int changeStatus(@Param("id") String id, @Param("status") String status);
  int renameUser(@Param("id") String id, @Param("name") String name);
  void bumpVersion(String id);
  void revokeTokens(String id);
  String bootstrap();
  boolean bootstrapCompleted();
  int reserveBootstrap(@Param("id") String id);
  void completeBootstrap(String id);
  List<Map<String,Object>> users(@Param("email") String email, @Param("role") String role,
      @Param("offset") int offset);
  List<Map<String,Object>> legacyUsers();
  List<Map<String,Object>> roleCatalog();
  List<Map<String,Object>> permissionCatalog();
  int renameRole(@Param("role") String role, @Param("name") String name);
  void clearPermissions(String role);
  void addPermission(@Param("role") String role, @Param("permission") String permission);
  int effectiveAdmins();
  Map<String,Object> invitationByUser(String id);
  String invitationOwner(String email);
  Map<String,Object> invitationByToken(String digest);
  Map<String,Object> lockInvitationByToken(String digest);
  void removeInvitation(String id);
  void addInvitation(@Param("id") String id, @Param("email") String email,
      @Param("digest") String digest, @Param("purpose") String purpose, @Param("expires") java.sql.Timestamp expires);
  int delivery(@Param("digest") String digest, @Param("status") String status);
  void audit(@Param("id") String id, @Param("actor") String actor, @Param("action") String action,
      @Param("target") String target, @Param("before") String before, @Param("after") String after,
      @Param("reason") String reason, @Param("trace") String trace);
  List<Map<String,Object>> auditList(int offset);
  String setting(String key);
  int updateSetting(@Param("key") String key, @Param("value") String value);
}
