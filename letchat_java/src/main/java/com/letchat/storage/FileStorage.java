package com.letchat.storage;

import java.io.InputStream;

public interface FileStorage {

    /**
     * 上传对象。
     *
     * <p>实现只负责消费 {@code inputStream}，<b>不负责关闭</b>；流的生命周期由调用方用
     * try-with-resources 管理，避免上传链路泄漏文件描述符。
     */
    void put(String objectKey, InputStream inputStream, long size, String contentType);

    StoredObject get(String objectKey);
}
