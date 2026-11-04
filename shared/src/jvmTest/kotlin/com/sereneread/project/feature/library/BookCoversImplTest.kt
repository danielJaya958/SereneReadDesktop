package com.sereneread.project.feature.library

import com.sereneread.project.core.network.createHttpClient
import com.sereneread.project.core.session.FakeTokenStore
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.library.data.BookCoversImpl
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookCoversImplTest {

    private val requestedUrls = mutableListOf<String>()

    private fun covers(body: ByteArray, status: HttpStatusCode = HttpStatusCode.OK): BookCoversImpl {
        val engine = MockEngine { request ->
            requestedUrls += request.url.toString()
            respond(body, status)
        }
        return BookCoversImpl(createHttpClient(engine, SessionManager(FakeTokenStore())), Dispatchers.Unconfined)
    }

    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    @Test
    fun loadsFromServerHostOnceThenFromMemory() = runTest {
        val covers = covers(png(20, 30))

        val first = assertNotNull(covers.load("/uploads/thumbnails/9.jpg"))
        covers.load("/uploads/thumbnails/9.jpg")

        assertEquals(20 to 30, first.width to first.height)
        assertEquals(listOf("https://sereneread.my.id/uploads/thumbnails/9.jpg"), requestedUrls)
    }

    @Test
    fun missingCoverGivesNull() = runTest {
        assertNull(covers(ByteArray(0), HttpStatusCode.NotFound).load("/uploads/thumbnails/9.jpg"))
    }

    @Test
    fun brokenImageGivesNull() = runTest {
        assertNull(covers("not an image".encodeToByteArray()).load("/uploads/thumbnails/9.jpg"))
    }
}
