package ru.fizteh.fivt.students.tonmit.storeable;

import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.storage.structured.TableProvider;
import ru.fizteh.fivt.students.tonmit.storeable.db_structure.DataBaseState;
import ru.fizteh.fivt.students.tonmit.storeable.db_structure.TableClass;
import ru.fizteh.fivt.students.tonmit.storeable.db_structure.TableManager;
import ru.fizteh.fivt.students.tonmit.storeable.interpreter.Command;
import ru.fizteh.fivt.students.tonmit.storeable.interpreter.Interpreter;
import ru.fizteh.fivt.students.tonmit.storeable.util.CastMaker;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.function.BiConsumer;

public class DefaultCommandArray {
    public static Command[] cmdArray = new Command[] {
            new Command("show", 1, 1, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    if (args[0].equals("tables")) {
                        TableManager manager = (TableManager) state.getManager();
                        List<String> tableNames = manager.getTableNames();
                        System.out.println("table_name row_count");
                        for (String name : tableNames) {
                            Table curTable = manager.getTable(name);
                            System.out.println(curTable.getName() + " " + curTable.size());
                        }
                    } else {
                        throw new RuntimeException(Interpreter.BAD_COMMAND + "show " + args[0]);
                    }
                }
            }),
            new Command("list", 0, 0, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null) {
                        System.out.println(String.join(", ", currentTable.list()));
                    } else {
                        throw new RuntimeException("no table");
                    }
                }
            }),
            new Command("create", 2, Integer.MAX_VALUE, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    TableProvider manager = state.getManager();
                    List<Class<?>> signature = CastMaker.signatureToClassesList(args, 1);
                    try {
                        if (manager.createTable(args[0], signature) != null) {
                            System.out.println("created");
                        } else {
                            throw new RuntimeException(args[0] + " exists");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Error while creating table. " + e.getMessage());
                    }
                }
            }),
            new Command("use", 1, 1, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    TableProvider manager = state.getManager();
                    Table newTable = manager.getTable(args[0]);
                    if (newTable == null) {
                        throw new IllegalArgumentException(args[0] + " not exists");
                    }
                    TableClass usedTable = (TableClass) state.getCurrentTable();
                    if (usedTable != null && (usedTable.getNumberOfUncommittedChanges() > 0)
                            && usedTable != newTable) {
                        throw new IllegalArgumentException(usedTable.getNumberOfUncommittedChanges()
                                + " unsaved changes");
                    } else {
                        state.setCurrentTable(newTable);
                        System.out.println("using " + args[0]);
                    }
                }
            }),
            new Command("put", 2, Integer.MAX_VALUE, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    try {
                        if (currentTable != null) {
                            String builded = CastMaker.makeJSON(args, 1);
                            Storeable oldValue;
                            oldValue = currentTable.put(args[0],
                                    state.getManager().deserialize(currentTable, builded));
                            if (oldValue != null) {
                                System.out.println("overwrite");
                                System.out.println(state.getManager().serialize(currentTable, oldValue));
                            } else {
                                System.out.println("new");
                            }
                        } else {
                            throw new RuntimeException("no table");
                        }
                    } catch (ParseException | IndexOutOfBoundsException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                }
            }),
            new Command("get", 1, 1, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null) {
                        Storeable value = currentTable.get(args[0]);
                        if (value != null) {
                            System.out.println("found");
                            System.out.println(state.getManager().serialize(currentTable, value));
                        } else {
                            throw new RuntimeException("not found");
                        }
                    } else {
                        throw new RuntimeException("no table");
                    }
                }
            }),
            new Command("remove", 1, 1, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null) {
                        Storeable removedValue = currentTable.remove(args[0]);
                        if (removedValue != null) {
                            System.out.println("removed");
                        } else {
                            throw new RuntimeException("not found");
                        }
                    } else {
                        throw new RuntimeException("no table");
                    }
                }
            }),
            new Command("drop", 1, 1, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    TableProvider manager = state.getManager();
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null && currentTable.getName().equals(args[0])) {
                        state.setCurrentTable(null);
                    }
                    try {
                        manager.removeTable(args[0]);
                        System.out.println("dropped");
                    } catch (IllegalStateException e) {
                        throw new RuntimeException(args[0] + " doesn't exist");
                    } catch (IOException e) {
                        throw new RuntimeException("Can't drop : " + e.getMessage());
                    }
                }
            }),
            new Command("size", 0, 0, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null) {
                        System.out.println(currentTable.size());
                    } else {
                        throw new RuntimeException("no table");
                    }
                }
            }),
            new Command("commit", 0, 0, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null) {
                        try {
                            System.out.println(currentTable.commit());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        throw new RuntimeException("no table");
                    }
                }
            }),
            new Command("rollback", 0, 0, new BiConsumer<DataBaseState, String[]>() {
                @Override
                public void accept(DataBaseState state, String[] args) {
                    Table currentTable = state.getCurrentTable();
                    if (currentTable != null) {
                        System.out.println(currentTable.rollback());
                    } else {
                        throw new RuntimeException("no table");
                    }
                }
            })
    };
}
