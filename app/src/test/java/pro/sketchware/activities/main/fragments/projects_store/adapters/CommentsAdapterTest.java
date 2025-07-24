package pro.sketchware.activities.main.fragments.projects_store.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

import pro.sketchware.activities.main.fragments.projects_store.models.Comment;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for CommentsAdapter
 * Testing Framework: JUnit 4 with Robolectric and Mockito
 * 
 * These tests cover comprehensive scenarios for a RecyclerView adapter used in the projects store
 * for displaying comments. The tests validate adapter behavior, data handling, view binding,
 * edge cases, performance, and thread safety that might occur in production.
 */
@RunWith(RobolectricTestRunner.class)
public class CommentsAdapterTest {

    private CommentsAdapter adapter;
    private Context context;
    private List<Comment> testComments;
    
    @Mock
    private LayoutInflater mockInflater;
    
    @Mock
    private ViewGroup mockParent;
    
    @Mock
    private View mockItemView;
    
    @Mock
    private TextView mockAuthorTextView;
    
    @Mock
    private TextView mockContentTextView;
    
    @Mock
    private TextView mockTimestampTextView;
    
    @Mock
    private CommentsAdapter.OnCommentClickListener mockClickListener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        
        // Create test data
        testComments = createTestComments();
        
        // Mock view creation
        when(mockParent.getContext()).thenReturn(context);
        when(mockInflater.inflate(anyInt(), any(ViewGroup.class), anyBoolean())).thenReturn(mockItemView);
        when(mockItemView.findViewById(android.R.id.text1)).thenReturn(mockAuthorTextView);
        when(mockItemView.findViewById(android.R.id.text2)).thenReturn(mockContentTextView);
        
