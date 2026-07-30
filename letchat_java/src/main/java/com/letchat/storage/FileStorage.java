package com.letchat.storage;

import java.io.InputStream;

public interface FileStorage {
    void put(String objectKey, InputStream inputStream, long size, String contentType);

    StoredObject get(String objectKey);
}
