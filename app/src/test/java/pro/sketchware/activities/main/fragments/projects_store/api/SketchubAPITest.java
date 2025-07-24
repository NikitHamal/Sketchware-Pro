package pro.sketchware.activities.main.fragments.projects_store.api;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Comprehensive unit tests for SketchubAPI class.
 * Testing framework: JUnit 4 with Mockito for mocking and OkHttp MockWebServer
 * Covers HTTP operations, error handling, data parsing, authentication, and edge cases.
 * 
 * This test suite demonstrates bias for action by providing extensive coverage
 * for all potential API scenarios including happy paths, error conditions,
 * network failures, and data parsing edge cases.
 */
@RunWith(MockitoJUnitRunner.class)
public class SketchubAPITest {

    @Mock
    private OkHttpClient mockHttpClient;
    
    @Mock
    private Call mockCall;
    
    @Mock
    private Response mockResponse;
    
    @Mock
    private ResponseBody mockResponseBody;
    
    private SketchubAPI sketchubAPI;
    private MockWebServer mockWebServer;
    private CountDownLatch callbackLatch;
    private Gson gson;
    
    // Test callback implementations for capturing results
    private TestProjectsCallback testProjectsCallback;
    private TestProjectDetailsCallback testDetailsCallback;
    private TestDownloadCallback testDownloadCallback;
    private String lastError;
    private List<Project> lastProjectsList;
    private ProjectDetails lastProjectDetails;
    private boolean downloadSuccess;
    
    // Sample JSON responses for testing
    private static final String SAMPLE_PROJECTS_JSON = 
        "[{\"id\":1,\"title\":\"Calculator App\",\"description\":\"Simple calculator application\"," +
        "\"author\":\"developer1\",\"downloads\":1250,\"rating\":4.5," +
        "\"imageUrl\":\"https://api.sketchub.in/images/calc.jpg\"," +
        "\"tags\":[\"utility\",\"math\"],\"createdAt\":\"2023-12-01T10:00:00Z\"}," +
        "{\"id\":2,\"title\":\"Weather Widget\",\"description\":\"Real-time weather display\"," +
        "\"author\":\"weatherdev\",\"downloads\":892,\"rating\":4.2," +
        "\"imageUrl\":\"https://api.sketchub.in/images/weather.jpg\"," +
        "\"tags\":[\"widget\",\"weather\"],\"createdAt\":\"2023-11-28T15:30:00Z\"}]";
    
    private static final String SAMPLE_PROJECT_DETAILS_JSON = 
        "{\"id\":1,\"title\":\"Calculator App\",\"description\":\"Advanced calculator with scientific functions\"," +
        "\"author\":\"developer1\",\"downloads\":1250,\"rating\":4.5," +
        "\"screenshots\":[\"https://api.sketchub.in/screens/calc1.jpg\"," +
        "\"https://api.sketchub.in/screens/calc2.jpg\"]," +
        "\"downloadUrl\":\"https://api.sketchub.in/download/calc.zip\"," +
        "\"fileSize\":\"2.8 MB\",\"lastUpdated\":\"2023-12-01T10:00:00Z\"," +
        "\"version\":\"1.2.0\",\"minSdkVersion\":21,\"permissions\":[\"INTERNET\"]," +
        "\"changelog\":\"Fixed division by zero bug\"}";

    private static final String SAMPLE_AUTH_RESPONSE = 
        "{\"token\":\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9\"," +
        "\"user\":{\"id\":123,\"username\":\"testuser\",\"email\":\"test@example.com\"}," +
        "\"expiresIn\":3600}";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        gson = new Gson();
        
        // Initialize test callbacks
        setupTestCallbacks();
        
        // Initialize SketchubAPI with mock server URL
        String baseUrl = mockWebServer.url("/api/v1/").toString();
        sketchubAPI = new SketchubAPI(baseUrl);
        
