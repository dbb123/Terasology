/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.world.lighting;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.terasology.TerasologyTestingEnvironment;
import org.terasology.math.Diamond3iIterator;
import org.terasology.math.Region3i;
import org.terasology.math.Side;
import org.terasology.math.Vector3i;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockUri;
import org.terasology.world.block.family.DefaultBlockFamilyFactoryRegistry;
import org.terasology.world.block.family.SymmetricFamily;
import org.terasology.world.block.internal.BlockManagerImpl;
import org.terasology.world.block.loader.WorldAtlas;
import org.terasology.world.chunks.Chunk;

import static org.junit.Assert.assertEquals;

/**
 * @author Immortius
 */
public class BulkLightPropagationTest extends TerasologyTestingEnvironment {

    private BlockManagerImpl blockManager;
    private Block air;
    private Block fullLight;
    private Block weakLight;
    private Block mediumLight;
    private Block solid;
    private Block solidMediumLight;

    @Before
    public void setup() throws Exception {
        super.setup();
        blockManager = new BlockManagerImpl(new WorldAtlas(4096),
                Lists.<String>newArrayList(), Maps.<String, Short>newHashMap(), true, new DefaultBlockFamilyFactoryRegistry());
        fullLight = new Block();
        fullLight.setDisplayName("Torch");
        fullLight.setUri(new BlockUri("engine:torch"));
        fullLight.setId((byte) 2);
        fullLight.setLuminance(Chunk.MAX_LIGHT);
        blockManager.addBlockFamily(new SymmetricFamily(fullLight.getURI(), fullLight), true);

        weakLight = new Block();
        weakLight.setDisplayName("PartLight");
        weakLight.setUri(new BlockUri("engine:weakLight"));
        weakLight.setId((byte) 3);
        weakLight.setLuminance((byte) 2);
        blockManager.addBlockFamily(new SymmetricFamily(weakLight.getURI(), weakLight), true);

        mediumLight = new Block();
        mediumLight.setDisplayName("MediumLight");
        mediumLight.setUri(new BlockUri("engine:mediumLight"));
        mediumLight.setId((byte) 4);
        mediumLight.setLuminance((byte) 5);
        blockManager.addBlockFamily(new SymmetricFamily(mediumLight.getURI(), mediumLight), true);

        solid = new Block();
        solid.setDisplayName("Solid");
        solid.setUri(new BlockUri("engine:solid"));
        solid.setId((byte) 5);
        for (Side side : Side.values()) {
            solid.setFullSide(side, true);
        }
        blockManager.addBlockFamily(new SymmetricFamily(solid.getURI(), solid), true);

        solidMediumLight = new Block();
        solidMediumLight.setDisplayName("SolidMediumLight");
        solidMediumLight.setUri(new BlockUri("engine:solidMediumLight"));
        solidMediumLight.setId((byte) 6);
        solidMediumLight.setLuminance((byte) 5);
        for (Side side : Side.values()) {
            solidMediumLight.setFullSide(side, true);
        }
        blockManager.addBlockFamily(new SymmetricFamily(solidMediumLight.getURI(), solidMediumLight), true);

        air = BlockManager.getAir();
    }

