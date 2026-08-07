package com.doq.comfozi.structuring.ingestion.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * 로컬 파일시스템 기반 [FileStorage] 구현.
 *
 * 저장 루트는 `app.storage.local.root` (기본 `./data/uploads`)로 주입한다.
 * 저장 키는 UUID라 원본 파일명을 경로에 쓰지 않는다 (path traversal 회피).
 * 원본 파일명은 domain의 IngestionUpload.fileName에 별도 보관.
 */
@Component
class LocalFileStorage(
    @param:Value("\${app.storage.local.root:./data/uploads}") private val root: String,
) : FileStorage {

    private val rootPath: Path get() = Path.of(root)

    override fun store(content: InputStream): StoredFile {
        Files.createDirectories(rootPath)
        val key = UUID.randomUUID().toString()
        val size = Files.copy(content, rootPath.resolve(key))
        return StoredFile(storageKey = key, size = size)
    }

    override fun load(storageKey: String): InputStream =
        Files.newInputStream(rootPath.resolve(storageKey))

    override fun delete(storageKey: String) {
        Files.deleteIfExists(rootPath.resolve(storageKey))
    }
}
