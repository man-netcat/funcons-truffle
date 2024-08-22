package trufflegen.main

import java.io.File

fun isFileOfType(file: File, fileType: String = "cbs"): Boolean {
    return file.isFile && file.extension == fileType
}