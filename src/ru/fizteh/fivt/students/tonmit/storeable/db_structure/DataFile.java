package ru.fizteh.fivt.students.tonmit.storeable.db_structure;

import ru.fizteh.fivt.storage.structured.Storeable;
import ru.fizteh.fivt.storage.structured.Table;
import ru.fizteh.fivt.storage.structured.TableProvider;
import ru.fizteh.fivt.students.tonmit.storeable.db_exceptions.DatabaseCorruptedException;
import ru.fizteh.fivt.students.tonmit.storeable.util.Coordinates;

import java.text.ParseException;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

public class DataFile {
    private Map<String, Storeable> fileMap;
    private Path filePath;
    private Coordinates folderFileIndexes;
    private Table table;
    private TableProvider provider;

    public DataFile(Path tablePath, Coordinates coordinates, Table table, TableProvider provider)
            throws IOException, DatabaseCorruptedException {
        this.table = table;
        this.provider = provider;
        fileMap = new HashMap<>();
        filePath = makeAbsolutePath(tablePath, coordinates);
        folderFileIndexes = coordinates;
        if (filePath.toFile().exists()) {
            update();
        }
    }

    public void commit() throws IOException {
        if (fileMap.size() == 0) {
            filePath.toFile().delete();
            filePath.getParent().toFile().delete();
        } else {
            filePath.getParent().toFile().mkdir();
            try (RandomAccessFile file = new RandomAccessFile(filePath.toString(), "rw")) {
                file.setLength(0);
                Set<String> keys = fileMap.keySet();
                List<Integer> reserved = new LinkedList<>();
                List<Integer> offsets = new LinkedList<>();
                for (String currentKey : keys) {
                    file.write(currentKey.getBytes(TableManager.CODE_FORMAT));
                    file.write('\0');
                    reserved.add((int) file.getFilePointer());
                    file.writeInt(0);
                }
                for (String currentKey : keys) {
                    offsets.add((int) file.getFilePointer());
                    file.write(provider.serialize(table, fileMap.get(currentKey)).getBytes(TableManager.CODE_FORMAT));
                }
                Iterator<Integer> offsetIterator = offsets.iterator();
                for (int offsetPos : reserved) {
                    file.seek(offsetPos);
                    file.writeInt(offsetIterator.next());
                }
            } catch (FileNotFoundException e) {
                throw new IOException("File not found: " + filePath.toString());
            } catch (UnsupportedEncodingException e) {
                throw new IOException("Can't encode file: " + filePath.toString());
            }
        }
    }

    private void update() throws DatabaseCorruptedException, IOException {
        try (RandomAccessFile file = new RandomAccessFile(filePath.toString(), "r")) {
            if (file.length() == 0) {
                throw new DatabaseCorruptedException("Data base corrupted: empty file found");
            }
            List<String> keys = new LinkedList<>();
            List<Integer> offsets = new LinkedList<>();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte b;
            int counter = 0;
            do { // Read keys.
                while ((b = file.readByte()) != 0) {
                    counter++;
                    bytes.write(b);
                }
                ++counter;
                offsets.add(file.readInt());
                counter += 4;
                String key = bytes.toString(TableManager.CODE_FORMAT);
                bytes.reset();
                if (!checkKey(key)) {
                    throw new DatabaseCorruptedException("Wrong key found in file " + filePath.toString());
                }
                keys.add(key);
            } while (counter < offsets.get(0));

            offsets.add((int) file.length());
            offsets.remove(0);
            Iterator<String> keyIterator = keys.iterator();
            for (int nextOffset : offsets) {
                while (counter < nextOffset) {
                    bytes.write(file.readByte());
                    counter++;
                }
                if (bytes.size() > 0) {
                    try {
                        fileMap.put(keyIterator.next(),
                                provider.deserialize(table, bytes.toString(TableManager.CODE_FORMAT)));
                    } catch (ParseException e) {
                        throw new RuntimeException("Data corrupted in file "
                                + filePath.toString() + " : " + e.getMessage());
                    }
                    bytes.reset();
                } else {
                    throw new DatabaseCorruptedException("Data corrupted in file " + filePath.toString());
                }
            }
            bytes.close();
        }
    }

    public Storeable put(String key, Storeable value) {
        if (!checkKey(key)) {
            throw new IllegalArgumentException("trying to put unexpected key in file " + filePath.toString());
        }
        if (value == null) {
            throw new IllegalArgumentException("trying to put null value in file " + filePath.toString());
        }
        return fileMap.put(key, value);
    }

    public Storeable get(String key) {
        if (!checkKey(key)) {
            throw new IllegalArgumentException("trying to read unexpected key in file " + filePath.toString());
        }
        return fileMap.get(key);
    }

    public Storeable remove(String key) {
        if (!checkKey(key)) {
            throw new IllegalArgumentException("trying to remove unexpected key from file " + filePath.toString());
        }
        return fileMap.remove(key);
    }

    public Set<String> list() {
        return fileMap.keySet();
    }

    public int size() {
        return fileMap.size();
    }

    private Path makeAbsolutePath(Path tablePath, Coordinates folderFileIndexes) {
        return tablePath.resolve(
                Paths.get(Integer.toString(folderFileIndexes.getFolderIndex()) + ".dir",
                        Integer.toString(folderFileIndexes.getFileIndex()) + ".dat"));
    }

    private boolean checkKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("checkKey: key == null");
        }
        try {
            return ((folderFileIndexes.getFolderIndex() == Math.abs(key.getBytes(TableManager.CODE_FORMAT)[0]
                    % TableManager.NUMBER_OF_PARTITIONS) && folderFileIndexes.getFileIndex()
                    == Math.abs((key.getBytes(TableManager.CODE_FORMAT)[0] / TableManager.NUMBER_OF_PARTITIONS)
                    % TableManager.NUMBER_OF_PARTITIONS)));
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }
}
