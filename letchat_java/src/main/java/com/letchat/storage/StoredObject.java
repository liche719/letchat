package com.letchat.storage;

import java.io.InputStream;

public record StoredObject(InputStream inputStream, long size, String contentType) {
}
