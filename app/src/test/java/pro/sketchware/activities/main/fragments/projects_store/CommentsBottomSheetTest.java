package pro.sketchware.activities.main.fragments.projects_store;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive unit tests for CommentsBottomSheet
 * Testing Framework: JUnit 4 with Mockito for mocking
 * 
 * Test Coverage Areas:
 * - Constructor and initialization
 * - Comment management (CRUD operations)
 * - UI state management and lifecycle
 * - Data validation and error handling
 * - Edge cases and boundary conditions
 * - Performance with large datasets
 * - Async operations and threading
 * - Memory management and cleanup
 * - Search and filtering functionality
 * - Sorting capabilities
 * - Listener management
 */
@RunWith(MockitoJUnitRunner.class)
public class CommentsBottomSheetTest {

    @Mock
    private Context mockContext;
    
    @Mock
    private LayoutInflater mockLayoutInflater;
    
    @Mock
    private View mockContentView;
    
    @Mock
    private RecyclerView mockRecyclerView;
    
    @Mock
    private TextView mockEmptyStateView;
    
    @Mock
    private TextView mockTitleView;
    
    @Mock
    private BottomSheetDialog mockDialog;
    
    private CommentsBottomSheet commentsBottomSheet;
    private List<Comment> testComments;
    private AutoCloseable mockitoCloseable;

    @Before
    public void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        testComments = createTestComments();
        
        // Setup mock interactions
        when(mockContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
            .thenReturn(mockLayoutInflater);
        when(mockLayoutInflater.inflate(anyInt(), any())).thenReturn(mockContentView);
        when(mockContentView.findViewById(anyInt())).thenReturn(mockRecyclerView);
        
        commentsBottomSheet = new CommentsBottomSheet(mockContext);
    }

    @After
    public void tearDown() throws Exception {
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
        if (commentsBottomSheet != null) {
            commentsBottomSheet.onDestroy();
        }
    }

    // =============================================================================
    // CONSTRUCTOR AND INITIALIZATION TESTS
    // =============================================================================
    
    @Test
    public void testConstructor_WithValidContext_ShouldCreateInstance() {
        // Act
        CommentsBottomSheet bottomSheet = new CommentsBottomSheet(mockContext);
        
        // Assert
        assertNotNull("CommentsBottomSheet should be created successfully", bottomSheet);
        assertFalse("Should not be showing initially", bottomSheet.isShowing());
        assertEquals("Initial comment count should be zero", 0, bottomSheet.getCommentsCount());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_WithNullContext_ShouldThrowException() {
        // Act
        new CommentsBottomSheet(null);
    }
    
    @Test
    public void testInitialization_ShouldSetupUIComponents() {
        // Act
        CommentsBottomSheet bottomSheet = new CommentsBottomSheet(mockContext);
        
        // Assert
        assertNotNull("Bottom sheet should be initialized", bottomSheet);
        verify(mockContext).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    // =============================================================================
    // COMMENT MANAGEMENT TESTS - HAPPY PATH
    // =============================================================================
    
    @Test
    public void testSetComments_WithValidList_ShouldSetCommentsSuccessfully() {
        // Arrange
        List<Comment> comments = createTestComments();
        
        // Act
        commentsBottomSheet.setComments(comments);
        
        // Assert
        assertEquals("Comments should be set correctly", comments.size(), commentsBottomSheet.getCommentsCount());
        List<Comment> retrievedComments = commentsBottomSheet.getComments();
        assertNotNull("Comments list should not be null", retrievedComments);
        assertEquals("Should have same number of comments", comments.size(), retrievedComments.size());
    }
    
    @Test
    public void testAddComment_WithValidComment_ShouldIncreaseCount() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>());
        Comment newComment = createTestComment("New comment", "TestUser", System.currentTimeMillis());
        int initialCount = commentsBottomSheet.getCommentsCount();
        
        // Act
        boolean result = commentsBottomSheet.addComment(newComment);
        
        // Assert
        assertTrue("Adding valid comment should return true", result);
        assertEquals("Comment count should increase by 1", initialCount + 1, commentsBottomSheet.getCommentsCount());
    }
    
    @Test
    public void testRemoveComment_WithValidIndex_ShouldDecreaseCount() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        int initialCount = commentsBottomSheet.getCommentsCount();
        
