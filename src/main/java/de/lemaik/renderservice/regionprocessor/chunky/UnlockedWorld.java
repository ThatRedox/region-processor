package de.lemaik.renderservice.regionprocessor.chunky;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import se.llbit.chunky.world.PlayerEntityData;
import se.llbit.chunky.world.World;

public class UnlockedWorld extends World {

  public UnlockedWorld(String levelName, File worldDirectory, int dimension,
      Set<PlayerEntityData> playerEntities, boolean haveSpawnPos,
      long seed, long timestamp) {
    super(levelName, worldDirectory, dimension, playerEntities, haveSpawnPos, seed, timestamp);
  }

  public UnlockedWorld(File worldDirectory, int dimension) {
    super("", worldDirectory, dimension, Collections.emptySet(), false, 0, 0);
  }
}
