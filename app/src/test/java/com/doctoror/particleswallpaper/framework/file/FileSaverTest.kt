/*
 * Copyright (C) 2018 Yaroslav Mytkalyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.doctoror.particleswallpaper.framework.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.standalone.StandAloneContext
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileSaverTest {

    private val filesDir = ApplicationProvider.getApplicationContext<Context>().filesDir
    private val uri = Uri.parse("content://file")
    private val sourceFile = File(filesDir, "sourceFile")
    private val targetFile = File(filesDir, "targetFile")
    private val fileContents = byteArrayOf(0, 1, 2)

    @Before
    fun setup() {
        sourceFile.writeBytes(fileContents)
    }

    @After
    fun tearDown() {
        sourceFile.delete()
        targetFile.delete()
        StandAloneContext.stopKoin()
    }

    @Test
    fun savesToPrivateFile() {
        // Given
        val context = mockContextForSourceFile()

        // When
        FileSaver(context).saveToPrivateFile(uri, targetFile)

        // Then
        val readFileContents = targetFile.readBytes()
        assertTrue(readFileContents.contentEquals(fileContents))
    }

    private fun mockContextForSourceFile(): Context {
        val fileDescriptor = ParcelFileDescriptor.open(
            sourceFile, ParcelFileDescriptor.MODE_READ_ONLY
        )

        val contentResolver: ContentResolver = mock {
            on(it.openFileDescriptor(uri, "r")).doReturn(fileDescriptor)
        }

        return mock {
            on(it.contentResolver).doReturn(contentResolver)
            on(it.filesDir).doReturn(filesDir)
        }
    }
}
