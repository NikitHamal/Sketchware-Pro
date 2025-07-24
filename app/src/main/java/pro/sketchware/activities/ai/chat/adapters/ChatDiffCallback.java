package pro.sketchware.activities.ai.chat.adapters;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

import pro.sketchware.activities.ai.chat.models.ChatMessage;

public class ChatDiffCallback extends DiffUtil.Callback {

    private final List<ChatMessage> oldList;
    private final List<ChatMessage> newList;

    public ChatDiffCallback(List<ChatMessage> oldList, List<ChatMessage> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return oldList.get(oldItemPosition).equals(newList.get(newItemPosition));
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        //you can return particular field for changes
        return super.getChangePayload(oldItemPosition, newItemPosition);
    }
}