    @Test
    public void addLightInVacuum() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(Vector3i.zero(), fullLight);

        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(Vector3i.zero(), air, fullLight));

        assertEquals(fullLight.getLuminance(), worldView.getLuminanceAt(Vector3i.zero()));
        assertEquals(fullLight.getLuminance() - 1, worldView.getLuminanceAt(new Vector3i(0, 1, 0)));
        assertEquals(fullLight.getLuminance() - 14, worldView.getLuminanceAt(new Vector3i(0, 14, 0)));
        for (int i = 1; i < fullLight.getLuminance(); ++i) {
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(fullLight.getLuminance() - i, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void removeLightInVacuum() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(Vector3i.zero(), air, fullLight));

        worldView.setBlockAt(Vector3i.zero(), air);
        propagator.process(new BlockChange(Vector3i.zero(), fullLight, air));

        assertEquals(0, worldView.getLuminanceAt(Vector3i.zero()));
        for (int i = 1; i < fullLight.getLuminance(); ++i) {
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(0, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void reduceLight() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(Vector3i.zero(), air, fullLight));

        worldView.setBlockAt(Vector3i.zero(), weakLight);
        propagator.process(new BlockChange(Vector3i.zero(), fullLight, weakLight));

        assertEquals(weakLight.getLuminance(), worldView.getLuminanceAt(Vector3i.zero()));
        for (int i = 1; i < 15; ++i) {
            byte expectedLuminance = (byte) Math.max(0, weakLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void addOverlappingLights() {
        Vector3i lightPos = new Vector3i(5, 0, 0);

        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        worldView.setBlockAt(lightPos, fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(Vector3i.zero(), air, fullLight), new BlockChange(lightPos, air, fullLight));

        assertEquals(fullLight.getLuminance(), worldView.getLuminanceAt(Vector3i.zero()));
        assertEquals(fullLight.getLuminance() - 1, worldView.getLuminanceAt(new Vector3i(1, 0, 0)));
        assertEquals(fullLight.getLuminance() - 2, worldView.getLuminanceAt(new Vector3i(2, 0, 0)));
        assertEquals(fullLight.getLuminance() - 2, worldView.getLuminanceAt(new Vector3i(3, 0, 0)));
        assertEquals(fullLight.getLuminance() - 1, worldView.getLuminanceAt(new Vector3i(4, 0, 0)));
        assertEquals(fullLight.getLuminance(), worldView.getLuminanceAt(new Vector3i(5, 0, 0)));
    }

    @Test
    public void removeOverlappingLight() {
        Vector3i lightPos = new Vector3i(5, 0, 0);

        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(Vector3i.zero(), fullLight);
        worldView.setBlockAt(lightPos, fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(Vector3i.zero(), air, fullLight), new BlockChange(lightPos, air, fullLight));

        worldView.setBlockAt(lightPos, air);
        propagator.process(new BlockChange(lightPos, fullLight, air));

        for (int i = 0; i < 16; ++i) {
            byte expectedLuminance = (byte) Math.max(0, fullLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void removeLightOverlappingAtEdge() {
        Vector3i lightPos = new Vector3i(2, 0, 0);

        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(Vector3i.zero(), weakLight);
        worldView.setBlockAt(lightPos, weakLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(Vector3i.zero(), air, weakLight), new BlockChange(lightPos, air, weakLight));

        worldView.setBlockAt(lightPos, air);
        propagator.process(new BlockChange(lightPos, weakLight, air));

        for (int i = 0; i < weakLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) Math.max(0, weakLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void addLightInLight() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(new Vector3i(2, 0, 0), mediumLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(2, 0, 0), air, mediumLight));

        worldView.setBlockAt(Vector3i.zero(), fullLight);
        propagator.process(new BlockChange(Vector3i.zero(), air, fullLight));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) Math.max(0, fullLight.getLuminance() - i);
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void addAdjacentLights() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(new Vector3i(1, 0, 0), mediumLight);
        worldView.setBlockAt(new Vector3i(0, 0, 0), mediumLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(1, 0, 0), air, mediumLight), new BlockChange(new Vector3i(0, 0, 0), air, mediumLight));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                int dist = Math.min(Vector3i.zero().gridDistance(pos), new Vector3i(1, 0, 0).gridDistance(pos));
                byte expectedLuminance = (byte) Math.max(mediumLight.getLuminance() - dist, 0);
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void addWeakLightNextToStrongLight() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(new Vector3i(0, 0, 0), fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(0, 0, 0), air, fullLight));

        worldView.setBlockAt(new Vector3i(1,0,0), weakLight);
        propagator.process(new BlockChange(new Vector3i(1,0,0), air, weakLight));
        assertEquals(14, worldView.getLuminanceAt(new Vector3i(1,0,0)));
    }

    @Test
    public void removeAdjacentLights() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(new Vector3i(1, 0, 0), mediumLight);
        worldView.setBlockAt(new Vector3i(0, 0, 0), mediumLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(1, 0, 0), air, mediumLight), new BlockChange(new Vector3i(0, 0, 0), air, mediumLight));

        worldView.setBlockAt(new Vector3i(1, 0, 0), air);
        worldView.setBlockAt(new Vector3i(0, 0, 0), air);
        propagator.process(new BlockChange(new Vector3i(1, 0, 0), mediumLight, air), new BlockChange(new Vector3i(0, 0, 0), mediumLight, air));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) 0;
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }

    @Test
    public void addSolidBlocksLight() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        worldView.setBlockAt(new Vector3i(0, 0, 0), mediumLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(0, 0, 0), air, mediumLight));

        worldView.setBlockAt(new Vector3i(1, 0, 0), solid);
        propagator.process(new BlockChange(new Vector3i(1, 0, 0), air, solid));

        assertEquals(0, worldView.getLuminanceAt(new Vector3i(1,0,0)));
        assertEquals(1, worldView.getLuminanceAt(new Vector3i(2,0,0)));
    }

    @Test
    public void removeSolidAllowsLight() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        for (Vector3i pos : Region3i.createFromCenterExtents(new Vector3i(1,0,0), new Vector3i(0,30,30))) {
            worldView.setBlockAt(pos, solid);
        }
        worldView.setBlockAt(new Vector3i(0, 0, 0), fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(0, 0, 0), air, fullLight));

        assertEquals(0, worldView.getLuminanceAt(new Vector3i(1,0,0)));

        worldView.setBlockAt(new Vector3i(1, 0, 0), air);
        propagator.process(new BlockChange(new Vector3i(1, 0, 0), solid, air));

        assertEquals(14, worldView.getLuminanceAt(new Vector3i(1,0,0)));
        assertEquals(13, worldView.getLuminanceAt(new Vector3i(2,0,0)));
    }

    @Test
    public void removeSolidAndLight() {
        StubLightingWorldView worldView = new StubLightingWorldView();
        for (Vector3i pos : Region3i.createFromCenterExtents(new Vector3i(1,0,0), new Vector3i(0,30,30))) {
            worldView.setBlockAt(pos, solid);
        }
        worldView.setBlockAt(new Vector3i(0, 0, 0), fullLight);
        BulkLightPropagator propagator = new BulkLightPropagator(worldView);
        propagator.process(new BlockChange(new Vector3i(0, 0, 0), air, fullLight));

        assertEquals(0, worldView.getLuminanceAt(new Vector3i(1,0,0)));

        worldView.setBlockAt(new Vector3i(1, 0, 0), air);
        worldView.setBlockAt(new Vector3i(0, 0, 0), air);
        propagator.process(new BlockChange(new Vector3i(1, 0, 0), solid, air), new BlockChange(new Vector3i(0,0,0), fullLight, air));

        for (int i = 0; i < fullLight.getLuminance() + 1; ++i) {
            byte expectedLuminance = (byte) 0;
            for (Vector3i pos : Diamond3iIterator.iterateAtDistance(Vector3i.zero(), i)) {
                assertEquals(expectedLuminance, worldView.getLuminanceAt(pos));
            }
        }
    }
}
