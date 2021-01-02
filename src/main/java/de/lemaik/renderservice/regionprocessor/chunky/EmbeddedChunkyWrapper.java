package de.lemaik.renderservice.regionprocessor.chunky;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.scene.SynchronousSceneManager;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

public class EmbeddedChunkyWrapper implements ChunkyWrapper {

  private final FileBufferRenderContext context = new FileBufferRenderContext();
  private File defaultTexturepack;
  private File previousTexturepack;

  @Override
  public BinarySceneData generateOctree(File scene, File worldDirectory, int dimension,
      File texturepack)
      throws IOException {
    if (texturepack == null) {
      texturepack = defaultTexturepack;
    }

    // all chunky instances share their texturepacks statically
    if (!texturepack.equals(previousTexturepack)) {
      if (texturepack.equals(defaultTexturepack)) {
        TexturePackLoader
            .loadTexturePacks(new String[]{defaultTexturepack.getAbsolutePath()}, false);
      } else {
        // load the selected texturepack and the default texturepack as fallback
        TexturePackLoader.loadTexturePacks(
            new String[]{texturepack.getAbsolutePath(), defaultTexturepack.getAbsolutePath()},
            false);
      }
      previousTexturepack = texturepack;
    }

    context.setRenderThreadCount(1);
    RenderManager renderer = new RenderManager(context, true);
    renderer.setCPULoad(100);

    SynchronousSceneManager sceneManager = new SynchronousSceneManager(context, renderer);
    context.setSceneDirectory(scene.getParentFile());
    sceneManager.getScene().loadDescription(new FileInputStream(scene));

    sceneManager.getScene().loadChunks(new TaskTracker(ProgressListener.NONE),
        new UnlockedWorld(worldDirectory, dimension),
        new HashSet<>(sceneManager.getScene().getChunks()));

    sceneManager.getScene().saveScene(context, new TaskTracker(ProgressListener.NONE));
    return new BinarySceneData(context.getOctree(), context.getEmittergrid());
  }

  @Override
  public void stop() {

  }

  @Override
  public void addListener(RenderListener listener) {

  }

  @Override
  public void removeListener(RenderListener listener) {

  }

  @Override
  public void setTargetSpp(int targetSpp) {

  }

  @Override
  public void setThreadCount(int threadCount) {

  }

  @Override
  public void setDefaultTexturepack(File texturepackPath) {
    this.defaultTexturepack = texturepackPath;
  }
}
