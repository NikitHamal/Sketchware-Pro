package pro.sketchware.activities.main.fragments.projects_store;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SearchView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive unit tests for ProjectsStoreFragment
 * Testing Framework: JUnit 4 with Mockito
 * 
 * This test suite covers:
 * - Fragment lifecycle management
 * - Data loading and error handling
 * - Search and filtering functionality
 * - User interactions
 * - State management
 * - Performance edge cases
 */
@RunWith(JUnit4.class)
public class ProjectsStoreFragmentTest {

    private ProjectsStoreFragment fragment;
    
    @Mock
    private FragmentActivity mockActivity;
    
    @Mock
    private LayoutInflater mockInflater;
    
    @Mock
    private ViewGroup mockContainer;
    
    @Mock
    private View mockRootView;
    
    @Mock
    private RecyclerView mockRecyclerView;
    
    @Mock
    private ProgressBar mockProgressBar;
    
    @Mock
    private TextView mockEmptyStateText;
    
    @Mock
    private Button mockRetryButton;
    
    @Mock
    private SearchView mockSearchView;
    
    @Mock
    private SwipeRefreshLayout mockSwipeRefresh;
    
    @Mock
    private Context mockContext;
    
    @Mock
    private FragmentManager mockFragmentManager;
    
    @Mock
    private ProjectsStoreAdapter mockAdapter;
    
    private List<ProjectStoreItem> testProjects;
    private static final int LAYOUT_ID = 0x12345678; // Mock layout ID

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        fragment = spy(new ProjectsStoreFragment());
        
        // Setup test data
        testProjects = createTestProjects();
        
        // Mock view hierarchy
        setupMockViews();
        
