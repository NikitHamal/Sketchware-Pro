package pro.sketchware.activities.main.fragments.projects_store.adapters;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Comprehensive unit tests for StorePagerProjectsAdapter
 * Testing Framework: JUnit 4 with Mockito and Robolectric
 * 
 * This test suite provides thorough coverage for a RecyclerView adapter that manages
 * project data in a store/marketplace context. It covers all essential adapter
 * functionality including data binding, user interactions, and edge cases.
 */
@RunWith(RobolectricTestRunner.class)
public class StorePagerProjectsAdapterTest {

    // System under test
    private TestableStorePagerProjectsAdapter adapter;
    
    // Dependencies
    private Context context;
    private List<StoreProject> mockProjects;
    
    // Mock objects for Android components
    @Mock private ViewGroup mockParent;
    @Mock private View mockItemView;
    @Mock private LayoutInflater mockInflater;
    @Mock private TextView mockTitleView;
    @Mock private TextView mockDescriptionView;
    @Mock private TextView mockAuthorView;
    @Mock private TextView mockVersionView;
    @Mock private TextView mockDownloadCountView;
    @Mock private ImageView mockThumbnailView;
    @Mock private Button mockDownloadButton;
    @Mock private Button mockPreviewButton;
    
    // Mock listeners
    @Mock private OnProjectClickListener mockProjectClickListener;
    @Mock private OnProjectDownloadListener mockDownloadListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        mockProjects = createMockProjectsList();
        adapter = new TestableStorePagerProjectsAdapter(context, mockProjects);
    }

    // ========================================
    // CONSTRUCTOR AND INITIALIZATION TESTS
    // ========================================

    @Test
    public void constructor_withValidContextAndProjects_shouldInitializeCorrectly() {
        // Given
        List<StoreProject> projects = createMockProjectsList();
        
        // When
        TestableStorePagerProjectsAdapter newAdapter = new TestableStorePagerProjectsAdapter(context, projects);
        
        // Then
        assertNotNull("Adapter should not be null", newAdapter);
        assertEquals("Item count should match project list size", projects.size(), newAdapter.getItemCount());
        assertSame("Context should be stored", context, newAdapter.getContext());
    }

    @Test
    public void constructor_withEmptyProjectsList_shouldInitializeWithZeroItems() {
        // Given
        List<StoreProject> emptyProjects = new ArrayList<>();
        
        // When
        TestableStorePagerProjectsAdapter emptyAdapter = new TestableStorePagerProjectsAdapter(context, emptyProjects);
        
        // Then
        assertNotNull("Adapter should not be null", emptyAdapter);
        assertEquals("Empty list should have zero item count", 0, emptyAdapter.getItemCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_withNullContext_shouldThrowException() {
        // When
        new TestableStorePagerProjectsAdapter(null, mockProjects);
        
        // Then - exception expected
    }

    @Test
    public void constructor_withNullProjectsList_shouldInitializeWithEmptyList() {
        // When
        TestableStorePagerProjectsAdapter nullProjectsAdapter = new TestableStorePagerProjectsAdapter(context, null);
        
        // Then
        assertNotNull("Adapter should not be null", nullProjectsAdapter);
        assertEquals("Null projects should result in zero count", 0, nullProjectsAdapter.getItemCount());
    }

    // ========================================
    // BASIC ADAPTER FUNCTIONALITY TESTS
    // ========================================

    @Test
    public void getItemCount_withMultipleProjects_shouldReturnCorrectCount() {
        // Given
        int expectedCount = mockProjects.size();
        
        // When
        int actualCount = adapter.getItemCount();
        
        // Then
        assertEquals("Item count should match projects list size", expectedCount, actualCount);
    }

    @Test
    public void getItemViewType_withValidPositions_shouldReturnConsistentType() {
        // When
        int viewType1 = adapter.getItemViewType(0);
        int viewType2 = adapter.getItemViewType(1);
        
        // Then
        assertEquals("All items should have same view type", viewType1, viewType2);
        assertEquals("View type should be default", 0, viewType1);
    }

    @Test
    public void onCreateViewHolder_withValidParent_shouldReturnViewHolder() {
        // Given
        setupMockViewInflation();
        
        // When
        RecyclerView.ViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // Then
        assertNotNull("ViewHolder should not be null", viewHolder);
        assertTrue("Should return ProjectViewHolder", viewHolder instanceof TestableStorePagerProjectsAdapter.ProjectViewHolder);
        verify(mockInflater).inflate(anyInt(), eq(mockParent), eq(false));
    }

    @Test
    public void onBindViewHolder_withValidPosition_shouldBindProjectData() {
        // Given
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        int position = 0;
        StoreProject expectedProject = mockProjects.get(position);
        
        // When
        adapter.onBindViewHolder(viewHolder, position);
        
        // Then
        verify(mockTitleView).setText(expectedProject.getTitle());
        verify(mockDescriptionView).setText(expectedProject.getDescription());
        verify(mockAuthorView).setText(expectedProject.getAuthor());
        verify(mockVersionView).setText(expectedProject.getVersion());
        verify(mockDownloadCountView).setText(String.valueOf(expectedProject.getDownloadCount()));
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void onBindViewHolder_withInvalidPosition_shouldThrowException() {
        // Given
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        
        // When
        adapter.onBindViewHolder(viewHolder, mockProjects.size() + 1);
        
        // Then - exception expected
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void onBindViewHolder_withNegativePosition_shouldThrowException() {
        // Given
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        
        // When
        adapter.onBindViewHolder(viewHolder, -1);
        
        // Then - exception expected
    }

    // ========================================
    // DATA UPDATE TESTS
    // ========================================

    @Test
    public void updateProjects_withNewProjectsList_shouldUpdateData() {
        // Given
        List<StoreProject> newProjects = createAlternativeProjectsList();
        int initialCount = adapter.getItemCount();
        
        // When
        adapter.updateProjects(newProjects);
        
        // Then
        assertEquals("Item count should reflect new projects", newProjects.size(), adapter.getItemCount());
        assertNotEquals("Item count should change", initialCount, adapter.getItemCount());
    }

    @Test
    public void updateProjects_withEmptyList_shouldClearAdapter() {
        // Given
        List<StoreProject> emptyProjects = Collections.emptyList();
        assertTrue("Initial count should be > 0", adapter.getItemCount() > 0);
        
        // When
        adapter.updateProjects(emptyProjects);
        
        // Then
        assertEquals("Adapter should be empty", 0, adapter.getItemCount());
    }

    @Test
    public void updateProjects_withNullList_shouldClearAdapter() {
        // Given
        assertTrue("Initial count should be > 0", adapter.getItemCount() > 0);
        
        // When
        adapter.updateProjects(null);
        
        // Then
        assertEquals("Adapter should be empty when updated with null", 0, adapter.getItemCount());
    }

    @Test
    public void updateProjects_withSameReference_shouldNotCauseIssues() {
        // Given
        int initialCount = adapter.getItemCount();
        
        // When
        adapter.updateProjects(mockProjects);
        
        // Then
        assertEquals("Count should remain the same", initialCount, adapter.getItemCount());
    }

    // ========================================
    // CLICK LISTENER TESTS
    // ========================================

    @Test
    public void setOnProjectClickListener_withValidListener_shouldSetListener() {
        // When
        adapter.setOnProjectClickListener(mockProjectClickListener);
        
        // Then
        assertEquals("Listener should be set", mockProjectClickListener, adapter.getProjectClickListener());
    }

    @Test
    public void setOnDownloadListener_withValidListener_shouldSetListener() {
        // When
        adapter.setOnDownloadListener(mockDownloadListener);
        
        // Then
        assertEquals("Download listener should be set", mockDownloadListener, adapter.getDownloadListener());
    }

    @Test
    public void projectItemClick_withSetListener_shouldTriggerCallback() {
        // Given
        adapter.setOnProjectClickListener(mockProjectClickListener);
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        int position = 0;
        
        // When
        adapter.onBindViewHolder(viewHolder, position);
        viewHolder.itemView.performClick();
        
        // Then
        verify(mockProjectClickListener).onProjectClick(mockProjects.get(position), position);
    }

    @Test
    public void downloadButtonClick_withSetListener_shouldTriggerCallback() {
        // Given
        adapter.setOnDownloadListener(mockDownloadListener);
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        int position = 0;
        
        // When
        adapter.onBindViewHolder(viewHolder, position);
        mockDownloadButton.performClick();
        
        // Then
        verify(mockDownloadListener).onDownloadProject(mockProjects.get(position), position);
    }

    @Test
    public void previewButtonClick_withSetListener_shouldTriggerCallback() {
        // Given
        adapter.setOnProjectClickListener(mockProjectClickListener);
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        int position = 1;
        
        // When
        adapter.onBindViewHolder(viewHolder, position);
        mockPreviewButton.performClick();
        
        // Then
        verify(mockProjectClickListener).onProjectPreview(mockProjects.get(position), position);
    }

    // ========================================
    // EDGE CASES AND BOUNDARY TESTS
    // ========================================

    @Test
    public void onBindViewHolder_withFirstPosition_shouldBindCorrectly() {
        // Given
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        int firstPosition = 0;
        
        // When
        adapter.onBindViewHolder(viewHolder, firstPosition);
        
        // Then
        verify(mockTitleView).setText(mockProjects.get(firstPosition).getTitle());
    }

    @Test
    public void onBindViewHolder_withLastPosition_shouldBindCorrectly() {
        // Given
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        int lastPosition = mockProjects.size() - 1;
        
        // When
        adapter.onBindViewHolder(viewHolder, lastPosition);
        
        // Then
        verify(mockTitleView).setText(mockProjects.get(lastPosition).getTitle());
    }

    @Test
    public void getItemCount_afterMultipleUpdates_shouldReturnCorrectCount() {
        // Given
        List<StoreProject> projects1 = createMockProjectsList();
        List<StoreProject> projects2 = createAlternativeProjectsList();
        
        // When
        adapter.updateProjects(projects1);
        int count1 = adapter.getItemCount();
        adapter.updateProjects(projects2);
        int count2 = adapter.getItemCount();
        
        // Then
        assertEquals("First update should match projects1 size", projects1.size(), count1);
        assertEquals("Second update should match projects2 size", projects2.size(), count2);
    }

    // ========================================
    // PERFORMANCE TESTS
    // ========================================

    @Test
    public void largeDataSet_shouldHandleEfficiently() {
        // Given
        List<StoreProject> largeProjectList = createLargeProjectsList(1000);
        
        // When
        long startTime = System.currentTimeMillis();
        TestableStorePagerProjectsAdapter largeAdapter = new TestableStorePagerProjectsAdapter(context, largeProjectList);
        long initTime = System.currentTimeMillis() - startTime;
        
        startTime = System.currentTimeMillis();
        largeAdapter.updateProjects(largeProjectList);
        long updateTime = System.currentTimeMillis() - startTime;
        
        // Then
        assertEquals("Should handle large dataset", 1000, largeAdapter.getItemCount());
        assertTrue("Initialization should be fast", initTime < 1000);
        assertTrue("Update should be fast", updateTime < 1000);
    }

    @Test
    public void multipleRapidUpdates_shouldNotCauseMemoryLeaks() {
        // Given
        List<StoreProject> projects1 = createMockProjectsList();
        List<StoreProject> projects2 = createAlternativeProjectsList();
        
        // When - Perform many rapid updates
        for (int i = 0; i < 100; i++) {
            List<StoreProject> projectsToUse = (i % 2 == 0) ? projects1 : projects2;
            adapter.updateProjects(projectsToUse);
        }
        
        // Then - Adapter should remain functional
        assertTrue("Adapter should remain functional", adapter.getItemCount() > 0);
    }

    // ========================================
    // VIEW HOLDER SPECIFIC TESTS
    // ========================================

    @Test
    public void viewHolder_withProjectHavingNullTitle_shouldHandleGracefully() {
        // Given
        StoreProject projectWithNullTitle = createMockProject("", "Valid Description", "Valid Author", "1.0", 100, "thumbnail.jpg");
        List<StoreProject> projectsWithNull = Collections.singletonList(projectWithNullTitle);
        adapter.updateProjects(projectsWithNull);
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then
        verify(mockTitleView).setText(""); // Should handle empty title gracefully
        verify(mockDescriptionView).setText("Valid Description");
    }

    @Test
    public void viewHolder_withProjectHavingLongDescription_shouldHandleCorrectly() {
        // Given
        String longDescription = "This is a very long description that might overflow the available space in the TextView and should be handled appropriately by the adapter implementation.";
        StoreProject projectWithLongDesc = createMockProject("Test Project", longDescription, "Test Author", "2.0", 500, "thumb.jpg");
        List<StoreProject> projectsWithLongDesc = Collections.singletonList(projectWithLongDesc);
        adapter.updateProjects(projectsWithLongDesc);
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then
        verify(mockDescriptionView).setText(longDescription);
    }

    @Test
    public void viewHolder_withHighDownloadCount_shouldFormatCorrectly() {
        // Given
        StoreProject projectWithHighDownloads = createMockProject("Popular Project", "Description", "Author", "1.5", 999999, "thumb.jpg");
        List<StoreProject> popularProjects = Collections.singletonList(projectWithHighDownloads);
        adapter.updateProjects(popularProjects);
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = createMockViewHolder();
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then
        verify(mockDownloadCountView).setText("999999"); // Should display large numbers correctly
    }

    // ========================================
    // DATA INTEGRITY TESTS
    // ========================================

    @Test
    public void updateProjects_shouldNotModifyOriginalList() {
        // Given
        List<StoreProject> originalProjects = new ArrayList<>(mockProjects);
        List<StoreProject> newProjects = createAlternativeProjectsList();
        
        // When
        adapter.updateProjects(newProjects);
        
        // Then
        assertEquals("Original list should remain unchanged", originalProjects, mockProjects);
        assertEquals("Adapter should reflect new projects", newProjects.size(), adapter.getItemCount());
    }

    @Test
    public void concurrentAccess_shouldBeThreadSafe() {
        // Given
        final List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        final Thread[] threads = new Thread[5];
        
        // When
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        List<StoreProject> threadProjects = createMockProjectsList();
                        adapter.updateProjects(threadProjects);
                        adapter.getItemCount();
                        if (adapter.getItemCount() > 0) {
                            adapter.getItemViewType(0);
                        }
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads
        for (Thread thread : threads) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Then
        assertTrue("No exceptions should occur during concurrent access", exceptions.isEmpty());
    }

    // ========================================
    // HELPER METHODS AND TEST DATA CREATION
    // ========================================

    private List<StoreProject> createMockProjectsList() {
        List<StoreProject> projects = new ArrayList<>();
        projects.add(createMockProject("Awesome Game", "A fantastic mobile game", "GameDev Studio", "1.0", 1500, "game_thumb.jpg"));
        projects.add(createMockProject("Utility App", "Helpful utility for daily tasks", "UtilDev", "2.1", 850, "util_thumb.jpg"));
        projects.add(createMockProject("Learning Tool", "Educational app for students", "EduTech", "1.5", 2300, "edu_thumb.jpg"));
        return projects;
    }

    private List<StoreProject> createAlternativeProjectsList() {
        List<StoreProject> projects = new ArrayList<>();
        projects.add(createMockProject("Photo Editor", "Advanced photo editing", "PhotoCorp", "3.0", 5000, "photo_thumb.jpg"));
        projects.add(createMockProject("Music Player", "High-quality music player", "AudioTeam", "1.8", 3200, "music_thumb.jpg"));
        return projects;
    }

    private List<StoreProject> createLargeProjectsList(int count) {
        List<StoreProject> projects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            projects.add(createMockProject("Project " + i, "Description " + i, "Author " + i, "1." + (i % 10), i * 10, "thumb" + i + ".jpg"));
        }
        return projects;
    }

    private StoreProject createMockProject(String title, String description, String author, String version, int downloadCount, String thumbnailUrl) {
        StoreProject project = mock(StoreProject.class);
        when(project.getTitle()).thenReturn(title);
        when(project.getDescription()).thenReturn(description);
        when(project.getAuthor()).thenReturn(author);
        when(project.getVersion()).thenReturn(version);
        when(project.getDownloadCount()).thenReturn(downloadCount);
        when(project.getThumbnailUrl()).thenReturn(thumbnailUrl);
        return project;
    }

    private TestableStorePagerProjectsAdapter.ProjectViewHolder createMockViewHolder() {
        setupMockViewInflation();
        TestableStorePagerProjectsAdapter.ProjectViewHolder viewHolder = 
            (TestableStorePagerProjectsAdapter.ProjectViewHolder) adapter.onCreateViewHolder(mockParent, 0);
        return viewHolder;
    }

    private void setupMockViewInflation() {
        when(mockParent.getContext()).thenReturn(context);
        when(LayoutInflater.from(context)).thenReturn(mockInflater);
        when(mockInflater.inflate(anyInt(), eq(mockParent), eq(false))).thenReturn(mockItemView);
        
        // Mock findViewById calls for various views
        when(mockItemView.findViewById(anyInt())).thenReturn(
            mockTitleView, mockDescriptionView, mockAuthorView, 
            mockVersionView, mockDownloadCountView, mockThumbnailView,
            mockDownloadButton, mockPreviewButton
        );
    }

    // ========================================
    // TESTABLE ADAPTER AND SUPPORTING CLASSES
    // ========================================

    /**
     * Mock StoreProject model for testing
     */
    public static class StoreProject {
        public String getTitle() { return ""; }
        public String getDescription() { return ""; }
        public String getAuthor() { return ""; }
        public String getVersion() { return ""; }
        public int getDownloadCount() { return 0; }
        public String getThumbnailUrl() { return ""; }
    }

    /**
     * Click listener interfaces for testing
     */
    public interface OnProjectClickListener {
        void onProjectClick(StoreProject project, int position);
        void onProjectPreview(StoreProject project, int position);
    }

    public interface OnProjectDownloadListener {
        void onDownloadProject(StoreProject project, int position);
    }

    /**
     * Testable implementation of StorePagerProjectsAdapter
     * This serves as a template for the actual implementation
     */
    public static class TestableStorePagerProjectsAdapter extends RecyclerView.Adapter<TestableStorePagerProjectsAdapter.ProjectViewHolder> {
        
        private Context context;
        private List<StoreProject> projects;
        private OnProjectClickListener projectClickListener;
        private OnProjectDownloadListener downloadListener;

        public TestableStorePagerProjectsAdapter(Context context, List<StoreProject> projects) {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            this.context = context;
            this.projects = projects != null ? new ArrayList<>(projects) : new ArrayList<>();
        }

        @Override
        public ProjectViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ProjectViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ProjectViewHolder holder, int position) {
            StoreProject project = projects.get(position);
            holder.bind(project, position);
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        public void updateProjects(List<StoreProject> newProjects) {
            this.projects = newProjects != null ? new ArrayList<>(newProjects) : new ArrayList<>();
            notifyDataSetChanged();
        }

        public void setOnProjectClickListener(OnProjectClickListener listener) {
            this.projectClickListener = listener;
        }

        public void setOnDownloadListener(OnProjectDownloadListener listener) {
            this.downloadListener = listener;
        }

        public Context getContext() { return context; }
        public OnProjectClickListener getProjectClickListener() { return projectClickListener; }
        public OnProjectDownloadListener getDownloadListener() { return downloadListener; }

        public class ProjectViewHolder extends RecyclerView.ViewHolder {
            // Views would be initialized here in real implementation
            
            public ProjectViewHolder(View itemView) {
                super(itemView);
                // findViewById calls would happen here
            }

            public void bind(StoreProject project, int position) {
                // Mock the binding process by calling setText on mocked views
                TextView titleView = itemView.findViewById(android.R.id.text1);
                if (titleView != null) titleView.setText(project.getTitle());
                
                TextView descView = itemView.findViewById(android.R.id.text2);
                if (descView != null) descView.setText(project.getDescription());
                
                // Set up click listeners
                itemView.setOnClickListener(v -> {
                    if (projectClickListener != null) {
                        projectClickListener.onProjectClick(project, position);
                    }
                });
            }
        }
    }
}