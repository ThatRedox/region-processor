package de.lemaik.renderservice.regionprocessor.chunky;

import java.lang.reflect.Field;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.world.World;

public class SceneUtils {

  public static int getDimension(Scene scene) {
    try {
      Field field = Scene.class.getDeclaredField("worldDimension");
      field.setAccessible(true);
      return field.getInt(scene);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Could not get dimension", e);
    }
  }
}
