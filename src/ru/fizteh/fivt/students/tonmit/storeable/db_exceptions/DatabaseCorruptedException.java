package ru.fizteh.fivt.students.tonmit.storeable.db_exceptions;

public class DatabaseCorruptedException extends Exception {
    public DatabaseCorruptedException(String message) {
        super(message);
    }
}
