package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Scene;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.SettableColorValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import io.github.fabb.wigai.common.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SceneBankFacade.
 */
class SceneBankFacadeTest {

    @Mock
    private ControllerHost host;
    @Mock
    private Logger logger;
    @Mock
    private SceneBank sceneBank;

    private SceneBankFacade facade;
    private final int sceneCount = 3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(host.createSceneBank(sceneCount)).thenReturn(sceneBank);

        for (int i = 0; i < sceneCount; i++) {
            Scene scene = mock(Scene.class);
            when(sceneBank.getItemAt(i)).thenReturn(scene);

            BooleanValue exists = mock(BooleanValue.class);
            SettableStringValue name = mock(SettableStringValue.class);
            SettableColorValue color = mock(SettableColorValue.class);

            when(scene.exists()).thenReturn(exists);
            when(scene.name()).thenReturn(name);
            when(scene.color()).thenReturn(color);
        }

        facade = new SceneBankFacade(host, logger, sceneCount);
    }

    @Test
    void testConstructorMarksAllScenePropertiesInterested() {
        for (int i = 0; i < sceneCount; i++) {
            Scene scene = sceneBank.getItemAt(i);
            verify(scene.name()).markInterested();
            verify(scene.exists()).markInterested();
            verify(scene.color()).markInterested();
        }
    }

    @Test
    void testGetSceneCountReturnsConfiguredValue() {
        assertEquals(sceneCount, facade.getSceneCount());
    }

    @Test
    void testGetSceneNameReturnsNullForOutOfRangeIndex() {
        assertNull(facade.getSceneName(-1));
        assertNull(facade.getSceneName(sceneCount));
    }

    @Test
    void testGetSceneNameReturnsNameForExistingScene() {
        Scene scene = sceneBank.getItemAt(1);
        when(scene.exists().get()).thenReturn(true);
        when(scene.name().get()).thenReturn("Chorus");

        assertEquals("Chorus", facade.getSceneName(1));
    }

    @Test
    void testGetSceneNameReturnsNullWhenSceneDoesNotExist() {
        Scene scene = sceneBank.getItemAt(2);
        when(scene.exists().get()).thenReturn(false);

        assertNull(facade.getSceneName(2));
    }

    @Test
    void testFindSceneByNameReturnsFirstMatch() {
        when(sceneBank.getItemAt(0).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(0).name().get()).thenReturn("Intro");

        when(sceneBank.getItemAt(1).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(1).name().get()).thenReturn("Verse");

        when(sceneBank.getItemAt(2).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(2).name().get()).thenReturn("Verse");

        assertEquals(1, facade.findSceneByName("Verse"));
        assertEquals(-1, facade.findSceneByName("Bridge"));
    }

    @Test
    void testGetAllScenesInfoReturnsOnlyExistingScenesWithFormattedColors() {
        when(sceneBank.getItemAt(0).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(0).name().get()).thenReturn("Intro");
        Color introColor = mock(Color.class);
        when(introColor.getRed()).thenReturn(1.0);
        when(introColor.getGreen()).thenReturn(0.0);
        when(introColor.getBlue()).thenReturn(0.5);
        when(sceneBank.getItemAt(0).color().get()).thenReturn(introColor);

        when(sceneBank.getItemAt(1).exists().get()).thenReturn(false);

        when(sceneBank.getItemAt(2).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(2).name().get()).thenReturn("Outro");
        Color outroColor = mock(Color.class);
        when(outroColor.getRed()).thenReturn(0.25);
        when(outroColor.getGreen()).thenReturn(0.25);
        when(outroColor.getBlue()).thenReturn(0.25);
        when(sceneBank.getItemAt(2).color().get()).thenReturn(outroColor);

        List<Map<String, Object>> scenes = facade.getAllScenesInfo();

        assertEquals(2, scenes.size());
        assertEquals(0, scenes.get(0).get("index"));
        assertEquals("Intro", scenes.get(0).get("name"));
        assertEquals("rgb(255,0,127)", scenes.get(0).get("color"));

        assertEquals(2, scenes.get(1).get("index"));
        assertEquals("Outro", scenes.get(1).get("name"));
        assertEquals("rgb(63,63,63)", scenes.get(1).get("color"));
    }

    @Test
    void testGetAllScenesInfoUsesDefaultColorWhenColorReadFails() {
        when(sceneBank.getItemAt(0).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(0).name().get()).thenReturn("Fallback");
        Color color = mock(Color.class);
        when(color.getRed()).thenThrow(new RuntimeException("color unavailable"));
        when(sceneBank.getItemAt(0).color().get()).thenReturn(color);

        when(sceneBank.getItemAt(1).exists().get()).thenReturn(false);
        when(sceneBank.getItemAt(2).exists().get()).thenReturn(false);

        List<Map<String, Object>> scenes = facade.getAllScenesInfo();

        assertEquals(1, scenes.size());
        assertEquals("rgb(127,127,127)", scenes.get(0).get("color"));
        verify(logger).info(contains("Using default color values"));
    }

    @Test
    void testGetAllScenesInfoReturnsEmptyWhenSceneIterationFails() {
        when(sceneBank.getItemAt(0).exists().get()).thenReturn(true);
        when(sceneBank.getItemAt(0).name().get()).thenReturn("Intro");
        when(sceneBank.getItemAt(0).color().get()).thenThrow(new RuntimeException("bad color"));
        when(sceneBank.getItemAt(1)).thenThrow(new RuntimeException("scene fetch failed"));

        List<Map<String, Object>> scenes = facade.getAllScenesInfo();

        assertTrue(scenes.isEmpty());
        verify(logger).warn(contains("Error getting scenes info"));
    }
}
