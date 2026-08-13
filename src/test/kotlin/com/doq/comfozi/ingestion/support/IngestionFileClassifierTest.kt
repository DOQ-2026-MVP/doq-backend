package com.doq.comfozi.ingestion.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class IngestionFileClassifierTest {

    private val classifier = IngestionFileClassifier()

    private fun batchFormat(bytes: ByteArray) =
        assertIs<ClassifiedFile.BatchFile>(classifier.classify(bytes)).format

    @Test
    fun `PDF·PNG·JPEG는 보관 전용 문서로 분류된다`() {
        val cases = mapOf(
            "PDF" to "%PDF-1.7\n…".toByteArray(),
            "PNG" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            "JPEG" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()),
        )
        for ((expected, bytes) in cases) {
            assertEquals(expected, assertIs<ClassifiedFile.Document>(classifier.classify(bytes)).format)
        }
    }

    @Test
    fun `zip(PK)은 XLSX 취합 파일로 분류된다`() {
        assertEquals(BatchFileFormat.XLSX, batchFormat(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
    }

    @Test
    fun `알려진 이진 포맷이 아닌 텍스트는 CSV 후보다`() {
        assertEquals(BatchFileFormat.CSV, batchFormat("문서ID,공급사\nDOC-001,가온푸드".toByteArray()))
    }

    @Test
    fun `BOM 붙은 CSV도 CSV로 분류된다`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(BatchFileFormat.CSV, batchFormat(bom + "문서ID".toByteArray()))
    }

    @Test
    fun `NUL이 섞인 바이너리는 거부한다 - CSV 파서로 흘려보내지 않는다`() {
        val e = assertFailsWith<IllegalArgumentException> {
            classifier.classify(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        }
        assertEquals(true, e.message!!.contains("지원하지 않는 파일 형식"))
    }

    @Test
    fun `UTF-8로 디코딩되지 않는 바이트열은 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            classifier.classify(byteArrayOf(0xC3.toByte(), 0x28, 0xA0.toByte(), 0xA1.toByte()))
        }
    }

    @Test
    fun `빈 파일은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { classifier.classify(ByteArray(0)) }
    }

    @Test
    fun `분류는 확장자·파일명과 무관하게 내용만 본다`() {
        // 같은 바이트열이면 파일명이 무엇이든 같은 결과여야 한다 (classify 는 파일명을 받지도 않는다)
        assertIs<ClassifiedFile.Document>(classifier.classify("%PDF-1.7".toByteArray()))
        assertEquals(BatchFileFormat.CSV, batchFormat("문서ID,공급사".toByteArray()))
    }
}
