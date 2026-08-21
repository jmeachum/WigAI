package io.github.fabb.wigai.bitwig;
import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Scene inventory reporting.
 *
 * <p>Mock harness lives in {@link BitwigApiFacadeTestSupport}.
 */
class BitwigApiFacadeSceneTest extends BitwigApiFacadeTestSupport {

    @Test
    void testGetAllScenesInfo() {
        // Arrange - setup scenes
        when(mockSceneBank.getSizeOfBank()).thenReturn(3);

        // Setup 3 different scenes
        Scene[] scenes = new Scene[3];
        for (int i = 0; i < 3; i++) {
            scenes[i] = mock(Scene.class);

            // Scene exists
            com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(mockExists.get()).thenReturn(i < 2); // Only first 2 scenes exist
            when(scenes[i].exists()).thenReturn(mockExists);

            if (i < 2) { // Only setup properties for existing scenes
                // Scene name
                com.bitwig.extension.controller.api.SettableStringValue mockName = mock(com.bitwig.extension.controller.api.SettableStringValue.class);
                when(mockName.get()).thenReturn("Scene " + (i + 1));
                when(scenes[i].name()).thenReturn(mockName);

                // Scene color - properly mock the Color object that get() returns
                com.bitwig.extension.controller.api.SettableColorValue mockColorValue = mock(com.bitwig.extension.controller.api.SettableColorValue.class);
                com.bitwig.extension.api.Color mockColor = mock(com.bitwig.extension.api.Color.class);
                when(mockColor.getRed()).thenReturn(0.5);
                when(mockColor.getGreen()).thenReturn(0.5);
                when(mockColor.getBlue()).thenReturn(0.5);
                when(mockColorValue.get()).thenReturn(mockColor);
                when(scenes[i].color()).thenReturn(mockColorValue);
            }

            when(mockSceneBank.getItemAt(i)).thenReturn(scenes[i]);
        }

        // Act
        java.util.List<java.util.Map<String, Object>> result = bitwigApiFacade.getAllScenesInfo();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size()); // Only 2 scenes exist

        // Verify Scene 1
        java.util.Map<String, Object> scene1 = result.get(0);
        assertEquals(0, scene1.get("index"));
        assertEquals("Scene 1", scene1.get("name"));
        assertEquals("rgb(127,127,127)", scene1.get("color")); // Mock returns 0.5 * 255 = 127 for each component

        // Verify Scene 2
        java.util.Map<String, Object> scene2 = result.get(1);
        assertEquals(1, scene2.get("index"));
        assertEquals("Scene 2", scene2.get("name"));
        assertEquals("rgb(127,127,127)", scene2.get("color"));

        // Verify logging
        verify(mockLogger).info("BitwigApiFacade: Getting all scenes info");
    }

    @Test
    void testGetAllScenesInfo_EmptyProject() {
        // Arrange - no scenes exist
        when(mockSceneBank.getSizeOfBank()).thenReturn(3);

        // Setup 3 non-existing scenes
        for (int i = 0; i < 3; i++) {
            Scene mockSceneEmpty = mock(Scene.class);
            com.bitwig.extension.controller.api.BooleanValue mockExists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
            when(mockExists.get()).thenReturn(false); // No scenes exist
            when(mockSceneEmpty.exists()).thenReturn(mockExists);
            when(mockSceneBank.getItemAt(i)).thenReturn(mockSceneEmpty);
        }

        // Act
        java.util.List<java.util.Map<String, Object>> result = bitwigApiFacade.getAllScenesInfo();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.size()); // Empty project

        // Verify logging
        verify(mockLogger).info("BitwigApiFacade: Getting all scenes info");
    }
}
