package chat.stoat.providers

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import chat.stoat.BuildConfig
import chat.stoat.R
import chat.stoat.api.StoatHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.io.File

class AttachmentProvider : FileProvider(R.xml.file_paths)


/**
 * Stream a remote attachment into an OutputStream without ever
 * materialising the complete file in memory.
 */
suspend fun streamAttachmentTo(
    resourceUrl: String,
    outputStream: OutputStream
) {
    val response = StoatHttp.get(resourceUrl)
    val channel = response.bodyAsChannel()

    // Bounded memory regardless of attachment size.
    val buffer = ByteArray(64 * 1024)

    while (true) {
        val read = channel.readAvailable(buffer)

        if (read < 0) {
            break
        }

        if (read == 0) {
            continue
        }

        outputStream.write(
            buffer,
            0,
            read
        )
    }

    outputStream.flush()
}

suspend fun getAttachmentContentUri(
    context: Context,
    resourceUrl: String,
    id: String,
    filename: String
): Uri {
    val attachmentsDir = File(context.cacheDir, "attachments")
    if (!attachmentsDir.exists()) {
        attachmentsDir.mkdir()
    }

    val file = File(
        attachmentsDir,
        "$id-$filename"
    )

    val partialFile = File(
        attachmentsDir,
        "$id-$filename.part"
    )

    withContext(Dispatchers.IO) {
        try {
            partialFile
                .outputStream()
                .buffered()
                .use { output ->
                    streamAttachmentTo(
                        resourceUrl,
                        output
                    )
                }

            if (
                file.exists() &&
                !file.delete()
            ) {
                error(
                    "Could not replace cached attachment"
                )
            }

            if (!partialFile.renameTo(file)) {
                partialFile.copyTo(
                    file,
                    overwrite = true
                )
                partialFile.delete()
            }
        } catch (error: Throwable) {
            partialFile.delete()
            throw error
        }
    }

    return FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        file
    )
}
