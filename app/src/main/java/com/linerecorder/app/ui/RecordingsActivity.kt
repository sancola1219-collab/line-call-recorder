package com.linerecorder.app.ui

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.linerecorder.app.R
import com.linerecorder.app.databinding.ActivityRecordingsBinding
import com.linerecorder.app.model.Recording
import com.linerecorder.app.util.RecordingManager

/**
 * 錄音列表 Activity
 */
class RecordingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var recordingManager: RecordingManager
    private lateinit var adapter: RecordingsAdapter
    
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingId: Long? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        recordingManager = RecordingManager(this)
        
        setupToolbar()
        setupRecyclerView()
        loadRecordings()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = RecordingsAdapter(
            onPlayClick = { recording -> togglePlayback(recording) },
            onMoreClick = { recording, view -> showPopupMenu(recording, view) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun loadRecordings() {
        val recordings = recordingManager.getAllRecordings()
        
        if (recordings.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            adapter.submitList(recordings)
        }
    }
    
    private fun togglePlayback(recording: Recording) {
        if (currentPlayingId == recording.id) {
            // 停止播放
            stopPlayback()
        } else {
            // 開始播放
            startPlayback(recording)
        }
    }
    
    private fun startPlayback(recording: Recording) {
        releaseMediaPlayer()
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    stopPlayback()
                }
            }
            
            currentPlayingId = recording.id
            adapter.setPlayingId(recording.id)
            
        } catch (e: Exception) {
            Toast.makeText(this, "播放失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopPlayback() {
        releaseMediaPlayer()
        currentPlayingId = null
        adapter.setPlayingId(null)
    }
    
    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
    
    private fun showPopupMenu(recording: Recording, anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.menu_recording, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_share -> {
                    shareRecording(recording)
                    true
                }
                R.id.action_delete -> {
                    confirmDelete(recording)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun shareRecording(recording: Recording) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                recording.file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
            
        } catch (e: Exception) {
            Toast.makeText(this, "分享失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun confirmDelete(recording: Recording) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                deleteRecording(recording)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteRecording(recording: Recording) {
        if (currentPlayingId == recording.id) {
            stopPlayback()
        }
        
        if (recordingManager.deleteRecording(recording)) {
            Toast.makeText(this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
            loadRecordings()
        } else {
            Toast.makeText(this, "刪除失敗", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 錄音列表 Adapter
 */
class RecordingsAdapter(
    private val onPlayClick: (Recording) -> Unit,
    private val onMoreClick: (Recording, View) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Recording, RecordingsAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<Recording>() {
        override fun areItemsTheSame(oldItem: Recording, newItem: Recording) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Recording, newItem: Recording) = oldItem == newItem
    }
) {
    
    private var playingId: Long? = null
    
    fun setPlayingId(id: Long?) {
        val oldPlayingId = playingId
        playingId = id
        
        // 更新舊的播放項目
        oldPlayingId?.let { oldId ->
            currentList.indexOfFirst { it.id == oldId }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }
        
        // 更新新的播放項目
        id?.let { newId ->
            currentList.indexOfFirst { it.id == newId }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val binding = com.linerecorder.app.databinding.ItemRecordingBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: com.linerecorder.app.databinding.ItemRecordingBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        
        fun bind(recording: Recording) {
            binding.fileName.text = recording.fileName
            binding.recordingDate.text = recording.formattedDate
            binding.recordingDuration.text = recording.formattedDuration
            binding.fileSize.text = recording.formattedFileSize
            
            // 更新播放按鈕狀態
            val isPlaying = playingId == recording.id
            binding.btnPlay.setImageResource(
                if (isPlaying) com.linerecorder.app.R.drawable.ic_stop
                else com.linerecorder.app.R.drawable.ic_play
            )
            
            binding.btnPlay.setOnClickListener {
                onPlayClick(recording)
            }
            
            binding.btnMore.setOnClickListener {
                onMoreClick(recording, it)
            }
        }
    }
}
