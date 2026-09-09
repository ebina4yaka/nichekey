package dev.example.jpkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 実際の assets/mozc_dict.bin を使った変換品質テスト */
class RealDictTest {
    private val dict: MozcDict by lazy {
        val f =
            File("../app/src/main/assets/mozc_dict.bin")
                .takeIf { it.exists() }
                ?: File("app/src/main/assets/mozc_dict.bin")
        val ctor = MozcDict::class.java.getDeclaredConstructor(ByteArray::class.java)
        ctor.isAccessible = true
        ctor.newInstance(f.readBytes()) as MozcDict
    }

    @Test fun fileConvertsFirst() {
        val out = Converter.nbest("ふぁいる", dict, 8)
        println("ふぁいる -> $out")
        assertEquals("ファイル", out.first())
    }

    @Test fun watashihaConvertsFirst() {
        val out = Converter.nbest("わたしは", dict, 8)
        println("わたしは -> $out")
        assertEquals("私は", out.first())
    }
}
