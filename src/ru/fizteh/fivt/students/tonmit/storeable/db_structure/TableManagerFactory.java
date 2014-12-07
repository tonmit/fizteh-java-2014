package ru.fizteh.fivt.students.tonmit.storeable.db_structure;

import ru.fizteh.fivt.storage.structured.TableProviderFactory;
import ru.fizteh.fivt.storage.structured.TableProvider;

public class TableManagerFactory implements TableProviderFactory {
    public TableManagerFactory() {
        //Do nothing, only for implemented interface.
    }

    @Override
    public TableProvider create(String dir) {
        if (dir == null) {
            throw new IllegalArgumentException("Directory name is null");
        }
        return new TableManager(dir);
    }
}