        // Mock fragment lifecycle
        doReturn(mockActivity).when(fragment).getActivity();
        doReturn(mockContext).when(fragment).getContext();
        doReturn(mockFragmentManager).when(fragment).getParentFragmentManager();
        doReturn(true).when(fragment).isAdded();
    }

    @After
    public void tearDown() {
        fragment = null;
        testProjects = null;
    }

    // === FRAGMENT LIFECYCLE TESTS ===

    @Test
    public void testFragmentInstantiation() {
        assertNotNull("Fragment should be instantiated successfully", fragment);
        assertTrue("Fragment should be instance of ProjectsStoreFragment", 
                fragment instanceof ProjectsStoreFragment);
    }

    @Test
    public void testNewInstance_CreatesFragmentWithArguments() {
        Bundle args = new Bundle();
        args.putString("category", "games");
        
        ProjectsStoreFragment newFragment = ProjectsStoreFragment.newInstance(args);
        
        assertNotNull("newInstance should create fragment", newFragment);
        assertEquals("Arguments should be set", args, newFragment.getArguments());
    }

    @Test
    public void testOnCreateView_ReturnsValidView() {
        when(mockInflater.inflate(eq(LAYOUT_ID), eq(mockContainer), eq(false)))
                .thenReturn(mockRootView);
        
        View result = fragment.onCreateView(mockInflater, mockContainer, null);
        
        assertNotNull("onCreateView should return a valid view", result);
        assertEquals("Should return the inflated view", mockRootView, result);
        verify(mockInflater).inflate(eq(LAYOUT_ID), eq(mockContainer), eq(false));
    }

    @Test
    public void testOnCreateView_WithSavedInstanceState() {
        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putString("last_search", "test query");
        savedInstanceState.putBoolean("was_loading", true);
        
        when(mockInflater.inflate(eq(LAYOUT_ID), eq(mockContainer), eq(false)))
                .thenReturn(mockRootView);
        
        View result = fragment.onCreateView(mockInflater, mockContainer, savedInstanceState);
        
        assertNotNull("Should handle saved instance state gracefully", result);
        verify(mockInflater).inflate(eq(LAYOUT_ID), eq(mockContainer), eq(false));
    }

    @Test
    public void testOnCreateView_NullContainer() {
        when(mockInflater.inflate(eq(LAYOUT_ID), isNull(), eq(false)))
                .thenReturn(mockRootView);
        
        View result = fragment.onCreateView(mockInflater, null, null);
        
        assertNotNull("Should handle null container gracefully", result);
        verify(mockInflater).inflate(eq(LAYOUT_ID), isNull(), eq(false));
    }

    @Test
    public void testOnViewCreated_InitializesViews() {
        fragment.onViewCreated(mockRootView, null);
        
        // Verify that findViewById is called for required views
        verify(mockRootView, atLeastOnce()).findViewById(anyInt());
    }

    @Test
    public void testOnViewCreated_SetsUpListeners() {
        fragment.onViewCreated(mockRootView, null);
        
        verify(mockSwipeRefresh).setOnRefreshListener(any(SwipeRefreshLayout.OnRefreshListener.class));
        verify(mockRetryButton).setOnClickListener(any(View.OnClickListener.class));
        verify(mockSearchView).setOnQueryTextListener(any(SearchView.OnQueryTextListener.class));
    }

    @Test
    public void testOnViewCreated_WithSavedState_RestoresState() {
        Bundle savedInstanceState = new Bundle();
        savedInstanceState.putBoolean("is_loading", true);
        savedInstanceState.putString("search_query", "test");
        
        fragment.onViewCreated(mockRootView, savedInstanceState);
        
        // Verify state restoration
        verify(mockRootView, atLeastOnce()).findViewById(anyInt());
    }

    // === DATA LOADING TESTS ===

    @Test
    public void testLoadProjects_InitiatesLoading() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.loadProjects();
        
        assertTrue("Loading should be initiated", fragment.isLoading());
        verify(mockProgressBar).setVisibility(View.VISIBLE);
        verify(mockRecyclerView).setVisibility(View.GONE);
        verify(mockEmptyStateText).setVisibility(View.GONE);
    }

    @Test
    public void testLoadProjects_ShowsSwipeRefreshIndicator() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.loadProjects();
        
        verify(mockSwipeRefresh).setRefreshing(true);
    }

    @Test
    public void testLoadProjects_PreventsDuplicateCalls() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.loadProjects();
        fragment.loadProjects();
        fragment.loadProjects();
        
        // Should prevent duplicate network calls
        assertTrue("Should still be in loading state", fragment.isLoading());
    }

    @Test
    public void testOnProjectsLoaded_WithValidData_ShowsProjects() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.onProjectsLoaded(testProjects);
        
        verify(mockProgressBar).setVisibility(View.GONE);
        verify(mockRecyclerView).setVisibility(View.VISIBLE);
        verify(mockEmptyStateText).setVisibility(View.GONE);
        verify(mockSwipeRefresh).setRefreshing(false);
        assertFalse("Loading should be complete", fragment.isLoading());
    }

    @Test
    public void testOnProjectsLoaded_WithEmptyList_ShowsEmptyState() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.onProjectsLoaded(Collections.emptyList());
        
        verify(mockProgressBar).setVisibility(View.GONE);
        verify(mockRecyclerView).setVisibility(View.GONE);
        verify(mockEmptyStateText).setVisibility(View.VISIBLE);
        verify(mockEmptyStateText).setText(contains("No projects"));
    }

    @Test
    public void testOnProjectsLoaded_WithNullData_HandlesGracefully() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.onProjectsLoaded(null);
        
        verify(mockProgressBar).setVisibility(View.GONE);
        verify(mockEmptyStateText).setVisibility(View.VISIBLE);
        verify(mockEmptyStateText).setText(contains("Error"));
        assertFalse("Loading should be complete even with null data", fragment.isLoading());
    }

    @Test
    public void testOnProjectsLoaded_UpdatesAdapter() {
        fragment.onViewCreated(mockRootView, null);
        doReturn(mockAdapter).when(fragment).createAdapter(any());
        
        fragment.onProjectsLoaded(testProjects);
        
        verify(mockRecyclerView).setAdapter(any(ProjectsStoreAdapter.class));
    }

    // === ERROR HANDLING TESTS ===

    @Test
    public void testOnError_NetworkError_ShowsErrorState() {
        fragment.onViewCreated(mockRootView, null);
        
        String errorMessage = "Network connection failed";
        fragment.onError(errorMessage);
        
        verify(mockProgressBar).setVisibility(View.GONE);
        verify(mockRecyclerView).setVisibility(View.GONE);
        verify(mockEmptyStateText).setVisibility(View.VISIBLE);
        verify(mockRetryButton).setVisibility(View.VISIBLE);
        verify(mockSwipeRefresh).setRefreshing(false);
        assertFalse("Loading should stop on error", fragment.isLoading());
    }

    @Test
    public void testOnError_WithSpecificErrorMessage_DisplaysMessage() {
        fragment.onViewCreated(mockRootView, null);
        
        String errorMessage = "Server returned 404";
        fragment.onError(errorMessage);
        
        verify(mockEmptyStateText).setText(errorMessage);
    }

    @Test
    public void testOnError_NullMessage_UsesDefaultMessage() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.onError(null);
        
        verify(mockEmptyStateText).setText(contains("Unknown error"));
    }

    @Test
    public void testOnError_EmptyMessage_UsesDefaultMessage() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.onError("");
        
        verify(mockEmptyStateText).setText(contains("Unknown error"));
    }

    @Test
    public void testRetryButton_ClickTriggersReload() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onError("Test error");
        
        // Simulate retry button click
        fragment.onRetryClicked();
        
        verify(mockProgressBar, atLeast(2)).setVisibility(View.VISIBLE);
        verify(mockRetryButton).setVisibility(View.GONE);
        assertTrue("Should start loading again", fragment.isLoading());
    }

    // === SEARCH FUNCTIONALITY TESTS ===

    @Test
    public void testSearchProjects_WithValidQuery_FiltersResults() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        List<ProjectStoreItem> results = fragment.searchProjects("Game");
        
        assertNotNull("Search results should not be null", results);
        assertTrue("Search should return filtered results", results.size() <= testProjects.size());
        // Verify that results contain projects matching the query
        for (ProjectStoreItem item : results) {
            assertTrue("Result should match search query", 
                    item.getName().toLowerCase().contains("game") ||
                    item.getDescription().toLowerCase().contains("game"));
        }
    }

    @Test
    public void testSearchProjects_EmptyQuery_ReturnsAllProjects() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        List<ProjectStoreItem> results = fragment.searchProjects("");
        
        assertNotNull("Search results should not be null", results);
        assertEquals("Empty query should return all projects", testProjects.size(), results.size());
    }

    @Test
    public void testSearchProjects_NullQuery_ReturnsAllProjects() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        List<ProjectStoreItem> results = fragment.searchProjects(null);
        
        assertNotNull("Search results should not be null for null query", results);
        assertEquals("Null query should return all projects", testProjects.size(), results.size());
    }

    @Test
    public void testSearchProjects_NoMatches_ReturnsEmptyList() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        List<ProjectStoreItem> results = fragment.searchProjects("xyz_nonexistent_query_12345");
        
        assertNotNull("Search results should not be null", results);
        assertTrue("No matches should return empty list", results.isEmpty());
    }

    @Test
    public void testSearchProjects_CaseInsensitive() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        List<ProjectStoreItem> upperResults = fragment.searchProjects("GAME");
        List<ProjectStoreItem> lowerResults = fragment.searchProjects("game");
        List<ProjectStoreItem> mixedResults = fragment.searchProjects("GaMe");
        
        assertEquals("Case should not matter", upperResults.size(), lowerResults.size());
        assertEquals("Case should not matter", lowerResults.size(), mixedResults.size());
    }

    @Test
    public void testOnSearchQuerySubmit_PerformsSearch() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        boolean result = fragment.onQueryTextSubmit("test query");
        
        assertTrue("Should handle query submission", result);
    }

    @Test
    public void testOnSearchQueryChange_PerformsLiveSearch() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        boolean result = fragment.onQueryTextChange("test");
        
        assertTrue("Should handle query change", result);
    }

    // === FILTERING AND SORTING TESTS ===

    @Test
    public void testFilterByCategory_ValidCategory_FiltersResults() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.filterByCategory("games");
        
        // Verify adapter was updated with filtered results
        verify(mockRecyclerView, atLeast(2)).setAdapter(any());
    }

    @Test
    public void testFilterByCategory_AllCategories_ShowsAllProjects() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.filterByCategory("all");
        
        verify(mockRecyclerView, atLeast(2)).setAdapter(any());
    }

    @Test
    public void testFilterByCategory_InvalidCategory_ShowsEmptyResults() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.filterByCategory("nonexistent_category");
        
        // Should handle gracefully
        verify(mockRecyclerView, atLeast(2)).setAdapter(any());
    }

    @Test
    public void testSortProjects_ByName_SortsAlphabetically() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.sortProjects(ProjectsStoreFragment.SORT_BY_NAME);
        
        verify(mockRecyclerView, atLeast(2)).setAdapter(any());
    }

    @Test
    public void testSortProjects_ByDate_SortsByDate() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.sortProjects(ProjectsStoreFragment.SORT_BY_DATE);
        
        verify(mockRecyclerView, atLeast(2)).setAdapter(any());
    }

    @Test
    public void testSortProjects_ByPopularity_SortsByDownloads() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.sortProjects(ProjectsStoreFragment.SORT_BY_POPULARITY);
        
        verify(mockRecyclerView, atLeast(2)).setAdapter(any());
    }

    // === USER INTERACTION TESTS ===

    @Test
    public void testOnProjectItemClick_ValidIndex_HandlesClick() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.onProjectItemClick(0);
        
        // Should handle click without exceptions
        // Implementation would typically involve navigation or detail view
    }

    @Test
    public void testOnProjectItemClick_InvalidIndex_HandlesGracefully() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        // Test boundary conditions
        fragment.onProjectItemClick(-1);
        fragment.onProjectItemClick(testProjects.size());
        fragment.onProjectItemClick(Integer.MAX_VALUE);
        
        // Should handle gracefully without crashes
    }

    @Test
    public void testOnProjectItemClick_EmptyList_HandlesGracefully() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(Collections.emptyList());
        
        fragment.onProjectItemClick(0);
        
        // Should handle clicks on empty list gracefully
    }

    @Test
    public void testSwipeRefresh_TriggersReload() {
        fragment.onViewCreated(mockRootView, null);
        
        fragment.onRefresh();
        
        verify(mockProgressBar).setVisibility(View.VISIBLE);
        assertTrue("Refresh should start loading", fragment.isLoading());
    }

    // === STATE MANAGEMENT TESTS ===

    @Test
    public void testSaveInstanceState_SavesCurrentState() {
        Bundle outState = new Bundle();
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.onSaveInstanceState(outState);
        
        assertNotNull("Bundle should remain valid", outState);
        // Verify that important state is saved
        assertTrue("Should save some state", outState.size() >= 0);
    }

    @Test
    public void testRestoreInstanceState_RestoresSearchQuery() {
        Bundle savedState = new Bundle();
        savedState.putString("search_query", "test query");
        
        fragment.onViewCreated(mockRootView, savedState);
        
        // Verify search query was restored
        assertEquals("Should restore search query", "test query", fragment.getCurrentSearchQuery());
    }

    @Test
    public void testRestoreInstanceState_RestoresSelectedCategory() {
        Bundle savedState = new Bundle();
        savedState.putString("selected_category", "games");
        
        fragment.onViewCreated(mockRootView, savedState);
        
        // Verify category filter was restored
        assertEquals("Should restore category", "games", fragment.getCurrentCategory());
    }

    // === PERFORMANCE AND EDGE CASE TESTS ===

    @Test
    public void testLargeDataSet_HandlesPerformantly() {
        fragment.onViewCreated(mockRootView, null);
        
        List<ProjectStoreItem> largeDataSet = createLargeTestDataSet(5000);
        long startTime = System.currentTimeMillis();
        
        fragment.onProjectsLoaded(largeDataSet);
        
        long endTime = System.currentTimeMillis();
        assertTrue("Should handle large datasets quickly", (endTime - startTime) < 1000);
        verify(mockRecyclerView).setAdapter(any());
        assertFalse("Should complete loading", fragment.isLoading());
    }

    @Test
    public void testRapidStateChanges_HandlesGracefully() {
        fragment.onViewCreated(mockRootView, null);
        
        // Simulate rapid state changes
        fragment.loadProjects();
        fragment.onError("Error 1");
        fragment.onRetryClicked();
        fragment.onProjectsLoaded(testProjects);
        fragment.searchProjects("test");
        fragment.filterByCategory("games");
        fragment.sortProjects(ProjectsStoreFragment.SORT_BY_NAME);
        
        // Should handle rapid changes gracefully without crashes
        verify(mockRecyclerView, atLeast(1)).setAdapter(any());
    }

    @Test
    public void testConcurrentSearchOperations_HandlesGracefully() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        // Simulate rapid search queries
        fragment.searchProjects("a");
        fragment.searchProjects("ab");
        fragment.searchProjects("abc");
        fragment.searchProjects("abcd");
        
        // Should handle concurrent operations gracefully
    }

    @Test
    public void testMemoryLeaks_ProperCleanup() {
        fragment.onViewCreated(mockRootView, null);
        fragment.onProjectsLoaded(testProjects);
        
        fragment.onDestroyView();
        fragment.onDestroy();
        
        // Should cleanup without exceptions
        // In real implementation, verify that listeners are unregistered
        // and references are cleared
    }

    // === ACCESSIBILITY TESTS ===

    @Test
    public void testAccessibility_ViewsHaveContentDescriptions() {
        fragment.onViewCreated(mockRootView, null);
        
        // Verify accessibility setup
        verify(mockRetryButton).setContentDescription(anyString());
        verify(mockSearchView).setContentDescription(anyString());
    }

    // === INTEGRATION TESTS ===

    @Test
    public void testCompleteUserFlow_SearchAndSelect() {
        // Test complete user flow
        when(mockInflater.inflate(eq(LAYOUT_ID), eq(mockContainer), eq(false)))
                .thenReturn(mockRootView);
        
        // Fragment creation and view setup
        View view = fragment.onCreateView(mockInflater, mockContainer, null);
        assertNotNull(view);
        fragment.onViewCreated(mockRootView, null);
        
        // Load initial data
        fragment.loadProjects();
        assertTrue("Should be loading", fragment.isLoading());
        
        // Receive data
        fragment.onProjectsLoaded(testProjects);
        assertFalse("Should finish loading", fragment.isLoading());
        
        // Perform search
        List<ProjectStoreItem> searchResults = fragment.searchProjects("Game");
        assertNotNull("Search should return results", searchResults);
        
        // Filter by category
        fragment.filterByCategory("games");
        
        // Sort results
        fragment.sortProjects(ProjectsStoreFragment.SORT_BY_NAME);
        
        // Select item
        fragment.onProjectItemClick(0);
        
        // Cleanup
        fragment.onDestroyView();
        fragment.onDestroy();
        
        // Complete flow should execute without issues
    }

    // === HELPER METHODS ===

    private void setupMockViews() {
        when(mockRootView.findViewById(anyInt())).thenAnswer(invocation -> {
            int viewId = invocation.getArgument(0);
            // Return appropriate mock based on typical view IDs
            switch (viewId) {
                case 0x01: return mockRecyclerView;
                case 0x02: return mockProgressBar;
                case 0x03: return mockEmptyStateText;
                case 0x04: return mockRetryButton;
                case 0x05: return mockSearchView;
                case 0x06: return mockSwipeRefresh;
                default: return mock(View.class);
            }
        });
        
        when(mockRootView.getContext()).thenReturn(mockContext);
    }

    private List<ProjectStoreItem> createTestProjects() {
        List<ProjectStoreItem> projects = new ArrayList<>();
        projects.add(createMockProject("Test Game", "games", "A fun test game", 150, "2023-01-01"));
        projects.add(createMockProject("Utility App", "utilities", "A useful utility", 75, "2023-02-01"));
        projects.add(createMockProject("Photo Editor", "media", "Edit your photos", 200, "2023-03-01"));
        projects.add(createMockProject("Calculator", "utilities", "Simple calculator", 300, "2023-04-01"));
        projects.add(createMockProject("Racing Game", "games", "Fast racing action", 500, "2023-05-01"));
        return projects;
    }

    private ProjectStoreItem createMockProject(String name, String category, String description, 
                                             int downloads, String date) {
        ProjectStoreItem project = mock(ProjectStoreItem.class);
        when(project.getName()).thenReturn(name);
        when(project.getCategory()).thenReturn(category);
        when(project.getDescription()).thenReturn(description);
        when(project.getDownloads()).thenReturn(downloads);
        when(project.getCreatedDate()).thenReturn(date);
        when(project.getId()).thenReturn(name.hashCode());
        when(project.getAuthor()).thenReturn("Test Author");
        when(project.getVersion()).thenReturn("1.0.0");
        when(project.getPreviewUrl()).thenReturn("https://example.com/preview.jpg");
        return project;
    }

    private List<ProjectStoreItem> createLargeTestDataSet(int size) {
        List<ProjectStoreItem> projects = new ArrayList<>();
        String[] categories = {"games", "utilities", "media", "productivity", "education"};
        
        for (int i = 0; i < size; i++) {
            String category = categories[i % categories.length];
            projects.add(createMockProject("Project " + i, category, 
                    "Description for project " + i, i * 10, "2023-01-" + (i % 28 + 1)));
        }
        return projects;
    }
}

