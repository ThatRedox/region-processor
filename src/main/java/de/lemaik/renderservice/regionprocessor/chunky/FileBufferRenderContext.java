package de.lemaik.renderservice.regionprocessor.chunky;


import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.RenderContext;

/**
 * A mocked {@link RenderContext} for Chunky that saves scene files into buffers. Only supports
 * saving scenes.
 */
public class FileBufferRenderContext extends RenderContext {

  private ByteArrayOutputStream octree;
  private ByteArrayOutputStream emittergrid;

  public FileBufferRenderContext() {
    super(new Chunky(ChunkyOptions.getDefaults()));
  }

  @Override
  public OutputStream getSceneFileOutputStream(String fileName) throws FileNotFoundException {
    if (fileName.endsWith(".octree") || fileName.endsWith(".octree2")) {
      return octree = new ByteArrayOutputStream();
    } else if (fileName.endsWith(".emittergrid")) {
      return emittergrid = new ByteArrayOutputStream();
    }

    return new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        // no-op
      }
    };
  }

  public byte[] getOctree() {
    return octree.toByteArray();
  }

  public byte[] getEmittergrid() {
    return emittergrid != null ? emittergrid.toByteArray() : null;
  }

  public void setRenderThreadCount(int threads) {
    config.renderThreads = threads;
  }
}
