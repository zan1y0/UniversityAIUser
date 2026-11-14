package db.migration.h2;

import com.kaoyan.ai.persistence.migration.BackfillTeachingNotices;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V12__backfill_teaching_notices extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    BackfillTeachingNotices.migrate(context.getConnection());
  }
}