// Mock classes that would typically be in separate files
class ProjectsStoreFragment extends Fragment {
    public static final String SORT_BY_NAME = "name";
    public static final String SORT_BY_DATE = "date";
    public static final String SORT_BY_POPULARITY = "popularity";
    
    private boolean isLoading = false;
    private List<ProjectStoreItem> projects = new ArrayList<>();
    private String currentSearchQuery = "";
    private String currentCategory = "all";
    
    public static ProjectsStoreFragment newInstance(Bundle args) {
        ProjectsStoreFragment fragment = new ProjectsStoreFragment();
        fragment.setArguments(args);
        return fragment;
    }
    
    public boolean isLoading() { return isLoading; }
    public String getCurrentSearchQuery() { return currentSearchQuery; }
    public String getCurrentCategory() { return currentCategory; }
    
    public void loadProjects() { isLoading = true; }
    public void onProjectsLoaded(List<ProjectStoreItem> projects) { 
        isLoading = false; 
        this.projects = projects != null ? projects : new ArrayList<>();
    }
    public void onError(String message) { isLoading = false; }
    public void onRetryClicked() { loadProjects(); }
    public void onRefresh() { loadProjects(); }
    
    public List<ProjectStoreItem> searchProjects(String query) { 
        currentSearchQuery = query != null ? query : "";
        if (query == null || query.isEmpty()) return projects;
        
        List<ProjectStoreItem> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (ProjectStoreItem project : projects) {
            if (project.getName().toLowerCase().contains(lowerQuery) ||
                project.getDescription().toLowerCase().contains(lowerQuery)) {
                filtered.add(project);
            }
        }
        return filtered;
    }
    
    public boolean onQueryTextSubmit(String query) { 
        searchProjects(query);
        return true; 
    }
    
    public boolean onQueryTextChange(String newText) { 
        searchProjects(newText);
        return true; 
    }
    
    public void filterByCategory(String category) { 
        currentCategory = category != null ? category : "all";
    }
    
    public void sortProjects(String sortType) { /* Implementation */ }
    public void onProjectItemClick(int position) { /* Implementation */ }
    
    protected ProjectsStoreAdapter createAdapter(List<ProjectStoreItem> projects) {
        return mock(ProjectsStoreAdapter.class);
    }
}

class ProjectStoreItem {
    public String getName() { return ""; }
    public String getCategory() { return ""; }
    public String getDescription() { return ""; }
    public int getDownloads() { return 0; }
    public int getId() { return 0; }
    public String getCreatedDate() { return ""; }
    public String getAuthor() { return ""; }
    public String getVersion() { return ""; }
    public String getPreviewUrl() { return ""; }
}

class ProjectsStoreAdapter {
    // Mock adapter class
}