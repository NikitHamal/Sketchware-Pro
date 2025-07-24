package pro.sketchware.activities.ai.chat.ui;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.ai.chat.FileUploadManager;
import pro.sketchware.activities.ai.chat.models.ChatMessage;

public class AttachedFilesManager {

    private final Context context;
    private final ViewGroup container;
    private final FileUploadManager fileUploadManager;
    private final List<ChatMessage.AttachedFile> pendingFiles = new ArrayList<>();

    public AttachedFilesManager(Context context, ViewGroup container, FileUploadManager fileUploadManager) {
        this.context = context;
        this.container = container;
        this.fileUploadManager = fileUploadManager;
    }

    public void uploadFile(Uri fileUri) {
        fileUploadManager.uploadFile(fileUri, new FileUploadManager.FileUploadCallback() {
            @Override
            public void onUploadStart(String fileName) {
                Toast.makeText(context, "Uploading " + fileName + "...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUploadProgress(String fileName, int progress) {
                // Could show progress dialog here
            }

            @Override
            public void onUploadSuccess(ChatMessage.AttachedFile attachedFile) {
                pendingFiles.add(attachedFile);
                updateUI();
                Toast.makeText(context, "File uploaded successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUploadError(String fileName, String error) {
                Toast.makeText(context, "Upload failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    public List<ChatMessage.AttachedFile> getPendingFiles() {
        return pendingFiles;
    }

    public void clearPendingFiles() {
        pendingFiles.clear();
        updateUI();
    }

    private void updateUI() {
        if (pendingFiles.isEmpty()) {
            container.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);
            container.removeAllViews();

            for (ChatMessage.AttachedFile file : pendingFiles) {
                View fileView = LayoutInflater.from(context).inflate(R.layout.item_attached_file, container, false);

                TextView fileName = fileView.findViewById(R.id.file_name);
                TextView fileSize = fileView.findViewById(R.id.file_size);
                ImageView fileRemove = fileView.findViewById(R.id.file_remove);

                fileName.setText(file.getName());
                fileSize.setText(file.getFormattedSize());

                fileRemove.setOnClickListener(v -> {
                    pendingFiles.remove(file);
                    updateUI();
                });

                container.addView(fileView);
            }
        }
    }
}