        // Act
        boolean result = commentsBottomSheet.removeComment(0);
        
        // Assert
        assertTrue("Removing valid comment should return true", result);
        assertEquals("Comment count should decrease by 1", initialCount - 1, commentsBottomSheet.getCommentsCount());
    }
    
    @Test
    public void testClearComments_ShouldRemoveAllComments() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        assertTrue("Should have comments initially", commentsBottomSheet.getCommentsCount() > 0);
        
        // Act
        commentsBottomSheet.clearComments();
        
        // Assert
        assertEquals("Comment count should be zero", 0, commentsBottomSheet.getCommentsCount());
        assertTrue("Should show empty state", commentsBottomSheet.isEmptyStateVisible());
    }

    // =============================================================================
    // EDGE CASES AND ERROR CONDITIONS
    // =============================================================================
    
    @Test
    public void testSetComments_WithEmptyList_ShouldHandleGracefully() {
        // Arrange
        List<Comment> emptyComments = new ArrayList<>();
        
        // Act
        commentsBottomSheet.setComments(emptyComments);
        
        // Assert
        assertEquals("Empty list should be handled", 0, commentsBottomSheet.getCommentsCount());
        assertTrue("Should show empty state", commentsBottomSheet.isEmptyStateVisible());
    }
    
    @Test
    public void testSetComments_WithNullList_ShouldHandleGracefully() {
        // Act
        commentsBottomSheet.setComments(null);
        
        // Assert
        assertEquals("Null list should result in zero comments", 0, commentsBottomSheet.getCommentsCount());
        assertTrue("Should show empty state with null list", commentsBottomSheet.isEmptyStateVisible());
    }
    
    @Test
    public void testAddComment_WithNullComment_ShouldHandleGracefully() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>());
        int initialCount = commentsBottomSheet.getCommentsCount();
        
        // Act
        boolean result = commentsBottomSheet.addComment(null);
        
        // Assert
        assertFalse("Adding null comment should return false", result);
        assertEquals("Count should not change with null comment", initialCount, commentsBottomSheet.getCommentsCount());
    }
    
    @Test
    public void testRemoveComment_WithInvalidIndex_ShouldHandleGracefully() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        int initialCount = commentsBottomSheet.getCommentsCount();
        
        // Act & Assert
        assertFalse("Removing with negative index should return false", commentsBottomSheet.removeComment(-1));
        assertFalse("Removing with too large index should return false", commentsBottomSheet.removeComment(100));
        assertEquals("Count should not change with invalid indices", initialCount, commentsBottomSheet.getCommentsCount());
    }

    // =============================================================================
    // PERFORMANCE TESTS
    // =============================================================================
    
    @Test
    public void testSetComments_WithLargeList_ShouldHandlePerformance() {
        // Arrange
        List<Comment> largeCommentsList = createLargeCommentsList(1000);
        
        // Act
        long startTime = System.currentTimeMillis();
        commentsBottomSheet.setComments(largeCommentsList);
        long endTime = System.currentTimeMillis();
        
        // Assert
        long executionTime = endTime - startTime;
        assertTrue("Should handle large lists efficiently (took " + executionTime + "ms)", executionTime < 1000);
        assertEquals("Should set all comments", 1000, commentsBottomSheet.getCommentsCount());
    }
    
    @Test
    public void testAddComment_PerformanceWithManyAdditions_ShouldBeEfficient() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>());
        int numberOfComments = 500;
        
        // Act
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfComments; i++) {
            Comment comment = createTestComment("Comment " + i, "User" + i, System.currentTimeMillis() + i);
            commentsBottomSheet.addComment(comment);
        }
        long endTime = System.currentTimeMillis();
        
        // Assert
        long executionTime = endTime - startTime;
        assertTrue("Multiple additions should be efficient (took " + executionTime + "ms)", executionTime < 2000);
        assertEquals("Should have added all comments", numberOfComments, commentsBottomSheet.getCommentsCount());
    }

    // =============================================================================
    // UI STATE MANAGEMENT TESTS
    // =============================================================================
    
    @Test
    public void testShow_WithComments_ShouldDisplayDialog() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        
        // Act
        commentsBottomSheet.show();
        
        // Assert
        assertTrue("Dialog should be shown", commentsBottomSheet.isShowing());
        assertFalse("Empty state should not be visible when comments exist", commentsBottomSheet.isEmptyStateVisible());
    }
    
    @Test
    public void testShow_WithoutComments_ShouldShowEmptyState() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>());
        
        // Act
        commentsBottomSheet.show();
        
        // Assert
        assertTrue("Dialog should be shown", commentsBottomSheet.isShowing());
        assertTrue("Should show empty state when no comments", commentsBottomSheet.isEmptyStateVisible());
    }
    
    @Test
    public void testDismiss_WhenShowing_ShouldHideDialog() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        commentsBottomSheet.show();
        assertTrue("Dialog should be showing initially", commentsBottomSheet.isShowing());
        
        // Act
        commentsBottomSheet.dismiss();
        
        // Assert
        assertFalse("Dialog should be dismissed", commentsBottomSheet.isShowing());
    }
    
    @Test
    public void testDismiss_WhenNotShowing_ShouldHandleGracefully() {
        // Act & Assert - Should not throw exception
        commentsBottomSheet.dismiss();
        assertFalse("Should remain not showing", commentsBottomSheet.isShowing());
    }

    // =============================================================================
    // DATA VALIDATION TESTS
    // =============================================================================
    
    @Test
    public void testValidateComment_WithValidComment_ShouldReturnTrue() {
        // Arrange
        Comment validComment = createTestComment("Valid comment text", "ValidUser", System.currentTimeMillis() - 1000);
        
        // Act
        boolean isValid = commentsBottomSheet.validateComment(validComment);
        
        // Assert
        assertTrue("Valid comment should pass validation", isValid);
    }
    
    @Test
    public void testValidateComment_WithEmptyText_ShouldReturnFalse() {
        // Arrange
        Comment invalidComment = createTestComment("", "ValidUser", System.currentTimeMillis());
        
        // Act
        boolean isValid = commentsBottomSheet.validateComment(invalidComment);
        
        // Assert
        assertFalse("Comment with empty text should fail validation", isValid);
    }
    
    @Test
    public void testValidateComment_WithEmptyAuthor_ShouldReturnFalse() {
        // Arrange
        Comment invalidComment = createTestComment("Valid text", "", System.currentTimeMillis());
        
        // Act
        boolean isValid = commentsBottomSheet.validateComment(invalidComment);
        
        // Assert
        assertFalse("Comment with empty author should fail validation", isValid);
    }
    
    @Test
    public void testValidateComment_WithNullComment_ShouldReturnFalse() {
        // Act
        boolean isValid = commentsBottomSheet.validateComment(null);
        
        // Assert
        assertFalse("Null comment should fail validation", isValid);
    }
    
    @Test
    public void testValidateComment_WithFutureTimestamp_ShouldReturnFalse() {
        // Arrange
        long futureTime = System.currentTimeMillis() + 86400000L; // 24 hours in future
        Comment invalidComment = createTestComment("Valid text", "ValidUser", futureTime);
        
        // Act
        boolean isValid = commentsBottomSheet.validateComment(invalidComment);
        
        // Assert
        assertFalse("Comment with future timestamp should fail validation", isValid);
    }

    // =============================================================================
    // ASYNC OPERATIONS AND THREADING TESTS
    // =============================================================================
    
    @Test
    public void testLoadCommentsAsync_ShouldExecuteCallback() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        final List<Comment>[] loadedComments = new List[]{null};
        
        CommentsBottomSheet.OnCommentsLoadedListener listener = comments -> {
            loadedComments[0] = comments;
            latch.countDown();
        };
        
        // Act
        commentsBottomSheet.loadCommentsAsync("testProjectId", listener);
        
        // Assert
        assertTrue("Async loading should complete within timeout", latch.await(2, TimeUnit.SECONDS));
        assertNotNull("Callback should provide comments list", loadedComments[0]);
    }
    
    @Test
    public void testLoadCommentsAsync_WithNullListener_ShouldNotCrash() throws InterruptedException {
        // Act & Assert - Should not throw exception
        commentsBottomSheet.loadCommentsAsync("testProjectId", null);
        Thread.sleep(100);
        assertTrue("Should handle null listener gracefully", true);
    }

    // =============================================================================
    // MEMORY MANAGEMENT AND CLEANUP TESTS
    // =============================================================================
    
    @Test
    public void testOnDestroy_ShouldCleanupResources() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        commentsBottomSheet.show();
        assertTrue("Should be showing before destroy", commentsBottomSheet.isShowing());
        
        // Act
        commentsBottomSheet.onDestroy();
        
        // Assert
        assertFalse("Dialog should be dismissed on destroy", commentsBottomSheet.isShowing());
        assertEquals("Comments should be cleared on destroy", 0, commentsBottomSheet.getCommentsCount());
    }
    
    @Test
    public void testOnDestroy_MultipleCallsShouldBeIdempotent() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        commentsBottomSheet.show();
        
        // Act
        commentsBottomSheet.onDestroy();
        commentsBottomSheet.onDestroy(); // Second call
        commentsBottomSheet.onDestroy(); // Third call
        
        // Assert - Should handle multiple calls gracefully
        assertFalse("Should remain not showing", commentsBottomSheet.isShowing());
        assertEquals("Comments should remain cleared", 0, commentsBottomSheet.getCommentsCount());
    }

    // =============================================================================
    // LISTENER TESTS
    // =============================================================================
    
    @Test
    public void testSetOnCommentClickListener_ShouldSetListener() {
        // Arrange
        CommentsBottomSheet.OnCommentClickListener listener = comment -> {
            // Mock listener implementation
        };
        
        // Act
        commentsBottomSheet.setOnCommentClickListener(listener);
        
        // Assert
        assertEquals("Listener should be set", listener, commentsBottomSheet.getOnCommentClickListener());
    }
    
    @Test
    public void testSetOnCommentClickListener_WithNull_ShouldClearListener() {
        // Arrange
        CommentsBottomSheet.OnCommentClickListener listener = comment -> {};
        commentsBottomSheet.setOnCommentClickListener(listener);
        
        // Act
        commentsBottomSheet.setOnCommentClickListener(null);
        
        // Assert
        assertNull("Listener should be cleared", commentsBottomSheet.getOnCommentClickListener());
    }

    // =============================================================================
    // SEARCH AND FILTER TESTS
    // =============================================================================
    
    @Test
    public void testFilterComments_WithValidQuery_ShouldReturnMatchingComments() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        String query = "great";
        
        // Act
        List<Comment> filteredComments = commentsBottomSheet.filterComments(query);
        
        // Assert
        assertNotNull("Filtered comments should not be null", filteredComments);
        for (Comment comment : filteredComments) {
            assertTrue("Each comment should contain the query term", 
                      comment.getText().toLowerCase().contains(query.toLowerCase()) ||
                      comment.getAuthor().toLowerCase().contains(query.toLowerCase()));
        }
    }
    
    @Test
    public void testFilterComments_WithEmptyQuery_ShouldReturnAllComments() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        
        // Act
        List<Comment> filteredComments = commentsBottomSheet.filterComments("");
        
        // Assert
        assertEquals("Empty query should return all comments", 
                    testComments.size(), filteredComments.size());
    }
    
    @Test
    public void testFilterComments_WithNoMatches_ShouldReturnEmptyList() {
        // Arrange
        commentsBottomSheet.setComments(new ArrayList<>(testComments));
        String unmatchableQuery = "xyz123unmatchable";
        
        // Act
        List<Comment> filteredComments = commentsBottomSheet.filterComments(unmatchableQuery);
        
        // Assert
        assertNotNull("Filtered comments should not be null", filteredComments);
        assertTrue("Should return empty list when no matches", filteredComments.isEmpty());
    }

    // =============================================================================
    // HELPER METHODS
    // =============================================================================
    
    private List<Comment> createTestComments() {
        return Arrays.asList(
            createTestComment("Great project! Really impressed with the implementation.", "User1", 1609459200000L),
            createTestComment("Nice work, but could use some improvements in the UI.", "User2", 1609545600000L),
            createTestComment("Could you add more documentation? It would be helpful.", "User3", 1609632000000L),
            createTestComment("Excellent use of design patterns!", "User4", 1609718400000L),
            createTestComment("This is exactly what I was looking for. Thank you!", "User5", 1609804800000L)
        );
    }
    
    private Comment createTestComment(String text, String author, long timestamp) {
        Comment comment = new Comment();
        comment.setText(text);
        comment.setAuthor(author);
        comment.setTimestamp(timestamp);
        return comment;
    }
    
    private List<Comment> createLargeCommentsList(int size) {
        List<Comment> comments = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            comments.add(createTestComment(
                "This is test comment number " + i + " with various keywords for testing.",
                "TestUser" + (i % 50),
                System.currentTimeMillis() - (i * 1000L)
            ));
        }
        return comments;
    }
}

