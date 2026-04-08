package pro.sketchware.ai.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pro.sketchware.ai.models.ModelInfo;
import pro.sketchware.databinding.ItemModelBinding;

public class ModelSelectorAdapter extends RecyclerView.Adapter<ModelSelectorAdapter.ViewHolder> {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelInfo model);
    }

    private final List<ModelInfo> models = new ArrayList<>();
    private final OnModelSelectedListener listener;
    @Nullable
    private String selectedModelId;

    public ModelSelectorAdapter(@NonNull OnModelSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemModelBinding binding = ItemModelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelInfo model = models.get(position);
        holder.bind(model);
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    public void setModels(@NonNull List<ModelInfo> newModels) {
        models.clear();
        models.addAll(newModels);
        notifyDataSetChanged();
    }

    public void setSelectedModelId(@Nullable String modelId) {
        String previousId = this.selectedModelId;
        this.selectedModelId = modelId;
        if (!Objects.equals(previousId, modelId)) {
            notifyDataSetChanged();
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemModelBinding binding;

        ViewHolder(@NonNull ItemModelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ModelInfo model) {
            String displayName = model.getName();
            if (TextUtils.isEmpty(displayName)) {
                displayName = model.getId();
            }
            binding.modelName.setText(displayName != null ? displayName : "");
            binding.modelId.setText(model.getId() != null ? model.getId() : "");

            boolean isSelected = model.getId() != null
                    && model.getId().equals(selectedModelId);
            if (binding.getRoot() instanceof MaterialCardView cardView) {
                cardView.setChecked(isSelected);
            }

            binding.getRoot().setOnClickListener(v -> {
                String previousId = selectedModelId;
                selectedModelId = model.getId();
                if (!Objects.equals(previousId, selectedModelId)) {
                    notifyDataSetChanged();
                }
                listener.onModelSelected(model);
            });
        }
    }
}