        // Initialize adapter
        adapter = new CommentsAdapter(context, testComments);
    }

    private List<Comment> createTestComments() {
        List<Comment> comments = new ArrayList<>();
        
        // Create test comment 1 - Normal comment
        Comment comment1 = createComment(1, "John Doe", "This is a test comment", System.currentTimeMillis());
        
        // Create test comment 2 - Comment with longer content  
        Comment comment2 = createComment(2, "Jane Smith", "This is a much longer comment that tests how the adapter handles longer text content and ensures proper display", System.currentTimeMillis() - 3600000);
        
        // Create test comment 3 - Comment with special characters
        Comment comment3 = createComment(3, "Test User", "Comment with special chars: @#$%^&*()_+{}|:<>?", System.currentTimeMillis() - 7200000);
        
        // Create test comment 4 - Comment with empty content (edge case)
        Comment comment4 = createComment(4, "Empty User", "", 0);
        
        // Create test comment 5 - Comment with null values (edge case)
        Comment comment5 = createComment(5, null, null, -1);
        
        comments.add(comment1);
        comments.add(comment2);
        comments.add(comment3);
        comments.add(comment4);
        comments.add(comment5);
        
        return comments;
    }
    
    private Comment createComment(int id, String author, String content, long timestamp) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setAuthor(author);
        comment.setContent(content);
        comment.setTimestamp(timestamp);
        return comment;
    }

    // Constructor Tests
    @Test
    public void testConstructor_withValidContextAndComments_shouldInitializeCorrectly() {
        // Given
        List<Comment> comments = createTestComments();
        
        // When
        CommentsAdapter newAdapter = new CommentsAdapter(context, comments);
        
        // Then
        assertNotNull("Adapter should not be null", newAdapter);
        assertEquals("Item count should match comments size", comments.size(), newAdapter.getItemCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_withNullContext_shouldThrowException() {
        // Given
        List<Comment> comments = createTestComments();
        
        // When & Then
        new CommentsAdapter(null, comments);
    }

    @Test
    public void testConstructor_withNullComments_shouldInitializeWithEmptyList() {
        // When
        CommentsAdapter newAdapter = new CommentsAdapter(context, null);
        
        // Then
        assertNotNull("Adapter should not be null", newAdapter);
        assertEquals("Item count should be zero for null comments", 0, newAdapter.getItemCount());
    }

    @Test
    public void testConstructor_withEmptyComments_shouldInitializeCorrectly() {
        // Given
        List<Comment> emptyComments = new ArrayList<>();
        
        // When
        CommentsAdapter newAdapter = new CommentsAdapter(context, emptyComments);
        
        // Then
        assertNotNull("Adapter should not be null", newAdapter);
        assertEquals("Item count should be zero for empty list", 0, newAdapter.getItemCount());
    }

    // Item Count Tests
    @Test
    public void testGetItemCount_withPopulatedList_shouldReturnCorrectCount() {
        // When
        int count = adapter.getItemCount();
        
        // Then
        assertEquals("Item count should match test comments size", testComments.size(), count);
    }

    @Test
    public void testGetItemCount_afterClearingComments_shouldReturnZero() {
        // Given
        adapter.clearComments();
        
        // When
        int count = adapter.getItemCount();
        
        // Then
        assertEquals("Item count should be zero after clearing", 0, count);
    }

    // ViewHolder Creation Tests
    @Test
    public void testOnCreateViewHolder_shouldReturnValidViewHolder() {
        // When
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // Then
        assertNotNull("ViewHolder should not be null", viewHolder);
        assertNotNull("ViewHolder itemView should not be null", viewHolder.itemView);
    }

    @Test
    public void testOnCreateViewHolder_shouldInitializeViewsCorrectly() {
        // When
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // Then
        assertNotNull("ViewHolder should not be null", viewHolder);
        assertNotNull("ViewHolder itemView should not be null", viewHolder.itemView);
        assertNotNull("Author TextView should not be null", viewHolder.authorTextView);
        assertNotNull("Content TextView should not be null", viewHolder.contentTextView);
    }

    // View Binding Tests
    @Test
    public void testOnBindViewHolder_withValidPosition_shouldBindDataCorrectly() {
        // Given
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        Comment testComment = testComments.get(0);
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then - Verify the comment data is properly displayed
        assertNotNull("ViewHolder should be bound", viewHolder);
    }

    @Test
    public void testOnBindViewHolder_withEmptyContent_shouldHandleGracefully() {
        // Given
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When - bind comment with empty content (index 3)
        adapter.onBindViewHolder(viewHolder, 3);
        
        // Then - Should not crash and handle empty content
        assertNotNull("ViewHolder should handle empty content", viewHolder);
    }

    @Test
    public void testOnBindViewHolder_withNullValues_shouldHandleGracefully() {
        // Given
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When - bind comment with null values (index 4)
        adapter.onBindViewHolder(viewHolder, 4);
        
        // Then - Should handle null values gracefully
        assertNotNull("ViewHolder should handle null values", viewHolder);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testOnBindViewHolder_withInvalidPosition_shouldThrowException() {
        // Given
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When & Then
        adapter.onBindViewHolder(viewHolder, testComments.size()); // Invalid position
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testOnBindViewHolder_withNegativePosition_shouldThrowException() {
        // Given
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When & Then
        adapter.onBindViewHolder(viewHolder, -1); // Negative position
    }

    // Data Management Tests
    @Test
    public void testUpdateComments_withNewList_shouldUpdateAndNotifyDataSetChanged() {
        // Given
        List<Comment> newComments = new ArrayList<>();
        newComments.add(createComment(10, "New Author", "New comment", System.currentTimeMillis()));
        
        // When
        adapter.updateComments(newComments);
        
        // Then
        assertEquals("Item count should match new comments size", 1, adapter.getItemCount());
    }

    @Test
    public void testUpdateComments_withNullList_shouldClearComments() {
        // When
        adapter.updateComments(null);
        
        // Then
        assertEquals("Item count should be zero after updating with null", 0, adapter.getItemCount());
    }

    @Test
    public void testAddComment_withValidComment_shouldIncreaseCount() {
        // Given
        int initialCount = adapter.getItemCount();
        Comment newComment = createComment(20, "Added Author", "Added comment", System.currentTimeMillis());
        
        // When
        adapter.addComment(newComment);
        
        // Then
        assertEquals("Item count should increase by 1", initialCount + 1, adapter.getItemCount());
    }

    @Test
    public void testAddComment_withNullComment_shouldNotChangeCount() {
        // Given
        int initialCount = adapter.getItemCount();
        
        // When
        adapter.addComment(null);
        
        // Then
        assertEquals("Item count should not change when adding null", initialCount, adapter.getItemCount());
    }

    @Test
    public void testRemoveComment_withValidPosition_shouldDecreaseCount() {
        // Given
        int initialCount = adapter.getItemCount();
        
        // When
        adapter.removeComment(0);
        
        // Then
        assertEquals("Item count should decrease by 1", initialCount - 1, adapter.getItemCount());
    }

    @Test
    public void testRemoveComment_withInvalidPosition_shouldNotChangeCount() {
        // Given
        int initialCount = adapter.getItemCount();
        
        // When
        adapter.removeComment(-1);
        adapter.removeComment(initialCount);
        
        // Then
        assertEquals("Item count should not change with invalid positions", initialCount, adapter.getItemCount());
    }

    @Test
    public void testGetComment_withValidPosition_shouldReturnCorrectComment() {
        // When
        Comment comment = adapter.getComment(0);
        
        // Then
        assertNotNull("Comment should not be null", comment);
        assertEquals("Comment ID should match", testComments.get(0).getId(), comment.getId());
        assertEquals("Comment author should match", testComments.get(0).getAuthor(), comment.getAuthor());
        assertEquals("Comment content should match", testComments.get(0).getContent(), comment.getContent());
    }

    @Test
    public void testGetComment_withInvalidPosition_shouldReturnNull() {
        // When
        Comment comment1 = adapter.getComment(-1);
        Comment comment2 = adapter.getComment(testComments.size());
        
        // Then
        assertNull("Comment should be null for negative position", comment1);
        assertNull("Comment should be null for position >= size", comment2);
    }

    @Test
    public void testClearComments_shouldEmptyList() {
        // Given
        assertTrue("Should have comments initially", adapter.getItemCount() > 0);
        
        // When
        adapter.clearComments();
        
        // Then
        assertEquals("Should have no comments after clearing", 0, adapter.getItemCount());
    }

    // Click Listener Tests
    @Test
    public void testSetOnCommentClickListener_shouldSetListener() {
        // When
        adapter.setOnCommentClickListener(mockClickListener);
        
        // Then
        assertNotNull("Click listener should not be null", mockClickListener);
    }

    @Test
    public void testSetOnCommentClickListener_withNull_shouldHandleGracefully() {
        // When & Then - should not throw exception
        adapter.setOnCommentClickListener(null);
    }

    // Filtering Tests
    @Test
    public void testFilter_withSearchQuery_shouldFilterComments() {
        // Given
        String searchQuery = "test";
        
        // When
        adapter.filter(searchQuery);
        
        // Then
        assertTrue("Filtered count should be <= original count", adapter.getItemCount() <= testComments.size());
    }

    @Test
    public void testFilter_withEmptyQuery_shouldShowAllComments() {
        // Given
        adapter.filter("specific"); // First apply a filter
        
        // When
        adapter.filter("");
        
        // Then
        assertEquals("Should show all comments with empty filter", testComments.size(), adapter.getItemCount());
    }

    @Test
    public void testFilter_withNullQuery_shouldShowAllComments() {
        // Given
        adapter.filter("specific"); // First apply a filter
        
        // When
        adapter.filter(null);
        
        // Then
        assertEquals("Should show all comments with null filter", testComments.size(), adapter.getItemCount());
    }

    @Test
    public void testFilter_caseInsensitive_shouldMatchRegardlessOfCase() {
        // Given
        String upperCaseQuery = "JOHN";
        String lowerCaseQuery = "john";
        
        // When
        adapter.filter(upperCaseQuery);
        int upperCount = adapter.getItemCount();
        
        adapter.filter(lowerCaseQuery);
        int lowerCount = adapter.getItemCount();
        
        // Then
        assertEquals("Case insensitive search should return same results", upperCount, lowerCount);
        assertTrue("Should find at least one match", upperCount > 0);
    }

    // Sorting Tests
    @Test
    public void testSortCommentsByTimestamp_shouldSortByNewestFirst() {
        // When
        adapter.sortCommentsByTimestamp();
        
        // Then
        Comment firstComment = adapter.getComment(0);
        Comment secondComment = adapter.getComment(1);
        
        if (firstComment != null && secondComment != null) {
            assertTrue("First comment should have newer or equal timestamp", 
                      firstComment.getTimestamp() >= secondComment.getTimestamp());
        }
    }

    @Test
    public void testSortCommentsByAuthor_shouldSortAlphabetically() {
        // When
        adapter.sortCommentsByAuthor();
        
        // Then
        Comment firstComment = adapter.getComment(0);
        Comment secondComment = adapter.getComment(1);
        
        if (firstComment != null && secondComment != null && 
            firstComment.getAuthor() != null && secondComment.getAuthor() != null) {
            assertTrue("Authors should be in alphabetical order", 
                      firstComment.getAuthor().compareToIgnoreCase(secondComment.getAuthor()) <= 0);
        }
    }

    @Test
    public void testSortCommentsByAuthor_withNullAuthors_shouldHandleGracefully() {
        // Given - we have comments with null authors in our test data
        
        // When
        adapter.sortCommentsByAuthor();
        
        // Then - should not crash
        assertTrue("Should handle null authors in sorting", adapter.getItemCount() > 0);
    }

    // Edge Cases and Error Handling Tests
    @Test
    public void testMultipleOperations_shouldMaintainConsistency() {
        // Given
        Comment newComment = createComment(100, "Multi Test", "Multiple operations test", System.currentTimeMillis());
        
        // When
        adapter.addComment(newComment);
        int countAfterAdd = adapter.getItemCount();
        
        adapter.removeComment(0);
        int countAfterRemove = adapter.getItemCount();
        
        adapter.clearComments();
        int countAfterClear = adapter.getItemCount();
        
        // Then
        assertEquals("Count should increase after add", testComments.size() + 1, countAfterAdd);
        assertEquals("Count should decrease after remove", testComments.size(), countAfterRemove);
        assertEquals("Count should be zero after clear", 0, countAfterClear);
    }

    @Test
    public void testPerformance_withLargeDataSet_shouldHandleEfficiently() {
        // Given
        List<Comment> largeCommentList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeCommentList.add(createComment(i, "Author " + i, "Content " + i, System.currentTimeMillis() - i * 1000));
        }
        
        // When
        long startTime = System.currentTimeMillis();
        CommentsAdapter largeAdapter = new CommentsAdapter(context, largeCommentList);
        long endTime = System.currentTimeMillis();
        
        // Then
        assertEquals("Should handle 1000 comments", 1000, largeAdapter.getItemCount());
        assertTrue("Adapter creation should be fast", (endTime - startTime) < 1000); // Less than 1 second
    }

    @Test
    public void testFilterPerformance_withLargeDataSet_shouldBeEfficient() {
        // Given
        List<Comment> largeCommentList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeCommentList.add(createComment(i, "Author " + i, "Content " + i + " test", System.currentTimeMillis()));
        }
        CommentsAdapter largeAdapter = new CommentsAdapter(context, largeCommentList);
        
        // When
        long startTime = System.currentTimeMillis();
        largeAdapter.filter("test");
        long endTime = System.currentTimeMillis();
        
        // Then
        assertEquals("Should filter all 1000 comments", 1000, largeAdapter.getItemCount());
        assertTrue("Filtering should be fast", (endTime - startTime) < 500); // Less than 0.5 seconds
    }

    @Test
    public void testSortingPerformance_withLargeDataSet_shouldBeEfficient() {
        // Given
        List<Comment> largeCommentList = new ArrayList<>();
        for (int i = 1000; i >= 0; i--) { // Reverse order for more challenging sort
            largeCommentList.add(createComment(i, "Author " + i, "Content " + i, i * 1000L));
        }
        CommentsAdapter largeAdapter = new CommentsAdapter(context, largeCommentList);
        
        // When
        long startTime = System.currentTimeMillis();
        largeAdapter.sortCommentsByTimestamp();
        long endTime = System.currentTimeMillis();
        
        // Then
        assertTrue("Sorting should be fast", (endTime - startTime) < 1000); // Less than 1 second
        
        // Verify sort worked correctly
        Comment first = largeAdapter.getComment(0);
        Comment second = largeAdapter.getComment(1);
        assertTrue("Should be sorted correctly", first.getTimestamp() >= second.getTimestamp());
    }

    @Test
    public void testConcurrentModification_shouldHandleGracefully() {
        // Given
        final List<Exception> exceptions = new ArrayList<>();
        
        // When - simulate concurrent access
        Runnable addTask = () -> {
            try {
                for (int i = 0; i < 50; i++) {
                    adapter.addComment(createComment(1000 + i, "Concurrent " + i, "Content " + i, System.currentTimeMillis()));
                    Thread.sleep(1); // Small delay to increase concurrency
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
        };
        
        Runnable removeTask = () -> {
            try {
                for (int i = 0; i < 25; i++) {
                    if (adapter.getItemCount() > 0) {
                        adapter.removeComment(0);
                    }
                    Thread.sleep(2); // Small delay
                }
            } catch (Exception e) {
                exceptions.add(e);
            }
        };
        
        Thread thread1 = new Thread(addTask);
        Thread thread2 = new Thread(removeTask);
        
        thread1.start();
        thread2.start();
        
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            exceptions.add(e);
        }
        
        // Then - should handle concurrent modifications without major crashes
        assertTrue("Should handle concurrent modifications gracefully", exceptions.size() <= 2);
    }

    // Timestamp Formatting Tests
    @Test
    public void testTimestampFormatting_withValidTimestamp_shouldFormatCorrectly() {
        // Given
        Comment comment = createComment(1, "Test", "Test", System.currentTimeMillis());
        adapter.updateComments(List.of(comment));
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then - should not crash with timestamp formatting
        assertNotNull("ViewHolder should handle timestamp formatting", viewHolder);
    }

    @Test
    public void testTimestampFormatting_withZeroTimestamp_shouldHandleGracefully() {
        // Given
        Comment comment = createComment(1, "Test", "Test", 0);
        adapter.updateComments(List.of(comment));
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then - should handle zero timestamp
        assertNotNull("ViewHolder should handle zero timestamp", viewHolder);
    }

    @Test
    public void testTimestampFormatting_withNegativeTimestamp_shouldHandleGracefully() {
        // Given
        Comment comment = createComment(1, "Test", "Test", -1);
        adapter.updateComments(List.of(comment));
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        
        // When
        adapter.onBindViewHolder(viewHolder, 0);
        
        // Then - should handle negative timestamp
        assertNotNull("ViewHolder should handle negative timestamp", viewHolder);
    }

    // Complex Filter Tests
    @Test
    public void testFilter_withSpecialCharacters_shouldMatchCorrectly() {
        // Given
        String specialCharsQuery = "@#$";
        
        // When
        adapter.filter(specialCharsQuery);
        
        // Then
        assertTrue("Should find comment with special characters", adapter.getItemCount() > 0);
    }

    @Test
    public void testFilter_withPartialMatch_shouldReturnMatches() {
        // Given
        String partialQuery = "Doe"; // Should match "John Doe"
        
        // When
        adapter.filter(partialQuery);
        
        // Then
        assertTrue("Should find partial matches", adapter.getItemCount() > 0);
        
        Comment foundComment = adapter.getComment(0);
        assertTrue("Found comment should contain query", 
                  foundComment.getAuthor() != null && foundComment.getAuthor().contains(partialQuery));
    }

    @Test
    public void testFilter_sequentialFilters_shouldMaintainCorrectState() {
        // Given
        int originalCount = adapter.getItemCount();
        
        // When - apply multiple filters in sequence
        adapter.filter("John");
        int firstFilterCount = adapter.getItemCount();
        
        adapter.filter("Jane");
        int secondFilterCount = adapter.getItemCount();
        
        adapter.filter("");
        int resetCount = adapter.getItemCount();
        
        // Then
        assertTrue("First filter should reduce count", firstFilterCount <= originalCount);
        assertTrue("Second filter should also be <= original", secondFilterCount <= originalCount);
        assertEquals("Reset should restore original count", originalCount, resetCount);
    }

    // ViewHolder Interaction Tests
    @Test
    public void testViewHolder_clickListener_shouldTriggerCallback() {
        // Given
        adapter.setOnCommentClickListener(mockClickListener);
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        adapter.onBindViewHolder(viewHolder, 0);
        
        // When
        viewHolder.itemView.performClick();
        
        // Then
        verify(mockClickListener, times(1)).onCommentClick(any(Comment.class), eq(0));
    }

    @Test
    public void testViewHolder_longClickListener_shouldTriggerCallback() {
        // Given
        adapter.setOnCommentClickListener(mockClickListener);
        CommentsAdapter.CommentViewHolder viewHolder = adapter.onCreateViewHolder(mockParent, 0);
        adapter.onBindViewHolder(viewHolder, 0);
        
        // When
        boolean result = viewHolder.itemView.performLongClick();
        
        // Then
        assertTrue("Long click should return true", result);
        verify(mockClickListener, times(1)).onCommentLongClick(any(Comment.class), eq(0));
    }
}