package io.github.fabb.wigai.features;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.features.ClipSceneController.ClipLaunchResult;
import io.github.fabb.wigai.features.ClipSceneController.SceneLaunchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for ClipSceneController.
 */
class ClipSceneControllerTest {

    @Mock
    private BitwigApiFacade bitwigApiFacade;

    @Mock
    private Logger logger;

    private ClipSceneController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ClipSceneController(bitwigApiFacade, logger);
    }

    @Test
    void testLaunchClip_Success() {
        // Arrange
        String trackName = "Drums";
        int clipIndex = 0;

        when(bitwigApiFacade.findTrackIndexByName(trackName)).thenReturn(0);
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);
        doNothing().when(bitwigApiFacade).launchClipByTrackIndex(0, clipIndex);

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Clip at Drums[0] launched.", result.getMessage());
        assertNull(result.getErrorCode());

        verify(bitwigApiFacade).findTrackIndexByName(trackName);
        verify(bitwigApiFacade).getTrackClipCountByIndex(0);
        verify(bitwigApiFacade).launchClipByTrackIndex(0, clipIndex);
    }

    @Test
    void testLaunchClip_TrackNotFound() {
        // Arrange
        String trackName = "NonExistentTrack";
        int clipIndex = 0;

        when(bitwigApiFacade.findTrackIndexByName(trackName))
            .thenThrow(new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "findTrackIndexByName", "Track '" + trackName + "' not found"));

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("TRACK_NOT_FOUND", result.getErrorCode());
        assertEquals("Track 'NonExistentTrack' not found", result.getMessage());

        verify(bitwigApiFacade).findTrackIndexByName(trackName);
        verify(bitwigApiFacade, never()).getTrackClipCountByIndex(anyInt());
        verify(bitwigApiFacade, never()).launchClipByTrackIndex(anyInt(), anyInt());
    }

    @Test
    void testLaunchClip_ClipIndexOutOfBounds() {
        // Arrange
        String trackName = "Drums";
        int clipIndex = 10;

        when(bitwigApiFacade.findTrackIndexByName(trackName)).thenReturn(0);
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER_INDEX", result.getErrorCode());
        assertEquals("Clip index 10 is out of bounds for track 'Drums'", result.getMessage());

        verify(bitwigApiFacade).findTrackIndexByName(trackName);
        verify(bitwigApiFacade).getTrackClipCountByIndex(0);
        verify(bitwigApiFacade, never()).launchClipByTrackIndex(anyInt(), anyInt());
    }

    @Test
    void testLaunchClip_BitwigLaunchFailed() {
        // Arrange
        String trackName = "Drums";
        int clipIndex = 0;

        when(bitwigApiFacade.findTrackIndexByName(trackName)).thenReturn(0);
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);
        doThrow(new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "launchClipByTrackIndex", "Failed to launch clip"))
            .when(bitwigApiFacade).launchClipByTrackIndex(0, clipIndex);

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("BITWIG_API_ERROR", result.getErrorCode());
        assertTrue(result.getMessage().contains("Failed to launch clip"));

        verify(bitwigApiFacade).findTrackIndexByName(trackName);
        verify(bitwigApiFacade).getTrackClipCountByIndex(0);
        verify(bitwigApiFacade).launchClipByTrackIndex(0, clipIndex);
    }

    @Test
    void testLaunchClip_ExceptionHandling() {
        // Arrange
        String trackName = "Drums";
        int clipIndex = 0;
        RuntimeException exception = new RuntimeException("Bitwig API error");

        when(bitwigApiFacade.findTrackIndexByName(trackName)).thenThrow(exception);

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("BITWIG_API_ERROR", result.getErrorCode());
        assertTrue(result.getMessage().contains("Internal error occurred while launching clip"));

        verify(bitwigApiFacade).findTrackIndexByName(trackName);
    }

    @Test
    void testLaunchClip_EmptyTrackName() {
        // Arrange
        String trackName = "";
        int clipIndex = 0;

        when(bitwigApiFacade.findTrackIndexByName(trackName))
            .thenThrow(new BitwigApiException(ErrorCode.EMPTY_PARAMETER, "findTrackIndexByName", "trackName cannot be empty"));

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("EMPTY_PARAMETER", result.getErrorCode());
        assertTrue(result.getMessage().contains("trackName cannot be empty"));
    }

    @Test
    void testLaunchClip_NegativeClipIndex() {
        // This test verifies the controller can handle negative indices gracefully
        // The validation should be done at the tool level, but controller should handle it too
        String trackName = "Drums";
        int clipIndex = -1;

        when(bitwigApiFacade.findTrackIndexByName(trackName)).thenReturn(0);
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER_INDEX", result.getErrorCode());
        assertTrue(result.getMessage().contains("out of bounds"));
    }

    @Test
    void testLaunchClip_ValidBoundaryClipIndex() {
        // Test launching the last valid clip slot
        String trackName = "Drums";
        int clipIndex = 7; // Last slot in an 8-slot bank

        when(bitwigApiFacade.findTrackIndexByName(trackName)).thenReturn(0);
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);
        doNothing().when(bitwigApiFacade).launchClipByTrackIndex(0, clipIndex);

        // Act
        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals("Clip at Drums[7] launched.", result.getMessage());

        verify(bitwigApiFacade).launchClipByTrackIndex(0, clipIndex);
    }

    @Test
    void testLaunchClip_WithExplicitTrackIndex_Success() {
        String trackName = "  drums  ";
        int clipIndex = 2;
        int trackIndex = 3;

        when(bitwigApiFacade.getTrackNameByIndex(trackIndex)).thenReturn("Drums");
        when(bitwigApiFacade.getTrackClipCountByIndex(trackIndex)).thenReturn(8);
        doNothing().when(bitwigApiFacade).launchClipByTrackIndex(trackIndex, clipIndex);

        ClipLaunchResult result = controller.launchClip(trackName, clipIndex, trackIndex);

        assertTrue(result.isSuccess());
        assertEquals(trackIndex, result.getTrackIndex());
        assertEquals("Drums", result.getTrackName());
        verify(bitwigApiFacade, never()).findTrackIndexByName(anyString());
        verify(bitwigApiFacade).launchClipByTrackIndex(trackIndex, clipIndex);
    }

    @Test
    void testLaunchClip_WithTrackIndexOnly_Success() {
        int clipIndex = 1;
        int trackIndex = 4;

        when(bitwigApiFacade.getTrackNameByIndex(trackIndex)).thenReturn("Bass");
        when(bitwigApiFacade.getTrackClipCountByIndex(trackIndex)).thenReturn(8);
        doNothing().when(bitwigApiFacade).launchClipByTrackIndex(trackIndex, clipIndex);

        ClipLaunchResult result = controller.launchClipWithSelectors(trackIndex, null, clipIndex);

        assertTrue(result.isSuccess());
        assertEquals(trackIndex, result.getTrackIndex());
        assertEquals("Bass", result.getTrackName());
        verify(bitwigApiFacade, never()).findTrackIndexByName(anyString());
        verify(bitwigApiFacade).launchClipByTrackIndex(trackIndex, clipIndex);
    }

    @Test
    void testLaunchClip_WithTrackIndexOnly_ClipIndexOutOfBoundsReturnsInvalidParameterIndex() {
        int clipIndex = 12;
        int trackIndex = 4;

        when(bitwigApiFacade.getTrackNameByIndex(trackIndex)).thenReturn("Bass");
        when(bitwigApiFacade.getTrackClipCountByIndex(trackIndex)).thenReturn(8);

        ClipLaunchResult result = controller.launchClipWithSelectors(trackIndex, null, clipIndex);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER_INDEX", result.getErrorCode());
        assertTrue(result.getMessage().contains("out of bounds"));
        verify(bitwigApiFacade, never()).launchClipByTrackIndex(anyInt(), anyInt());
    }

    @Test
    void testLaunchClip_WithDualSelectorsExactMatch_Success() {
        String trackName = "Drums";
        int clipIndex = 0;
        int trackIndex = 3;

        when(bitwigApiFacade.getTrackNameByIndex(trackIndex)).thenReturn("Drums");
        when(bitwigApiFacade.getTrackClipCountByIndex(trackIndex)).thenReturn(8);
        doNothing().when(bitwigApiFacade).launchClipByTrackIndex(trackIndex, clipIndex);

        ClipLaunchResult result = controller.launchClip(trackName, clipIndex, trackIndex);

        assertTrue(result.isSuccess());
        assertEquals(trackIndex, result.getTrackIndex());
        assertEquals("Drums", result.getTrackName());
        verify(bitwigApiFacade, never()).findTrackIndexByName(anyString());
        verify(bitwigApiFacade).launchClipByTrackIndex(trackIndex, clipIndex);
    }

    @Test
    void testLaunchClip_WithExplicitTrackIndex_MismatchReturnsInvalidParameter() {
        String trackName = "Drums";
        int clipIndex = 0;
        int trackIndex = 1;

        when(bitwigApiFacade.getTrackNameByIndex(trackIndex)).thenReturn("Bass");

        ClipLaunchResult result = controller.launchClip(trackName, clipIndex, trackIndex);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER", result.getErrorCode());
        assertTrue(result.getMessage().contains("does not match track_name"));
        verify(bitwigApiFacade, never()).launchClipByTrackIndex(anyInt(), anyInt());
    }

    @Test
    void testLaunchClip_DuplicateTrackNameReturnsAmbiguityAndDoesNotLaunch() {
        String trackName = "Drums";
        int clipIndex = 0;

        List<Map<String, Object>> candidates = List.of(
            Map.of("track_index", 1, "track_name", "Drums"),
            Map.of("track_index", 3, "track_name", "Drums")
        );

        when(bitwigApiFacade.findTrackIndexByName(trackName))
            .thenThrow(new BitwigApiException(
                ErrorCode.INVALID_PARAMETER,
                "findTrackIndexByName",
                "Ambiguous track_name 'Drums'. Provide track_index to confirm target.",
                Map.of(
                    "reason", "ambiguous_track_name",
                    "track_name", trackName,
                    "confirmation_parameter", "track_index",
                    "candidates", candidates
                )
            ));

        ClipLaunchResult result = controller.launchClip(trackName, clipIndex);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER", result.getErrorCode());
        assertTrue(result.isAmbiguous());
        assertTrue(result.getMessage().contains("Provide track_index"));
        assertEquals("track_index", result.getConfirmationParameter());
        assertEquals(2, result.getCandidates().size());
        verify(bitwigApiFacade).findTrackIndexByName(trackName);
        verify(bitwigApiFacade, never()).launchClipByTrackIndex(anyInt(), anyInt());
    }

    // === Scene index out-of-bounds and overflow regression tests ===

    @Test
    void testLaunchSceneByIndex_NegativeIndex() {
        // Controller defense-in-depth: negative scene index returns INVALID_PARAMETER_INDEX
        SceneLaunchResult result = controller.launchSceneByIndex(-1);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER_INDEX", result.getErrorCode());
        assertTrue(result.getMessage().contains("non-negative"));
    }

    @Test
    void testLaunchSceneByIndex_OutOfBounds() {
        // Scene index exceeds all track clip counts → INVALID_PARAMETER_INDEX
        when(bitwigApiFacade.getTrackBankSize()).thenReturn(2);
        when(bitwigApiFacade.getTrackNameByIndex(0)).thenReturn("Track 1");
        when(bitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Track 2");
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(4);
        when(bitwigApiFacade.getTrackClipCountByIndex(1)).thenReturn(4);

        SceneLaunchResult result = controller.launchSceneByIndex(999);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_PARAMETER_INDEX", result.getErrorCode());
        assertTrue(result.getMessage().contains("out of bounds"));
    }

    @Test
    void testLaunchSceneByIndex_NoTracksReturnsSceneNotFound() {
        // No tracks in session → SCENE_NOT_FOUND
        when(bitwigApiFacade.getTrackBankSize()).thenReturn(2);
        when(bitwigApiFacade.getTrackNameByIndex(0)).thenThrow(
            new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "getTrackNameByIndex", "Track at index 0 does not exist"));
        when(bitwigApiFacade.getTrackNameByIndex(1)).thenThrow(
            new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "getTrackNameByIndex", "Track at index 1 does not exist"));

        SceneLaunchResult result = controller.launchSceneByIndex(0);

        assertFalse(result.isSuccess());
        assertEquals("SCENE_NOT_FOUND", result.getErrorCode());
        assertTrue(result.getMessage().contains("No tracks found"));
    }

    @Test
    void testLaunchSceneByIndex_LaunchFailPreservesNonIndexError() {
        // Clips exist at scene index but all launches fail with BITWIG_API_ERROR
        // Must NOT collapse into INVALID_PARAMETER_INDEX
        when(bitwigApiFacade.getTrackBankSize()).thenReturn(2);
        when(bitwigApiFacade.getTrackNameByIndex(0)).thenReturn("Track 1");
        when(bitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Track 2");
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);
        when(bitwigApiFacade.getTrackClipCountByIndex(1)).thenReturn(8);
        doThrow(new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "launchClipByTrackIndex", "API error"))
            .when(bitwigApiFacade).launchClipByTrackIndex(anyInt(), anyInt());

        SceneLaunchResult result = controller.launchSceneByIndex(0);

        assertFalse(result.isSuccess());
        assertEquals("BITWIG_API_ERROR", result.getErrorCode());
        assertTrue(result.getMessage().contains("Failed to launch scene"));
    }

    @Test
    void testLaunchSceneByIndex_UsesIndexBasedApis() {
        // Verify index-based APIs are called instead of name-based
        when(bitwigApiFacade.getTrackBankSize()).thenReturn(1);
        when(bitwigApiFacade.getTrackNameByIndex(0)).thenReturn("Drums");
        when(bitwigApiFacade.getTrackClipCountByIndex(0)).thenReturn(8);

        SceneLaunchResult result = controller.launchSceneByIndex(0);

        assertTrue(result.isSuccess());
        verify(bitwigApiFacade).getTrackClipCountByIndex(0);
        verify(bitwigApiFacade).launchClipByTrackIndex(0, 0);
        verify(bitwigApiFacade, never()).getTrackClipCount(anyString());
        verify(bitwigApiFacade, never()).launchClip(anyString(), anyInt());
    }

    @Test
    void testGetClipsInScene_OutOfBounds() {
        // Scene index exceeds scene count → INVALID_PARAMETER_INDEX from controller
        when(bitwigApiFacade.getSceneCount()).thenReturn(4);

        BitwigApiException exception = assertThrows(BitwigApiException.class, () ->
            controller.getClipsInScene(999, null)
        );

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("out of bounds"));
    }

    @Test
    void testGetClipsInScene_NegativeIndex() {
        // Negative scene index is invalid per isSceneIndexValid
        when(bitwigApiFacade.getSceneCount()).thenReturn(4);

        BitwigApiException exception = assertThrows(BitwigApiException.class, () ->
            controller.getClipsInScene(-1, null)
        );

        assertEquals(ErrorCode.INVALID_PARAMETER_INDEX, exception.getErrorCode());
    }
}
