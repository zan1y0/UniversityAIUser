package com.kaoyan.ai.persistence.mapper;

import com.kaoyan.ai.persistence.entity.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** SQL 定义见 resources/mapper/UserAccountMapper.xml。 */
public interface UserAccountMapper {

  UserAccount findByLoginName(@Param("loginName") String loginName);
  UserAccount findById(@Param("id") String id);
  int countById(@Param("id") String id);
  int insert(UserAccount account);
  String lockById(@Param("id") String id);
  List<String> findAllIds();
}
