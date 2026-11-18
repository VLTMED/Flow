package io.github.aedev.flow.data.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleTranslateResponseParserTest {
    @Test
    fun parsesAndJoinsGoogleTranslateSentenceSegments() {
        val response =
            """[[["صباح الخير","Good morning",null,null,3],["، كيف حالك؟","how are you?",null,null,3]],null,"en"]"""

        assertEquals("صباح الخير، كيف حالك؟", GoogleTranslateResponseParser.parse(response))
    }

    @Test
    fun ignoresNonSentenceMetadataAndNullSegments() {
        val response = """[[[null,"source"],["ترجمة","source",null]],null,"en"]"""

        assertEquals("ترجمة", GoogleTranslateResponseParser.parse(response))
    }

    @Test
    fun rejectsMalformedOrEmptyResponses() {
        assertNull(GoogleTranslateResponseParser.parse("not json"))
        assertNull(GoogleTranslateResponseParser.parse("""[[],null,"en"]"""))
    }

    @Test
    fun splittingLongTextPreservesEveryOriginalSeparator() {
        val text = "عنوان\n\n" + "نص طويل ".repeat(8)
        val chunks = splitTranslationText(text, maxChars = 10)

        assertTrue(chunks.size > 1)
        assertEquals(text, chunks.joinToString("") { it.text + it.separatorAfter })
    }
}
