package com.example.relevantreasearchupdates.ui.pdfviewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.relevantreasearchupdates.data.AppDatabase
import com.example.relevantreasearchupdates.network.FileDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface PdfViewerState {
    data object Loading : PdfViewerState
    data class Ready(val file: File) : PdfViewerState
    data class Error(val message: String) : PdfViewerState
}

class PdfViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val downloader = FileDownloader(application)

    private val _state = MutableStateFlow<PdfViewerState>(PdfViewerState.Loading)
    val state: StateFlow<PdfViewerState> = _state.asStateFlow()

    private var loadedForPaperId: Long? = null

    fun load(paperId: Long) {
        if (loadedForPaperId == paperId) return
        loadedForPaperId = paperId
        _state.value = PdfViewerState.Loading
        viewModelScope.launch {
            try {
                val paper = db.paperDao().getById(paperId)
                if (paper == null) {
                    _state.value = PdfViewerState.Error("Paper not found")
                    return@launch
                }

                val localPath = paper.localPdfPath
                if (localPath != null && File(localPath).exists()) {
                    _state.value = PdfViewerState.Ready(File(localPath))
                    return@launch
                }

                val pdfUrl = paper.pdfUrl
                if (pdfUrl == null) {
                    _state.value = PdfViewerState.Error(
                        "No direct PDF is available for this paper. Use \"Open via EZproxy\" on the previous screen instead."
                    )
                    return@launch
                }

                val fileName = "paper_${paper.id}.pdf"
                val file = downloader.downloadPdf(pdfUrl, fileName)
                db.paperDao().updateLocalPdfPath(paper.id, file.absolutePath)
                _state.value = PdfViewerState.Ready(file)
            } catch (e: Exception) {
                _state.value = PdfViewerState.Error(e.message ?: "Failed to open PDF")
            }
        }
    }
}