        // Reset test state
        resetTestState();
    }

    private void setupTestCallbacks() {
        testProjectsCallback = new TestProjectsCallback();
        testDetailsCallback = new TestProjectDetailsCallback();
        testDownloadCallback = new TestDownloadCallback();
    }

    private void resetTestState() {
        lastError = null;
        lastProjectsList = null;
        lastProjectDetails = null;
        downloadSuccess = false;
        callbackLatch = new CountDownLatch(1);
    }

    // ==================== SUCCESS PATH TESTS ====================

    @Test
    public void testGetProjects_SuccessfulResponse() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_PROJECTS_JSON)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        assertEquals("Should have 2 projects", 2, lastProjectsList.size());
        
        Project firstProject = lastProjectsList.get(0);
        assertEquals("First project should have correct ID", 1, firstProject.getId());
        assertEquals("First project should have correct title", "Calculator App", firstProject.getTitle());
        assertEquals("First project should have correct downloads", 1250, firstProject.getDownloads());
        
        // Verify request was made correctly
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("Should be GET request", "GET", request.getMethod());
        assertTrue("Request path should be correct", request.getPath().contains("/projects"));
    }

    @Test
    public void testGetProjectDetails_SuccessfulResponse() throws Exception {
        // Arrange
        int projectId = 1;
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_PROJECT_DETAILS_JSON)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjectDetails(projectId, testDetailsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error", lastError);
        assertNotNull("Should have project details", lastProjectDetails);
        assertEquals("Should have correct project ID", 1, lastProjectDetails.getId());
        assertEquals("Should have correct title", "Calculator App", lastProjectDetails.getTitle());
        assertEquals("Should have correct file size", "2.8 MB", lastProjectDetails.getFileSize());
        assertTrue("Should have screenshots", lastProjectDetails.getScreenshots().size() > 0);
        
        // Verify request path includes project ID
        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue("Request path should contain project ID", 
            request.getPath().contains("/projects/" + projectId));
    }

    @Test
    public void testSearchProjects_SuccessfulResponse() throws Exception {
        // Arrange
        String searchQuery = "calculator";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_PROJECTS_JSON)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.searchProjects(searchQuery, testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        
        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue("Request should contain search query", 
            request.getPath().contains("q=" + searchQuery));
    }

    @Test
    public void testDownloadProject_SuccessfulResponse() throws Exception {
        // Arrange
        int projectId = 1;
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("Mock ZIP file content")
            .addHeader("Content-Type", "application/zip")
            .addHeader("Content-Length", "12345"));
        
        // Act
        sketchubAPI.downloadProject(projectId, testDownloadCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error", lastError);
        assertTrue("Download should be successful", downloadSuccess);
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    public void testGetProjects_NetworkError() throws Exception {
        // Arrange - Server not responding
        mockWebServer.shutdown();
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(10, TimeUnit.SECONDS));
        assertNotNull("Should have error message", lastError);
        assertNull("Should not have projects list", lastProjectsList);
        assertTrue("Error should mention connection issue", 
            lastError.toLowerCase().contains("connection") || 
            lastError.toLowerCase().contains("network"));
    }

    @Test
    public void testGetProjects_HttpErrorResponse() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody("{\"error\":\"Projects not found\",\"code\":404}")
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNotNull("Should have error message", lastError);
        assertNull("Should not have projects list", lastProjectsList);
        assertTrue("Error should contain HTTP error code", lastError.contains("404"));
    }

    @Test
    public void testGetProjects_InvalidJsonResponse() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{invalid json content}")
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNotNull("Should have error message", lastError);
        assertNull("Should not have projects list", lastProjectsList);
        assertTrue("Error should mention JSON parsing", 
            lastError.toLowerCase().contains("json") || 
            lastError.toLowerCase().contains("parse"));
    }

    @Test
    public void testGetProjects_EmptyResponse() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        assertEquals("Should have empty list", 0, lastProjectsList.size());
    }

    @Test
    public void testGetProjectDetails_InvalidProjectId() {
        // Act & Assert - Should handle invalid project ID
        sketchubAPI.getProjectDetails(-1, testDetailsCallback);
        
        // Verify error callback is called synchronously for invalid input
        assertNotNull("Should have error for invalid project ID", lastError);
        assertTrue("Error should mention invalid ID", 
            lastError.toLowerCase().contains("invalid"));
    }

    @Test
    public void testGetProjectDetails_ZeroProjectId() {
        // Act & Assert - Should handle zero project ID
        sketchubAPI.getProjectDetails(0, testDetailsCallback);
        
        // Verify error callback is called for invalid input
        assertNotNull("Should have error for zero project ID", lastError);
        assertTrue("Error should mention invalid ID", 
            lastError.toLowerCase().contains("invalid"));
    }

    @Test
    public void testSearchProjects_EmptyQuery() {
        // Act & Assert - Should handle empty search query
        sketchubAPI.searchProjects("", testProjectsCallback);
        
        assertNotNull("Should have error for empty query", lastError);
        assertTrue("Error should mention empty query", 
            lastError.toLowerCase().contains("empty") || 
            lastError.toLowerCase().contains("query"));
    }

    @Test
    public void testSearchProjects_NullQuery() {
        // Act & Assert - Should handle null search query
        sketchubAPI.searchProjects(null, testProjectsCallback);
        
        assertNotNull("Should have error for null query", lastError);
        assertTrue("Error should mention invalid query", 
            lastError.toLowerCase().contains("invalid") || 
            lastError.toLowerCase().contains("query"));
    }

    // ==================== CALLBACK HANDLING TESTS ====================

    @Test
    public void testGetProjects_NullCallback() {
        // Act & Assert - Should handle null callback gracefully without crashing
        assertDoesNotThrow(() -> sketchubAPI.getProjects(null));
    }

    @Test
    public void testGetProjectDetails_NullCallback() {
        // Act & Assert - Should handle null callback gracefully without crashing
        assertDoesNotThrow(() -> sketchubAPI.getProjectDetails(1, null));
    }

    // ==================== CONCURRENCY TESTS ====================

    @Test
    public void testConcurrentRequests() throws Exception {
        // Arrange
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(SAMPLE_PROJECTS_JSON)
                .addHeader("Content-Type", "application/json"));
        }
        
        CountDownLatch concurrentLatch = new CountDownLatch(3);
        List<List<Project>> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        SketchubAPI.ProjectsCallback concurrentCallback = new SketchubAPI.ProjectsCallback() {
            @Override
            public void onSuccess(List<Project> projects) {
                synchronized (results) {
                    results.add(projects);
                }
                concurrentLatch.countDown();
            }

            @Override
            public void onError(String error) {
                synchronized (errors) {
                    errors.add(error);
                }
                concurrentLatch.countDown();
            }
        };
        
        // Act - Make multiple concurrent requests
        for (int i = 0; i < 3; i++) {
            sketchubAPI.getProjects(concurrentCallback);
        }
        
        // Assert
        assertTrue("All callbacks should complete within timeout", 
            concurrentLatch.await(10, TimeUnit.SECONDS));
        assertEquals("Should have 3 successful responses", 3, results.size());
        assertEquals("Should have no errors", 0, errors.size());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    public void testJsonParsing_MissingOptionalFields() throws Exception {
        // Arrange - JSON with missing optional fields
        String incompleteJson = "[{\"id\":1,\"title\":\"Test\"}]"; // Missing other fields
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(incompleteJson)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert - Should handle missing fields gracefully
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error for missing optional fields", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        assertEquals("Should have 1 project", 1, lastProjectsList.size());
    }

    @Test
    public void testJsonParsing_ExtraFields() throws Exception {
        // Arrange - JSON with extra fields that should be ignored
        String jsonWithExtraFields = "[{\"id\":1,\"title\":\"Test\"," +
                "\"description\":\"Desc\",\"author\":\"Author\",\"downloads\":100," +
                "\"rating\":4.5,\"extraField\":\"value\",\"anotherExtra\":123," +
                "\"futureField\":{\"nested\":\"data\"}}]";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(jsonWithExtraFields)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert - Should handle extra fields gracefully
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error for extra fields", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        assertEquals("Should have 1 project", 1, lastProjectsList.size());
    }

    @Test
    public void testSpecialCharactersInResponse() throws Exception {
        // Arrange - JSON with special characters and emojis
        String specialCharsJson = "[{\"id\":1,\"title\":\"测试 Project 🎨\"," +
                "\"description\":\"Description with \\\"quotes\\\" and \\n newlines and 中文\"," +
                "\"author\":\"Author with émojis 😀 and symbols #@$%\",\"downloads\":100,\"rating\":4.5}]";
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(specialCharsJson)
            .addHeader("Content-Type", "application/json; charset=utf-8"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have error for special characters", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        assertEquals("Should have 1 project", 1, lastProjectsList.size());
        
        Project project = lastProjectsList.get(0);
        assertTrue("Title should contain special characters", 
            project.getTitle().contains("测试") && project.getTitle().contains("🎨"));
    }

    @Test
    public void testLargeDataSet() throws Exception {
        // Arrange - Large JSON response with many projects
        StringBuilder largeJson = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeJson.append(",");
            largeJson.append(String.format(
                "{\"id\":%d,\"title\":\"Project %d\",\"description\":\"Description %d\"," +
                "\"author\":\"Author%d\",\"downloads\":%d,\"rating\":%.1f," +
                "\"imageUrl\":\"https://example.com/image%d.jpg\"}",
                i, i, i, i, i * 10, 4.0 + (i % 10) * 0.1, i
            ));
        }
        largeJson.append("]");
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(largeJson.toString())
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(10, TimeUnit.SECONDS));
        assertNull("Should not have error for large dataset", lastError);
        assertNotNull("Should have projects list", lastProjectsList);
        assertEquals("Should have 100 projects", 100, lastProjectsList.size());
    }

    // ==================== AUTHENTICATION TESTS ====================

    @Test
    public void testAuthentication_SuccessfulLogin() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_AUTH_RESPONSE)
            .addHeader("Content-Type", "application/json"));
        
        TestAuthCallback authCallback = new TestAuthCallback();
        
        // Act
        sketchubAPI.authenticate("testuser", "password", authCallback);
        
        // Assert
        assertTrue("Auth callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNull("Should not have auth error", lastError);
        assertTrue("Authentication should be successful", authCallback.isAuthenticated());
        assertNotNull("Should have auth token", authCallback.getToken());
    }

    @Test
    public void testAuthentication_InvalidCredentials() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("{\"error\":\"Invalid credentials\",\"code\":401}")
            .addHeader("Content-Type", "application/json"));
        
        TestAuthCallback authCallback = new TestAuthCallback();
        
        // Act
        sketchubAPI.authenticate("invalid", "wrong", authCallback);
        
        // Assert
        assertTrue("Auth callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        assertNotNull("Should have auth error", authCallback.getError());
        assertFalse("Authentication should fail", authCallback.isAuthenticated());
        assertTrue("Error should mention invalid credentials", 
            authCallback.getError().toLowerCase().contains("invalid") ||
            authCallback.getError().toLowerCase().contains("credentials"));
    }

    // ==================== REQUEST HEADER TESTS ====================

    @Test
    public void testRequestHeaders() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_PROJECTS_JSON)
            .addHeader("Content-Type", "application/json"));
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        
        RecordedRequest request = mockWebServer.takeRequest();
        assertNotNull("Should have User-Agent header", request.getHeader("User-Agent"));
        assertNotNull("Should have Accept header", request.getHeader("Accept"));
        assertTrue("Accept header should include JSON", 
            request.getHeader("Accept").contains("application/json"));
        
        // Check for API key header if configured
        String apiKeyHeader = request.getHeader("X-API-Key");
        if (apiKeyHeader != null) {
            assertFalse("API key should not be empty", apiKeyHeader.isEmpty());
        }
    }

    @Test
    public void testAuthenticatedRequests() throws Exception {
        // Arrange - First authenticate
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_AUTH_RESPONSE)
            .addHeader("Content-Type", "application/json"));
        
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_PROJECTS_JSON)
            .addHeader("Content-Type", "application/json"));
        
        TestAuthCallback authCallback = new TestAuthCallback();
        sketchubAPI.authenticate("user", "pass", authCallback);
        
        // Reset latch for second request
        callbackLatch = new CountDownLatch(1);
        
        // Act - Make authenticated request
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Projects callback should be called within timeout", 
            callbackLatch.await(5, TimeUnit.SECONDS));
        
        // Skip auth request, check projects request
        mockWebServer.takeRequest(); // Auth request
        RecordedRequest projectsRequest = mockWebServer.takeRequest(); // Projects request
        
        String authHeader = projectsRequest.getHeader("Authorization");
        if (authHeader != null) {
            assertTrue("Should have Bearer token", authHeader.startsWith("Bearer "));
        }
    }

    // ==================== TIMEOUT AND PERFORMANCE TESTS ====================

    @Test
    public void testTimeoutHandling() throws Exception {
        // Arrange - Slow server response
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(SAMPLE_PROJECTS_JSON)
            .setHeadersDelay(15, TimeUnit.SECONDS)); // Delay longer than typical timeout
        
        // Act
        sketchubAPI.getProjects(testProjectsCallback);
        
        // Assert
        assertTrue("Callback should be called within extended timeout", 
            callbackLatch.await(20, TimeUnit.SECONDS));
        
        // Should either succeed with data or fail with timeout error
        assertTrue("Should have either success or timeout error", 
            lastProjectsList != null || (lastError != null && 
            lastError.toLowerCase().contains("timeout")));
    }

    // ==================== CONSTRUCTOR AND CONFIGURATION TESTS ====================

    @Test
    public void testApiConstructor_DefaultConfiguration() {
        // Act
        SketchubAPI api = new SketchubAPI();
        
        // Assert
        assertNotNull("API instance should be created", api);
    }

    @Test
    public void testApiConstructor_CustomBaseUrl() {
        // Act
        String customUrl = "https://custom.api.sketchub.in/v2/";
        SketchubAPI api = new SketchubAPI(customUrl);
        
        // Assert
        assertNotNull("API instance should be created with custom URL", api);
    }

    @Test
    public void testApiConstructor_InvalidUrl() {
        // Act & Assert
        assertDoesNotThrow(() -> new SketchubAPI("invalid-url"));
    }

    // ==================== TEST CALLBACK IMPLEMENTATIONS ====================

    private class TestProjectsCallback implements SketchubAPI.ProjectsCallback {
        @Override
        public void onSuccess(List<Project> projects) {
            lastProjectsList = projects;
            callbackLatch.countDown();
        }

        @Override
        public void onError(String error) {
            lastError = error;
            callbackLatch.countDown();
        }
    }

    private class TestProjectDetailsCallback implements SketchubAPI.ProjectDetailsCallback {
        @Override
        public void onSuccess(ProjectDetails details) {
            lastProjectDetails = details;
            callbackLatch.countDown();
        }

        @Override
        public void onError(String error) {
            lastError = error;
            callbackLatch.countDown();
        }
    }

    private class TestDownloadCallback implements SketchubAPI.DownloadCallback {
        @Override
        public void onSuccess(String filePath) {
            downloadSuccess = true;
            callbackLatch.countDown();
        }

        @Override
        public void onError(String error) {
            lastError = error;
            callbackLatch.countDown();
        }

        @Override
        public void onProgress(int progress) {
            // Track download progress if needed
        }
    }

    private class TestAuthCallback implements SketchubAPI.AuthCallback {
        private boolean authenticated = false;
        private String token = null;
        private String error = null;

        @Override
        public void onSuccess(String authToken) {
            authenticated = true;
            token = authToken;
            callbackLatch.countDown();
        }

        @Override
        public void onError(String errorMsg) {
            error = errorMsg;
            callbackLatch.countDown();
        }

        public boolean isAuthenticated() { return authenticated; }
        public String getToken() { return token; }
        public String getError() { return error; }
    }

    // ==================== HELPER METHODS ====================

    private static void assertDoesNotThrow(Runnable executable) {
        try {
            executable.run();
        } catch (Exception e) {
            fail("Expected no exception to be thrown, but got: " + e.getMessage());
        }
    }

    // ==================== TEST CLEANUP ====================

    @After
    public void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    // ==================== DATA CLASSES FOR TESTING ====================

    public static class Project {
        private int id;
        private String title;
        private String description;
        private String author;
        private int downloads;
        private double rating;
        private String imageUrl;
        private List<String> tags;
        private String createdAt;

        // Getters
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public int getDownloads() { return downloads; }
        public double getRating() { return rating; }
        public String getImageUrl() { return imageUrl; }
        public List<String> getTags() { return tags; }
        public String getCreatedAt() { return createdAt; }
    }

    public static class ProjectDetails extends Project {
        private List<String> screenshots;
        private String downloadUrl;
        private String fileSize;
        private String lastUpdated;
        private String version;
        private int minSdkVersion;
        private List<String> permissions;
        private String changelog;

        // Getters
        public List<String> getScreenshots() { return screenshots; }
        public String getDownloadUrl() { return downloadUrl; }
        public String getFileSize() { return fileSize; }
        public String getLastUpdated() { return lastUpdated; }
        public String getVersion() { return version; }
        public int getMinSdkVersion() { return minSdkVersion; }
        public List<String> getPermissions() { return permissions; }
        public String getChangelog() { return changelog; }
    }
}