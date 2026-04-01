sed -i '213,234c\
        if (item.getItemId() == 1001) {\
            for (Pair<String, String> selectedItem : adapter.getSelectedItems()) {\
                String originalName = selectedItem.first;\
                String finalName = originalName;\
                int count = 1;\
                while (alreadyAddedImageNames.contains(finalName)) {\
                    finalName = originalName + "_" + count;\
                    count++;\
                }\
                String resFullname = selectedItem.second + File.separator + selected_icon_type + ".svg";\
                \
                Bundle bundle = new Bundle();\
                bundle.putString("iconName", finalName);\
                bundle.putString("iconPath", resFullname);\
                bundle.putInt("iconColor", selected_color);\
                bundle.putString("iconColorHex", selected_color_hex);\
                \
                selectedIcons.add(bundle);\
                alreadyAddedImageNames.add(finalName);\
            }\
            a.a.a.bB.a(ImportIconActivity.this, adapter.getSelectedItems().size() + " " + getString(pro.sketchware.R.string.design_manager_message_add_complete), a.a.a.bB.TOAST_NORMAL).show();\
            \
            adapter.clearSelection();' app/src/main/java/pro/sketchware/activities/importicon/ImportIconActivity.java
