package pro.sketchware.activities.main.fragments.projects_store.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.activities.main.fragments.projects_store.models.Comment;

/**
 * RecyclerView adapter for displaying comments in the projects store
 * Supports filtering, sorting, and click handling for comment items
 */
public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private final Context context;
    private List<Comment> comments;
    private List<Comment> filteredComments;
    private OnCommentClickListener clickListener;
    private final SimpleDateFormat dateFormat;

    public interface OnCommentClickListener {
        void onCommentClick(Comment comment, int position);
        void onCommentLongClick(Comment comment, int position);
    }

    public CommentsAdapter(Context context, List<Comment> comments) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        
        this.context = context;
        this.comments = comments != null ? new ArrayList<>(comments) : new ArrayList<>();
        this.filteredComments = new ArrayList<>(this.comments);
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = filteredComments.get(position);
        
        // Set author name
        String author = comment.getAuthor() != null ? comment.getAuthor() : "Anonymous";
        holder.authorTextView.setText(author);
        
        // Set content with timestamp
        String content = comment.getContent() != null ? comment.getContent() : "";
        String timestamp = formatTimestamp(comment.getTimestamp());
        String displayText = content + (content.isEmpty() ? "" : "\n") + timestamp;
        holder.contentTextView.setText(displayText);
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onCommentClick(comment, position);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (clickListener != null) {
                clickListener.onCommentLongClick(comment, position);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return filteredComments.size();
    }

    // Data management methods
    public void updateComments(List<Comment> newComments) {
        this.comments = newComments != null ? new ArrayList<>(newComments) : new ArrayList<>();
        this.filteredComments = new ArrayList<>(this.comments);
        notifyDataSetChanged();
    }

    public void addComment(Comment comment) {
        if (comment != null) {
            comments.add(comment);
            filteredComments.add(comment);
            notifyItemInserted(filteredComments.size() - 1);
        }
    }

    public void removeComment(int position) {
        if (position >= 0 && position < filteredComments.size()) {
            Comment removedComment = filteredComments.remove(position);
            comments.remove(removedComment);
            notifyItemRemoved(position);
        }
    }

    public Comment getComment(int position) {
        if (position >= 0 && position < filteredComments.size()) {
            return filteredComments.get(position);
        }
        return null;
    }

    public void clearComments() {
        comments.clear();
        filteredComments.clear();
        notifyDataSetChanged();
    }

    // Filtering and sorting methods
    public void filter(String query) {
        filteredComments.clear();
        
        if (query == null || query.trim().isEmpty()) {
            filteredComments.addAll(comments);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Comment comment : comments) {
                if (matchesQuery(comment, lowerCaseQuery)) {
                    filteredComments.add(comment);
                }
            }
        }
        
        notifyDataSetChanged();
    }

    private boolean matchesQuery(Comment comment, String query) {
        if (comment.getAuthor() != null && comment.getAuthor().toLowerCase().contains(query)) {
            return true;
        }
        if (comment.getContent() != null && comment.getContent().toLowerCase().contains(query)) {
            return true;
        }
        return false;
    }

    public void sortCommentsByTimestamp() {
        Collections.sort(filteredComments, (c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));
        notifyDataSetChanged();
    }

    public void sortCommentsByAuthor() {
        Collections.sort(filteredComments, (c1, c2) -> {
            String author1 = c1.getAuthor() != null ? c1.getAuthor() : "";
            String author2 = c2.getAuthor() != null ? c2.getAuthor() : "";
            return author1.compareToIgnoreCase(author2);
        });
        notifyDataSetChanged();
    }

    // Utility methods
    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "Unknown time";
        }
        
        try {
            Date date = new Date(timestamp);
            return dateFormat.format(date);
        } catch (Exception e) {
            return "Invalid date";
        }
    }

    // Listener setter
    public void setOnCommentClickListener(OnCommentClickListener listener) {
        this.clickListener = listener;
    }

    // ViewHolder class
    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        TextView authorTextView;
        TextView contentTextView;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            
            // Using simple_list_item_2 layout
            authorTextView = itemView.findViewById(android.R.id.text1);
            contentTextView = itemView.findViewById(android.R.id.text2);
        }
    }
}