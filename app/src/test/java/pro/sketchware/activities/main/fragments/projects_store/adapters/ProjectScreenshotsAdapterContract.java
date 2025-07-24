package pro.sketchware.activities.main.fragments.projects_store.adapters;

import android.content.Context;
import java.util.List;

/**
 * Contract interface defining the expected behavior of ProjectScreenshotsAdapter
 * This serves as a reference for implementation and testing.
 * 
 * The adapter should display project screenshots in a RecyclerView with:
 * - Support for various image formats (PNG, JPG, JPEG, WEBP)
 * - Proper error handling for missing/corrupted images
 * - Efficient image loading and caching
 * - Click handling for screenshot preview/selection
 */
public interface ProjectScreenshotsAdapterContract {
    
    /**
     * Initialize adapter with context and screenshot file paths
     * @param context Android context for resource access
     * @param screenshots List of screenshot file paths/URLs
     */
    void initialize(Context context, List<String> screenshots);
    
    /**
     * Get the number of screenshots to display
     * @return Number of screenshots
     */
    int getItemCount();
    
    /**
     * Update the screenshots list and notify adapter
     * @param newScreenshots Updated list of screenshots
     */
    void updateScreenshots(List<String> newScreenshots);
    
    /**
     * Set click listener for screenshot selection
     * @param listener Click listener interface
     */
    void setOnScreenshotClickListener(OnScreenshotClickListener listener);
    
    /**
     * Interface for handling screenshot clicks
     */
    interface OnScreenshotClickListener {
        void onScreenshotClick(String screenshotPath, int position);
    }
}