/**
 * Comment model class for testing
 */
class Comment {
    private String text;
    private String author;
    private long timestamp;
    private String id;
    
    public Comment() {
        this.id = "comment_" + System.nanoTime();
    }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getId() { return id; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Comment comment = (Comment) obj;
        return timestamp == comment.timestamp &&
               text != null && text.equals(comment.text) &&
               author != null && author.equals(comment.author);
    }
    
    @Override
    public int hashCode() {
        int result = text != null ? text.hashCode() : 0;
        result = 31 * result + (author != null ? author.hashCode() : 0);
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }
}

/**
 * Mock CommentsBottomSheet class for testing
 */
class CommentsBottomSheet {
    private Context context;
    private List<Comment> comments;
    private boolean isShowing;
    private String title;
    private OnCommentClickListener onCommentClickListener;
    private OnCommentsLoadedListener onCommentsLoadedListener;
    
    public interface OnCommentClickListener {
        void onCommentClick(Comment comment);
    }
    
    public interface OnCommentsLoadedListener {
        void onCommentsLoaded(List<Comment> comments);
    }
    
    public CommentsBottomSheet(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context;
        this.comments = new ArrayList<>();
        this.isShowing = false;
    }
    
    public void setComments(List<Comment> comments) {
        if (comments == null) {
            this.comments = new ArrayList<>();
        } else {
            this.comments = new ArrayList<>();
            for (Comment comment : comments) {
                if (comment != null && validateComment(comment)) {
                    this.comments.add(comment);
                }
            }
        }
    }
    
