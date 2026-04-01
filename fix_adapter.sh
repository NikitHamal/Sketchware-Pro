sed -i '57,75c\
    public void toggleSelection(int position) {\
        Pair<String, String> item = getItem(position);\
        if (selectedItems.contains(item)) {\
            selectedItems.remove(item);\
        } else {\
            selectedItems.add(item);\
        }\
        notifyItemChanged(position);\
    }\
\
    public void clearSelection() {\
        selectedItems.clear();\
        notifyDataSetChanged();\
    }\
\
    public Set<Pair<String, String>> getSelectedItems() {\
        return selectedItems;\
    }' app/src/main/java/pro/sketchware/activities/importicon/adapters/IconAdapter.java
