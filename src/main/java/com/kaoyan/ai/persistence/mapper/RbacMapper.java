package com.kaoyan.ai.persistence.mapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
public interface RbacMapper {
  List<String> roles(@Param("user") String user);
  List<String> permissions(@Param("role") String role);
  int grant(@Param("user") String user, @Param("role") String role);
  int revoke(@Param("user") String user, @Param("role") String role);
}
