package com.kaoyan.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kaoyan.ai.persistence.LocalDataInitializer;
import com.kaoyan.ai.persistence.RecordRepository;
import com.kaoyan.ai.persistence.UserAccountRepository;
import org.junit.jupiter.api.Test;

class LocalDataInitializerTests {
  @Test
  void missingOrBlankPasswordStopsBeforeCreatingAccounts() {
    for (String password : new String[] {"", "  \t"}) {
      var records = mock(RecordRepository.class);
      var accounts = mock(UserAccountRepository.class);
      var initializer = new LocalDataInitializer(records, true, password, accounts);

      var error = assertThrows(IllegalStateException.class, () -> initializer.run(null));

      assertTrue(error.getMessage().contains("LOCAL_STUDENT_PASSWORD"));
      verify(accounts, never()).create(any());
      verifyNoInteractions(records);
    }
  }

  @Test
  void existingAccountDoesNotRequireOrOverwritePassword() throws Exception {
    var records = mock(RecordRepository.class);
    var accounts = mock(UserAccountRepository.class);
    when(accounts.existsById(LocalDataInitializer.USER)).thenReturn(true);

    new LocalDataInitializer(records, true, "", accounts).run(null);

    verify(accounts, never()).create(any());
    verifyNoInteractions(records);
  }

  @Test
  void disabledSeedingDoesNotRequirePassword() throws Exception {
    var records = mock(RecordRepository.class);
    var accounts = mock(UserAccountRepository.class);

    new LocalDataInitializer(records, false, "", accounts).run(null);

    verifyNoInteractions(records, accounts);
  }
}
