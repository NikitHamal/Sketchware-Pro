package pro.sketchware.activities.ai.chat.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import pro.sketchware.R;

public class SourcesAdapter extends RecyclerView.Adapter<SourcesAdapter.SourceViewHolder> {
    private List<JSONObject> sources;
    private Context context;

    public SourcesAdapter(List<JSONObject> sources, Context context) {
        this.sources = sources;
        this.context = context;
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_source, parent, false);
        return new SourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
        JSONObject source = sources.get(position);
        try {
            String title = source.optString("title", "Unknown Title");
            String url = source.optString("url", "");
            String snippet = source.optString("snippet", "");

            holder.title.setText(title);
            holder.url.setText(url);
            holder.snippet.setText(snippet);

            // Set click listener to open URL
            holder.itemView.setOnClickListener(v -> {
                if (!url.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    context.startActivity(intent);
                }
            });

        } catch (Exception e) {
            holder.title.setText("Error loading source");
            holder.url.setText("");
            holder.snippet.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    public static class SourceViewHolder extends RecyclerView.ViewHolder {
        TextView title, url, snippet;
        ImageView favicon;

        public SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.source_title);
            url = itemView.findViewById(R.id.source_url);
            snippet = itemView.findViewById(R.id.source_snippet);
            favicon = itemView.findViewById(R.id.source_favicon);
        }
    }
}