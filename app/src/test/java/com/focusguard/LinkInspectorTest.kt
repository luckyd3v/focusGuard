package com.focusguard

import com.focusguard.util.LinkInfo
import com.focusguard.util.LinkInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkInspectorTest {

    @Test
    fun `acha o link no texto compartilhado`() {
        assertEquals(
            "https://youtu.be/abc123?si=x",
            LinkInspector.extractUrl("Olha esse vídeo: https://youtu.be/abc123?si=x."),
        )
        assertNull(LinkInspector.extractUrl("comprar pão"))
    }

    @Test
    fun `duracao de video do youtube`() {
        val html = """<html><head><meta property="og:title" content="Aula de Kotlin &amp; Android"></head>
            <script>var x = {"videoDetails":{"lengthSeconds":"754"}}</script></html>"""
        val info = LinkInspector.analyze(html)
        assertEquals("Aula de Kotlin & Android", info.title)
        assertEquals(13, info.minutes) // 12min34s arredonda para cima
        assertEquals(LinkInfo.Source.VIDEO, info.source)
    }

    @Test
    fun `duracao iso e meta de video`() {
        assertEquals(3723L, LinkInspector.parseIsoDuration("PT1H2M3S"))
        assertEquals(45L, LinkInspector.parseIsoDuration("PT45S"))
        assertNull(LinkInspector.parseIsoDuration("P"))
        assertEquals(754L, LinkInspector.videoSeconds("""<meta itemprop="duration" content="PT12M34S">"""))
        assertEquals(300L, LinkInspector.videoSeconds("""<meta property="video:duration" content="300" />"""))
    }

    @Test
    fun `tempo de leitura de um artigo`() {
        val palavras = (1..1000).joinToString(" ") { "palavra$it" }
        val html = "<html><head><title>Um artigo</title></head><body><nav>menu menu menu</nav>" +
            "<article><p>$palavras</p></article><script>var a = 'nao conta'</script></body></html>"
        val info = LinkInspector.analyze(html)
        assertEquals("Um artigo", info.title)
        assertEquals(5, info.minutes) // 1000 palavras / 200 por minuto
        assertEquals(LinkInfo.Source.READING, info.source)
    }

    @Test
    fun `pagina curta sem video nao tem avaliacao`() {
        val info = LinkInspector.analyze("<html><head><title>Login</title></head><body>Entre com sua conta</body></html>")
        assertEquals("Login", info.title)
        assertNull(info.minutes)
    }
}