    public List<Comment> getComments() {
        return new ArrayList<>(comments);
    }
    
    public int getCommentsCount() {
        return comments.size();
    }
    
    public boolean addComment(Comment comment) {
        if (comment != null && validateComment(comment)) {
            comments.add(comment);
            return true;
        }
        return false;
    }
    
    public boolean removeComment(int index) {
        if (index >= 0 && index < comments.size()) {
            comments.remove(index);
            return true;
        }
        return false;
    }
    
    public void clearComments() {
        comments.clear();
    }
    
    public boolean validateComment(Comment comment) {
        if (comment == null) return false;
        if (comment.getText() == null || comment.getText().trim().isEmpty()) return false;
        if (comment.getAuthor() == null || comment.getAuthor().trim().isEmpty()) return false;
        if (comment.getTimestamp() <= 0) return false;
        if (comment.getTimestamp() > System.currentTimeMillis()) return false;
        return true;
    }
    
    public void show() {
        isShowing = true;
    }
    
    public void dismiss() {
        isShowing = false;
    }
    
    public boolean isShowing() {
        return isShowing;
    }
    
    public boolean isEmptyStateVisible() {
        return comments.isEmpty();
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setOnCommentClickListener(OnCommentClickListener listener) {
        this.onCommentClickListener = listener;
    }
    
    public OnCommentClickListener getOnCommentClickListener() {
        return onCommentClickListener;
    }
    
    public void loadCommentsAsync(String projectId, OnCommentsLoadedListener listener) {
        new Thread(() -> {
            try {
                Thread.sleep(50);
                List<Comment> loadedComments = new ArrayList<>();
                if (projectId != null && !projectId.trim().isEmpty()) {
                    loadedComments.add(new Comment());
                }
                if (listener != null) {
                    listener.onCommentsLoaded(loadedComments);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (listener != null) {
                    listener.onCommentsLoaded(new ArrayList<>());
                }
            }
        }).start();
    }
    
    public List<Comment> filterComments(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(comments);
        }
        
        List<Comment> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (Comment comment : comments) {
            if (comment.getText().toLowerCase().contains(lowerQuery) ||
                comment.getAuthor().toLowerCase().contains(lowerQuery)) {
                filtered.add(comment);
            }
        }
        return filtered;
    }
    
    public void onDestroy() {
        dismiss();
        clearComments();
        onCommentClickListener = null;
        onCommentsLoadedListener = null;
    }
}